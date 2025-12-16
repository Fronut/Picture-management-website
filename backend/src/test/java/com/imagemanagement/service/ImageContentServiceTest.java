package com.imagemanagement.service;

import com.imagemanagement.entity.Image;
import com.imagemanagement.entity.Thumbnail;
import com.imagemanagement.entity.User;
import com.imagemanagement.entity.enums.ImagePrivacyLevel;
import com.imagemanagement.entity.enums.ThumbnailSizeType;
import com.imagemanagement.entity.enums.UserRole;
import com.imagemanagement.exception.ForbiddenException;
import com.imagemanagement.repository.ImageRepository;
import com.imagemanagement.repository.UserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageContentServiceTest {

    @Mock
    private ImageRepository imageRepository;

    @Mock
    private UserRepository userRepository;

    @TempDir
    Path tempDir;

    private ImageContentService imageContentService;

    @BeforeEach
    void setUp() {
        imageContentService = new ImageContentService(imageRepository, userRepository);
    }

    @Test
    void loadOriginal_shouldAllowAdminToAccessPrivateImage() throws IOException {
        Path imagePath = createTempFile("admin-access.jpg");
        Image image = buildImageWithFile(imagePath, ImagePrivacyLevel.PRIVATE);

        when(imageRepository.findWithUserAndThumbnailsById(1L)).thenReturn(Optional.of(image));
        when(userRepository.existsByIdAndRole(99L, UserRole.ADMIN)).thenReturn(true);

        ImageContentService.ContentResource content = imageContentService.loadOriginal(1L, 99L);

        assertThat(content).isNotNull();
        assertThat(content.mediaType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(content.contentLength()).isEqualTo(Files.size(imagePath));
    }

    @Test
    void loadOriginal_shouldRejectNonOwnerForPrivateImage() throws IOException {
        Path imagePath = createTempFile("restricted.jpg");
        Image image = buildImageWithFile(imagePath, ImagePrivacyLevel.PRIVATE);

        when(imageRepository.findWithUserAndThumbnailsById(5L)).thenReturn(Optional.of(image));
        when(userRepository.existsByIdAndRole(42L, UserRole.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> imageContentService.loadOriginal(5L, 42L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void loadThumbnail_shouldReturnResourceWhenAccessAllowed() throws IOException {
        Path imagePath = createTempFile("thumb-source.jpg");
        Path thumbnailPath = createTempFile("thumb-small.jpg");
        Image image = buildImageWithFile(imagePath, ImagePrivacyLevel.PRIVATE);
        Thumbnail thumbnail = new Thumbnail();
        thumbnail.setId(200L);
        thumbnail.setFilePath(thumbnailPath.toString());
        thumbnail.setWidth(320);
        thumbnail.setHeight(200);
        thumbnail.setFileSize((int) Files.size(thumbnailPath));
        thumbnail.setSizeType(ThumbnailSizeType.SMALL);
        image.addThumbnail(thumbnail);

        when(imageRepository.findWithUserAndThumbnailsById(7L)).thenReturn(Optional.of(image));
        when(userRepository.existsByIdAndRole(100L, UserRole.ADMIN)).thenReturn(true);

        ImageContentService.ContentResource content = imageContentService.loadThumbnail(7L, thumbnail.getId(), 100L);

        assertThat(content).isNotNull();
        assertThat(content.contentLength()).isEqualTo(Files.size(thumbnailPath));
    }

    private Image buildImageWithFile(Path filePath, ImagePrivacyLevel privacyLevel) {
        User owner = new User();
        owner.setId(10L);
        owner.setUsername("owner");
        owner.setRole(UserRole.USER);

        Image image = new Image();
        image.setId(1L);
        image.setUser(owner);
        image.setPrivacyLevel(privacyLevel);
        image.setMimeType("image/jpeg");
        image.setFilePath(filePath.toString());
        return image;
    }

    private Path createTempFile(String filename) throws IOException {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, "binary-data");
        return file;
    }
}
