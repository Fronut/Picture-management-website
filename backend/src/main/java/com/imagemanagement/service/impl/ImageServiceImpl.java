package com.imagemanagement.service.impl;

import com.imagemanagement.dto.response.ImageUploadResponse;
import com.imagemanagement.entity.Image;
import com.imagemanagement.entity.User;
import com.imagemanagement.entity.enums.ImagePrivacyLevel;
import com.imagemanagement.exception.BadRequestException;
import com.imagemanagement.repository.ImageRepository;
import com.imagemanagement.repository.UserRepository;
import com.imagemanagement.service.FileStorageService;
import com.imagemanagement.service.ImageService;
import jakarta.transaction.Transactional;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class ImageServiceImpl implements ImageService {

    private final ImageRepository imageRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    public ImageServiceImpl(ImageRepository imageRepository, UserRepository userRepository, FileStorageService fileStorageService) {
        this.imageRepository = imageRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
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
        return savedImages.stream()
                .map(this::toResponse)
                .toList();
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

        setImageDimensions(image, storedFile.absolutePath());
        return image;
    }

    private void setImageDimensions(Image image, String absolutePath) {
        try (var inputStream = Files.newInputStream(Path.of(absolutePath))) {
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
}