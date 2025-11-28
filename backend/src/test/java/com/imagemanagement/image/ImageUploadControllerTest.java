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

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImageUploadControllerTest {

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

    @Value("${app.file.upload-dir}")
    private Path uploadDir;

    @Value("${app.thumbnail.base-dir}")
    private Path thumbnailDir;

    @Autowired
    private ThumbnailProperties thumbnailProperties;

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
    void uploadImages_shouldPersistMetadataAndReturnResponse() throws Exception {
        User user = persistUser();
        String token = loginAndGetToken();

        MockMultipartFile file = new MockMultipartFile(
                "files",
                "sample.png",
                "image/png",
                createPngBytes());

        mockMvc.perform(multipart("/api/images/upload")
                        .file(file)
                        .param("privacyLevel", "PRIVATE")
                        .param("description", "test image")
                        .header("Authorization", "Bearer " + token)
                        .contentType(Objects.requireNonNull(MediaType.MULTIPART_FORM_DATA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].originalFilename").value("sample.png"))
                .andExpect(jsonPath("$.data[0].storedFilename").isNotEmpty());

        assertThat(imageRepository.count()).isEqualTo(1);
        Image storedImage = imageRepository.findAll().get(0);
        assertThat(storedImage.getThumbnails()).hasSize(thumbnailProperties.getPresets().size());
        storedImage.getThumbnails().forEach(thumbnail ->
                assertThat(Files.exists(Path.of(thumbnail.getFilePath()))).isTrue());
        assertThat(Files.exists(uploadDir)).isTrue();
        try (var stream = Files.list(uploadDir.resolve(String.valueOf(user.getId())))) {
            assertThat(stream.count()).isEqualTo(1);
        }
    }

    private User persistUser() {
        User user = new User();
        user.setUsername("uploaduser");
        user.setEmail("upload@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password123"));
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);
        return userRepository.save(user);
    }

    private String loginAndGetToken() throws Exception {
        String payload = "{" +
                "\"usernameOrEmail\":\"uploaduser\"," +
                "\"password\":\"Password123\"}";

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("data").path("token").asText();
    }

    private byte[] createPngBytes() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
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