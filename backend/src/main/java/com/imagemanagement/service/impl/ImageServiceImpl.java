package com.imagemanagement.service.impl;

import com.imagemanagement.dto.request.ImageSearchRequest;
import com.imagemanagement.dto.response.ImageSummaryResponse;
import com.imagemanagement.dto.response.ImageUploadResponse;
import com.imagemanagement.dto.response.PageResponse;
import com.imagemanagement.entity.Image;
import com.imagemanagement.entity.ImageTag;
import com.imagemanagement.entity.Thumbnail;
import com.imagemanagement.entity.User;
import com.imagemanagement.entity.enums.ImagePrivacyLevel;
import com.imagemanagement.exception.BadRequestException;
import com.imagemanagement.repository.ImageRepository;
import com.imagemanagement.repository.UserRepository;
import com.imagemanagement.repository.specification.ImageSpecifications;
import com.imagemanagement.service.ExifExtractionService;
import com.imagemanagement.service.FileStorageService;
import com.imagemanagement.service.ImageService;
import com.imagemanagement.service.TagService;
import com.imagemanagement.service.ThumbnailService;
import jakarta.transaction.Transactional;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.imageio.ImageIO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class ImageServiceImpl implements ImageService {

    private final ImageRepository imageRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final ExifExtractionService exifExtractionService;
    private final ThumbnailService thumbnailService;
    private final TagService tagService;

    public ImageServiceImpl(ImageRepository imageRepository,
            UserRepository userRepository,
            FileStorageService fileStorageService,
            ExifExtractionService exifExtractionService,
            ThumbnailService thumbnailService,
            TagService tagService) {
        this.imageRepository = imageRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.exifExtractionService = exifExtractionService;
        this.thumbnailService = thumbnailService;
        this.tagService = tagService;
    }

    @Override
    public List<ImageUploadResponse> uploadImages(Long userId, List<MultipartFile> files, ImagePrivacyLevel privacyLevel, String description) {
        if (userId == null) {
            throw new BadRequestException("User id is required");
        }

        if (CollectionUtils.isEmpty(files)) {
            throw new BadRequestException("No files were provided");
        }

        User user = userRepository.findById(Objects.requireNonNull(userId))
                .orElseThrow(() -> new BadRequestException("User not found"));

        List<Image> imagesToSave = new ArrayList<>();

        for (MultipartFile file : files) {
            FileStorageService.StoredFileInfo storedFile = fileStorageService.storeFile(file, userId);
            Image image = buildImageEntity(user, storedFile, privacyLevel, description);
            imagesToSave.add(image);
        }

        List<Image> savedImages = imageRepository.saveAll(imagesToSave);
        savedImages.forEach(tagService::applyAutomaticTags);

        return savedImages.stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    public PageResponse<ImageSummaryResponse> searchImages(Long userId, ImageSearchRequest request) {
        ImageSearchRequest criteria = request != null ? request : new ImageSearchRequest();
        validateRange(criteria.getMinWidth(), criteria.getMaxWidth(), "width");
        validateRange(criteria.getMinHeight(), criteria.getMaxHeight(), "height");

        Sort sort = Objects.requireNonNull(buildSort(criteria));
        Pageable pageable = PageRequest.of(criteria.getPage(), criteria.getSize(), sort);
        Specification<Image> specification = Objects.requireNonNull(ImageSpecifications.build(criteria, userId));
        Page<Image> images = imageRepository.findAll(specification, pageable);
        return PageResponse.from(images.map(this::toSummaryResponse));
    }

    private Image buildImageEntity(User user, FileStorageService.StoredFileInfo storedFile,
                                   ImagePrivacyLevel privacyLevel, String description) {
        Image image = new Image();
        image.setUser(user);
        image.setOriginalFilename(storedFile.originalFilename());
        image.setStoredFilename(storedFile.storedFilename());
        image.setFilePath(storedFile.absolutePath());
        image.setFileSize(storedFile.size());
        image.setMimeType(storedFile.contentType());
        image.setDescription(description);
        image.setPrivacyLevel(privacyLevel != null ? privacyLevel : ImagePrivacyLevel.PUBLIC);

        Path imagePath = Path.of(storedFile.absolutePath());
        setImageDimensions(image, imagePath);
        exifExtractionService.extract(imagePath, image).ifPresent(image::setExifData);
        thumbnailService.generateThumbnails(image);
        return image;
    }

    private void setImageDimensions(Image image, Path absolutePath) {
        try (var inputStream = Files.newInputStream(absolutePath)) {
            BufferedImage bufferedImage = ImageIO.read(inputStream);
            if (bufferedImage != null) {
                image.setWidth(bufferedImage.getWidth());
                image.setHeight(bufferedImage.getHeight());
            }
        } catch (IOException ignored) {
            image.setWidth(null);
            image.setHeight(null);
        }
    }

    private ImageUploadResponse toResponse(Image image) {
        return new ImageUploadResponse(
                image.getId(),
                image.getOriginalFilename(),
                image.getStoredFilename(),
                image.getFilePath(),
                image.getFileSize(),
                image.getMimeType(),
                image.getWidth(),
                image.getHeight(),
                image.getUploadTime()
        );
    }

    private Sort buildSort(ImageSearchRequest request) {
        String sortBy = request.getSortBy();
        Sort.Direction direction = request.getSortDirection();
        if (direction == null) {
            direction = Sort.Direction.DESC;
        }
        String property = switch (sortBy != null ? sortBy : "") {
            case "originalFilename" -> "originalFilename";
            case "fileSize" -> "fileSize";
            case "width" -> "width";
            case "height" -> "height";
            default -> "uploadTime";
        };
        return Sort.by(direction, property);
    }

    private void validateRange(Integer min, Integer max, String fieldName) {
        if (min != null && max != null && min > max) {
            throw new BadRequestException("Invalid " + fieldName + " range: min must be <= max");
        }
    }

    private ImageSummaryResponse toSummaryResponse(Image image) {
        ImageSummaryResponse response = new ImageSummaryResponse();
        response.setId(image.getId());
        response.setOriginalFilename(image.getOriginalFilename());
        response.setStoredFilename(image.getStoredFilename());
        response.setFilePath(image.getFilePath());
        response.setFileSize(image.getFileSize());
        response.setMimeType(image.getMimeType());
        response.setWidth(image.getWidth());
        response.setHeight(image.getHeight());
        response.setDescription(image.getDescription());
        response.setPrivacyLevel(image.getPrivacyLevel());
        response.setUploadTime(image.getUploadTime());

        if (image.getExifData() != null) {
            response.setCameraMake(image.getExifData().getCameraMake());
            response.setCameraModel(image.getExifData().getCameraModel());
            response.setTakenTime(image.getExifData().getTakenTime());
        }

        response.setTags(extractTagNames(image.getImageTags()));
        response.setThumbnails(extractThumbnailSummaries(image.getThumbnails()));
        return response;
    }

    private List<String> extractTagNames(Set<ImageTag> imageTags) {
        if (CollectionUtils.isEmpty(imageTags)) {
            return List.of();
        }
        return imageTags.stream()
                .map(ImageTag::getTag)
                .filter(Objects::nonNull)
                .map(tag -> tag.getTagName() != null ? tag.getTagName() : "")
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    private List<ImageSummaryResponse.ThumbnailSummary> extractThumbnailSummaries(Set<Thumbnail> thumbnails) {
        if (CollectionUtils.isEmpty(thumbnails)) {
            return List.of();
        }

        return thumbnails.stream()
                .sorted(Comparator.comparing(thumbnail -> thumbnail.getSizeType().name()))
                .map(thumbnail -> {
                    ImageSummaryResponse.ThumbnailSummary summary = new ImageSummaryResponse.ThumbnailSummary();
                    summary.setId(thumbnail.getId());
                    summary.setSizeType(thumbnail.getSizeType());
                    summary.setWidth(thumbnail.getWidth());
                    summary.setHeight(thumbnail.getHeight());
                    summary.setFilePath(thumbnail.getFilePath());
                    summary.setFileSize(thumbnail.getFileSize());
                    return summary;
                })
                .toList();
    }
}