package com.imagemanagement.service;

import com.imagemanagement.config.FileStorageProperties;
import com.imagemanagement.exception.BadRequestException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setUploadDir(tempDir.toString());
        properties.setMaxSize(1024 * 1024);
        properties.setAllowedTypes(List.of("image/png"));

        fileStorageService = new FileStorageService(properties);
        fileStorageService.init();
    }

    @Test
    void storeFile_shouldPersistFileToUserDirectory() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "photo.png",
                "image/png",
                new byte[] {1, 2, 3});

        FileStorageService.StoredFileInfo info = fileStorageService.storeFile(file, 42L);

        assertThat(info.originalFilename()).isEqualTo("photo.png");
        assertThat(info.storedFilename()).isNotBlank();
        assertThat(Files.exists(Path.of(info.absolutePath()))).isTrue();
        assertThat(Files.readAllBytes(Path.of(info.absolutePath()))).containsExactly(1, 2, 3);
    }

    @Test
    void storeFile_shouldRejectOversizedFiles() {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "photo.png",
                "image/png",
                new byte[2_000_000]);

        assertThatThrownBy(() -> fileStorageService.storeFile(file, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("size");
    }

    @Test
    void storeFile_shouldSkipWhitelistWhenContentTypeMissing() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "photo",
                null,
                new byte[] {9, 9, 9});

        FileStorageService.StoredFileInfo info = fileStorageService.storeFile(file, 7L);

        assertThat(info.contentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void storeFile_shouldRejectUnsupportedMimeType() {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "photo.gif",
                "image/gif",
                new byte[] {1});

        assertThatThrownBy(() -> fileStorageService.storeFile(file, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not supported");
    }
}
