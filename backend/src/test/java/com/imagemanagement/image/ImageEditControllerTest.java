package com.imagemanagement.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanagement.config.ThumbnailProperties;
import com.imagemanagement.entity.Image;
import com.imagemanagement.entity.User;
import com.imagemanagement.entity.enums.UserRole;
import com.imagemanagement.entity.enums.UserStatus;
import com.imagemanagement.repository.ImageRepository;
import com.imagemanagement.repository.UserRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImageEditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ThumbnailProperties thumbnailProperties;

    @Value("${app.file.upload-dir}")
    private Path uploadDir;

    @Value("${app.thumbnail.base-dir}")
    private Path thumbnailDir;

    @BeforeEach
    void setUp() throws IOException {
        imageRepository.deleteAll();
        userRepository.deleteAll();
        deleteDirectory(uploadDir);
        deleteDirectory(thumbnailDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteDirectory(uploadDir);
        deleteDirectory(thumbnailDir);
    }

    @Test
    void editImage_shouldCropAndRegenerateThumbnails() throws Exception {
        persistUser();
        String token = loginAndGetToken();
        long imageId = uploadSampleImage(token, "edit-crop.png", 3, 3);

        String payload = "{" +
                "\"crop\":{\"x\":0,\"y\":0,\"width\":1,\"height\":1}," +
                "\"toneAdjustment\":{\"brightness\":0.2}" +
                "}";

        mockMvc.perform(post("/api/images/{imageId}/edit", imageId)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(payload)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.width").value(1))
                .andExpect(jsonPath("$.data.height").value(1))
                .andExpect(jsonPath("$.data.thumbnails.length()").value(thumbnailProperties.getPresets().size()));

        Image editedImage = imageRepository.findWithUserAndThumbnailsById(imageId).orElseThrow();
        assertThat(editedImage.getWidth()).isEqualTo(1);
        assertThat(editedImage.getHeight()).isEqualTo(1);
        assertThat(editedImage.getThumbnails()).hasSize(thumbnailProperties.getPresets().size());
        editedImage.getThumbnails()
                .forEach(thumbnail -> assertThat(Files.exists(Path.of(thumbnail.getFilePath()))).isTrue());
    }

    @Test
    void editImage_shouldRejectMissingOperations() throws Exception {
        persistUser();
        String token = loginAndGetToken();
        long imageId = uploadSampleImage(token, "edit-invalid.png", 2, 2);

        mockMvc.perform(post("/api/images/{imageId}/edit", imageId)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("{}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.validOperationSet").value("至少需要提供一种编辑操作"));
    }

    private User persistUser() {
        User user = new User();
        user.setUsername("edituser");
        user.setEmail("edit@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password123"));
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);
        return userRepository.save(user);
    }

    private String loginAndGetToken() throws Exception {
        String payload = "{" +
                "\"usernameOrEmail\":\"edituser\"," +
                "\"password\":\"Password123\"}";

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("data").path("token").asText();
    }

    private long uploadSampleImage(String token, String filename, int width, int height) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                filename,
                "image/png",
                createPngBytes(width, height));

        MvcResult result = mockMvc.perform(multipart("/api/images/upload")
                        .file(file)
                        .param("privacyLevel", "PRIVATE")
                        .header("Authorization", "Bearer " + token)
                        .contentType(Objects.requireNonNull(MediaType.MULTIPART_FORM_DATA)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("data").get(0).path("id").asLong();
    }

    private byte[] createPngBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (directory != null && Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                paths.sorted((p1, p2) -> p2.compareTo(p1))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                            }
                        });
            }
        }
    }
}
