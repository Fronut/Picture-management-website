package com.imagemanagement.service.impl;

import com.imagemanagement.cache.CacheNames;
import com.imagemanagement.dto.request.ImageEditRequest;
import com.imagemanagement.dto.request.ImageSearchRequest;
import com.imagemanagement.dto.response.ImageDeleteResponse;
import com.imagemanagement.dto.response.ImageSummaryResponse;
import com.imagemanagement.dto.response.ImageUploadResponse;
import com.imagemanagement.dto.response.PageResponse;
import com.imagemanagement.entity.Image;
import com.imagemanagement.entity.ImageTag;
import com.imagemanagement.entity.Thumbnail;
import com.imagemanagement.entity.User;
import com.imagemanagement.entity.enums.ImagePrivacyLevel;
import com.imagemanagement.exception.BadRequestException;
import com.imagemanagement.exception.ForbiddenException;
import com.imagemanagement.exception.ResourceNotFoundException;
import com.imagemanagement.repository.ImageRepository;
import com.imagemanagement.repository.UserRepository;
import com.imagemanagement.repository.specification.ImageSpecifications;
import com.imagemanagement.service.ExifExtractionService;
import com.imagemanagement.service.FileStorageService;
import com.imagemanagement.service.ImageService;
import com.imagemanagement.service.TagService;
import com.imagemanagement.service.ThumbnailService;
import jakarta.transaction.Transactional;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Locale;
import java.util.Set;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.apache.commons.io.FilenameUtils;
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
    private static final int MAX_HIGHLIGHT_SIZE = 12;

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
    @CacheEvict(value = CacheNames.IMAGE_SEARCH, allEntries = true)
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
        List<PendingUpload> pendingUploads = new ArrayList<>();
        Set<String> hashesInRequest = new HashSet<>();
        LinkedHashSet<String> duplicatedFilenames = new LinkedHashSet<>();

        for (MultipartFile file : files) {
            String contentHash = calculateContentHash(file);
            String filename = StringUtils.hasText(file.getOriginalFilename())
                    ? Objects.requireNonNull(file.getOriginalFilename())
                    : "当前文件";

            if (!hashesInRequest.add(contentHash)) {
                duplicatedFilenames.add(filename);
                continue;
            }

            if (imageRepository.existsByUser_IdAndContentHash(userId, contentHash)) {
                duplicatedFilenames.add(filename);
                continue;
            }

            pendingUploads.add(new PendingUpload(file, contentHash));
        }

        if (!duplicatedFilenames.isEmpty()) {
            String message = "以下文件已上传过，无法重复上传：" + String.join(", ", duplicatedFilenames);
            throw new com.imagemanagement.exception.DuplicateFileException(new java.util.ArrayList<>(duplicatedFilenames), message);
        }

        for (PendingUpload pendingUpload : pendingUploads) {
            FileStorageService.StoredFileInfo storedFile = fileStorageService.storeFile(pendingUpload.file(), userId);
            Image image = buildImageEntity(user, storedFile, privacyLevel, description, pendingUpload.contentHash());
            imagesToSave.add(image);
        }

        List<Image> savedImages = imageRepository.saveAll(imagesToSave);
        savedImages.forEach(tagService::applyAutomaticTags);

        return savedImages.stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
        @Cacheable(cacheNames = CacheNames.IMAGE_SEARCH,
            key = "T(com.imagemanagement.cache.CacheKeyGenerator).imageSearchKey(#userId, #request)")
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

    @Override
    @CacheEvict(value = CacheNames.IMAGE_SEARCH, allEntries = true)
    public ImageDeleteResponse deleteImage(Long userId, Long imageId) {
        if (userId == null) {
            throw new BadRequestException("User id is required");
        }
        if (imageId == null) {
            throw new BadRequestException("Image id is required");
        }

        Image image = imageRepository.findWithUserAndThumbnailsById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));

        if (!Objects.equals(image.getUser().getId(), userId)) {
            throw new ForbiddenException("You do not have permission to delete this image");
        }

        removeStoredFiles(image);
        imageRepository.delete(image);
        return new ImageDeleteResponse(imageId, Instant.now());
    }

    @Override
    @CacheEvict(value = CacheNames.IMAGE_SEARCH, allEntries = true)
    public ImageSummaryResponse editImage(Long userId, Long imageId, ImageEditRequest request) {
        if (userId == null) {
            throw new BadRequestException("User id is required");
        }
        if (imageId == null) {
            throw new BadRequestException("Image id is required");
        }
        if (request == null || !request.hasAnyOperation()) {
            throw new BadRequestException("No edit operation was provided");
        }

        Image image = imageRepository.findWithUserAndThumbnailsById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));

        if (!Objects.equals(image.getUser().getId(), userId)) {
            throw new ForbiddenException("You do not have permission to edit this image");
        }

        if (!StringUtils.hasText(image.getFilePath())) {
            throw new IllegalStateException("Image file path is missing");
        }

        Path imagePath = Path.of(image.getFilePath());
        BufferedImage workingImage = readImageForEditing(imagePath);

        if (request.getCrop() != null) {
            workingImage = applyCrop(workingImage, request.getCrop());
        }

        if (request.getToneAdjustment() != null && request.getToneAdjustment().hasAdjustments()) {
            workingImage = applyToneAdjustment(workingImage, request.getToneAdjustment());
        }

        writeImage(workingImage, imagePath, resolveOutputFormat(image));
        updateImageMetadata(image, workingImage, imagePath);
        refreshThumbnails(image);

        return toSummaryResponse(image);
    }

    @Override
    public List<ImageSummaryResponse> getHighlightImages(Long userId, int size) {
        if (size <= 0) {
            throw new BadRequestException("Size must be greater than zero");
        }

        int pageSize = Math.min(size, MAX_HIGHLIGHT_SIZE);
        Pageable pageable = PageRequest.of(0, pageSize, Sort.by(Sort.Direction.DESC, "uploadTime"));
        Page<Image> page = imageRepository.findByUser_IdOrderByUploadTimeDesc(userId, pageable);
        return page.getContent().stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    private Image buildImageEntity(User user, FileStorageService.StoredFileInfo storedFile,
                                   ImagePrivacyLevel privacyLevel, String description, String contentHash) {
        Image image = new Image();
        image.setUser(user);
        image.setOriginalFilename(storedFile.originalFilename());
        image.setStoredFilename(storedFile.storedFilename());
        image.setFilePath(storedFile.absolutePath());
        image.setFileSize(storedFile.size());
        image.setContentHash(contentHash);
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
            buildImageContentEndpoint(image.getId()),
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
        response.setFilePath(buildImageContentEndpoint(image.getId()));
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
        response.setThumbnails(extractThumbnailSummaries(image));
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

    private List<ImageSummaryResponse.ThumbnailSummary> extractThumbnailSummaries(Image image) {
        Set<Thumbnail> thumbnails = image.getThumbnails();
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
                    summary.setFilePath(buildThumbnailContentEndpoint(image.getId(), thumbnail.getId()));
                    summary.setFileSize(thumbnail.getFileSize());
                    return summary;
                })
                .toList();
    }

    private String buildImageContentEndpoint(Long imageId) {
        if (imageId == null) {
            return null;
        }
        return "/api/images/" + imageId + "/content";
    }

    private String buildThumbnailContentEndpoint(Long imageId, Long thumbnailId) {
        if (imageId == null || thumbnailId == null) {
            return null;
        }
        return "/api/images/" + imageId + "/thumbnails/" + thumbnailId;
    }

    private void removeStoredFiles(Image image) {
        fileStorageService.deleteFile(image.getFilePath());
        if (!CollectionUtils.isEmpty(image.getThumbnails())) {
            image.getThumbnails()
                    .stream()
                    .map(Thumbnail::getFilePath)
                    .filter(StringUtils::hasText)
                    .forEach(fileStorageService::deleteFile);
        }
    }

    private BufferedImage readImageForEditing(Path imagePath) {
        try {
            BufferedImage bufferedImage = ImageIO.read(imagePath.toFile());
            if (bufferedImage == null) {
                throw new BadRequestException("Unsupported image format");
            }
            return convertToEditableType(bufferedImage);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read image content", ex);
        }
    }

    private BufferedImage applyCrop(BufferedImage source, ImageEditRequest.CropOperation crop) {
        int x = crop.getX();
        int y = crop.getY();
        int width = crop.getWidth();
        int height = crop.getHeight();

        if (x + width > source.getWidth() || y + height > source.getHeight()) {
            throw new BadRequestException("Crop area exceeds image bounds");
        }

        BufferedImage cropped = source.getSubimage(x, y, width, height);
        return deepCopy(cropped);
    }

    private BufferedImage applyToneAdjustment(BufferedImage source, ImageEditRequest.ToneAdjustment tone) {
        BufferedImage working = deepCopy(source);
        float brightness = tone.getBrightness() != null ? tone.getBrightness().floatValue() : 0f;
        float contrast = tone.getContrast() != null ? tone.getContrast().floatValue() : 0f;
        float warmth = tone.getWarmth() != null ? tone.getWarmth().floatValue() : 0f;

        if (brightness != 0f || contrast != 0f) {
            float scale = Math.max(0.1f, 1f + contrast);
            float offset = brightness * 255f;
            float[] scales = new float[]{scale, scale, scale, 1f};
            float[] offsets = new float[]{offset, offset, offset, 0f};
            RescaleOp rescaleOp = new RescaleOp(scales, offsets, null);
            working = rescaleOp.filter(working, null);
        }

        if (warmth != 0f) {
            applyWarmthShift(working, warmth);
        }

        return working;
    }

    private void applyWarmthShift(BufferedImage image, float warmth) {
        int width = image.getWidth();
        int height = image.getHeight();
        float redDelta = warmth * 25f;
        float blueDelta = -warmth * 25f;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;

                red = clampColor(red + redDelta);
                blue = clampColor(blue + blueDelta);

                int adjusted = (alpha << 24)
                        | (red << 16)
                        | (green << 8)
                        | blue;
                image.setRGB(x, y, adjusted);
            }
        }
    }

    private int clampColor(float channel) {
        return Math.min(255, Math.max(0, Math.round(channel)));
    }

    private void writeImage(BufferedImage image, Path targetPath, String format) {
        try {
            BufferedImage output = image;
            String normalizedFormat = format != null ? format.toLowerCase(Locale.ROOT) : "jpg";
            if ("jpg".equals(normalizedFormat) || "jpeg".equals(normalizedFormat)) {
                BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = rgbImage.createGraphics();
                graphics.drawImage(image, 0, 0, null);
                graphics.dispose();
                output = rgbImage;
            }

            if (!ImageIO.write(output, normalizedFormat, targetPath.toFile())) {
                throw new IllegalStateException("Failed to persist edited image");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist edited image", ex);
        }
    }

    private String resolveOutputFormat(Image image) {
        String extension = FilenameUtils.getExtension(image.getStoredFilename());
        if (StringUtils.hasText(extension)) {
            return extension.toLowerCase(Locale.ROOT);
        }
        if (StringUtils.hasText(image.getMimeType())) {
            String mime = image.getMimeType().toLowerCase(Locale.ROOT);
            if (mime.contains("png")) {
                return "png";
            }
        }
        return "jpg";
    }

    private BufferedImage convertToEditableType(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }
        return deepCopy(source);
    }

    private BufferedImage deepCopy(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return copy;
    }

    private void updateImageMetadata(Image image, BufferedImage bufferedImage, Path imagePath) {
        image.setWidth(bufferedImage.getWidth());
        image.setHeight(bufferedImage.getHeight());
        try {
            image.setFileSize(Files.size(imagePath));
            image.setContentHash(calculateContentHash(imagePath));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to update image metadata", ex);
        }
    }

    private void refreshThumbnails(Image image) {
        if (image == null) {
            return;
        }
        if (!CollectionUtils.isEmpty(image.getThumbnails())) {
            List<Thumbnail> existing = new ArrayList<>(image.getThumbnails());
            for (Thumbnail thumbnail : existing) {
                if (thumbnail != null && StringUtils.hasText(thumbnail.getFilePath())) {
                    fileStorageService.deleteFile(thumbnail.getFilePath());
                }
                image.removeThumbnail(thumbnail);
            }
        }
        thumbnailService.generateThumbnails(image);
    }

    private String calculateContentHash(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return calculateContentHash(inputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to calculate content hash", ex);
        }
    }

    private String calculateContentHash(Path path) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return calculateContentHash(inputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to calculate content hash", ex);
        }
    }

    private String calculateContentHash(InputStream inputStream) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to calculate content hash", ex);
        }
    }

    private record PendingUpload(MultipartFile file, String contentHash) {
    }
}