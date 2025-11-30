package com.imagemanagement.service;

import com.imagemanagement.entity.Image;
import com.imagemanagement.entity.Thumbnail;
import com.imagemanagement.entity.enums.ImagePrivacyLevel;
import com.imagemanagement.exception.ForbiddenException;
import com.imagemanagement.exception.ResourceNotFoundException;
import com.imagemanagement.repository.ImageRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class ImageContentService {

    private final ImageRepository imageRepository;

    public ImageContentService(ImageRepository imageRepository) {
        this.imageRepository = imageRepository;
    }

    public ContentResource loadOriginal(Long imageId, Long requesterId) {
        Image image = getAccessibleImage(imageId, requesterId);
        MediaType mediaType = parseMediaType(image.getMimeType());
        return toContentResource(Paths.get(image.getFilePath()), mediaType);
    }

    public ContentResource loadThumbnail(Long imageId, Long thumbnailId, Long requesterId) {
        Image image = getAccessibleImage(imageId, requesterId);
        Thumbnail thumbnail = image.getThumbnails().stream()
                .filter(candidate -> Objects.equals(candidate.getId(), thumbnailId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Thumbnail not found"));
        return toContentResource(Paths.get(thumbnail.getFilePath()), MediaType.IMAGE_JPEG);
    }

    private Image getAccessibleImage(Long imageId, Long requesterId) {
        Image image = imageRepository.findWithUserAndThumbnailsById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));
        ensureAccess(image, requesterId);
        return image;
    }

    private void ensureAccess(Image image, Long requesterId) {
        if (image.getPrivacyLevel() == ImagePrivacyLevel.PRIVATE
                && !Objects.equals(image.getUser().getId(), requesterId)) {
            throw new ForbiddenException("You do not have permission to view this image");
        }
    }

    private ContentResource toContentResource(Path path, MediaType mediaType) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            throw new ResourceNotFoundException("File not found");
        }
        try {
            Resource resource = new UrlResource(Objects.requireNonNull(normalized.toUri()));
            long contentLength = Files.size(normalized);
            return new ContentResource(resource, mediaType, contentLength);
        } catch (IOException ex) {
            throw new ResourceNotFoundException("Failed to read file");
        }
    }

    private MediaType parseMediaType(String mimeType) {
        String sanitizedMimeType = mimeType;
        if (sanitizedMimeType != null && StringUtils.hasText(sanitizedMimeType)) {
            try {
                return MediaType.parseMediaType(sanitizedMimeType);
            } catch (IllegalArgumentException ignored) {
                // fall through to octet-stream
            }
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    public record ContentResource(Resource resource, MediaType mediaType, long contentLength) {
    }
}