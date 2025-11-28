package com.imagemanagement.service;

import com.imagemanagement.config.FileStorageProperties;
import com.imagemanagement.exception.BadRequestException;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    private final FileStorageProperties properties;
    private Path rootLocation;
    private Set<String> allowedTypes;

    public FileStorageService(FileStorageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        this.rootLocation = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize upload directory", e);
        }
        this.allowedTypes = new HashSet<>();
        if (properties.getAllowedTypes() != null) {
            for (String type : properties.getAllowedTypes()) {
                if (StringUtils.hasText(type)) {
                    allowedTypes.add(type.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    public StoredFileInfo storeFile(MultipartFile file, Long userId) {
        validateFile(file);

        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            originalFilename = "uploaded";
        }
        originalFilename = StringUtils.cleanPath(Objects.requireNonNull(originalFilename));
        if (originalFilename.contains("..")) {
            throw new BadRequestException("Filename contains invalid path sequence");
        }

        String extension = FilenameUtils.getExtension(originalFilename);
        String storedFilename = UUID.randomUUID() + (StringUtils.hasText(extension) ? "." + extension : "");
        Path userDirectory = rootLocation.resolve(String.valueOf(userId));

        try {
            Files.createDirectories(userDirectory);
            Path targetLocation = userDirectory.resolve(storedFilename).normalize();
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }
            String absolutePath = targetLocation.toAbsolutePath().normalize().toString().replace('\\', '/');
            return new StoredFileInfo(originalFilename, storedFilename, absolutePath, file.getSize(), resolveContentType(file));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not store file " + originalFilename, ex);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty");
        }

        if (file.getSize() > properties.getMaxSize()) {
            throw new BadRequestException("File exceeds maximum allowed size");
        }

        if (!allowedTypes.isEmpty()) {
            String rawType = file.getContentType();
            if (rawType == null || rawType.isBlank()) {
                return;
            }
            String normalizedType = rawType.toLowerCase(Locale.ROOT);
            if (!allowedTypes.contains(normalizedType)) {
                throw new BadRequestException("File type " + rawType + " is not supported");
            }
        }
    }

    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType)) {
            contentType = "application/octet-stream";
        }
        return contentType;
    }

    public record StoredFileInfo(String originalFilename, String storedFilename, String absolutePath, long size, String contentType) {
    }
}