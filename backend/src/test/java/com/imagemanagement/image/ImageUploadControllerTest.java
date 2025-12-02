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
import com.imagemanagement.support.TestImageResource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasToString;
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

        @ParameterizedTest
        @ValueSource(strings = {"beach.jpeg", "man2.png", "tree.jpeg"})
        void uploadImages_shouldPersistMetadataAndReturnResponse(String filename) throws Exception {
        User user = persistUser();
        String token = loginAndGetToken();

        TestImageResource testImage = TestImageResource.load(filename);
        MockMultipartFile file = testImage.asMultipart("files");

        mockMvc.perform(multipart("/api/images/upload")
                .file(Objects.requireNonNull(file))
                        .param("privacyLevel", "PRIVATE")
                        .param("description", "test image")
                        .header("Authorization", "Bearer " + token)
                        .contentType(Objects.requireNonNull(MediaType.MULTIPART_FORM_DATA)))
                .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].originalFilename").value(filename))
                .andExpect(jsonPath("$.data[0].storedFilename").isNotEmpty());

        assertThat(imageRepository.count()).isEqualTo(1);
        Image storedImage = imageRepository.findAll().get(0);
        assertThat(storedImage.getOriginalFilename()).isEqualTo(filename);
        assertThat(storedImage.getMimeType()).isEqualTo(testImage.getMimeType());
        assertThat(storedImage.getFileSize()).isEqualTo(testImage.getSize());
        assertThat(storedImage.getWidth()).isEqualTo(testImage.getWidth());
        assertThat(storedImage.getHeight()).isEqualTo(testImage.getHeight());
        assertThat(storedImage.getThumbnails()).hasSize(thumbnailProperties.getPresets().size());
        storedImage.getThumbnails().forEach(thumbnail ->
                assertThat(Files.exists(Path.of(thumbnail.getFilePath()))).isTrue());
        assertThat(Files.exists(uploadDir)).isTrue();
        try (var stream = Files.list(uploadDir.resolve(String.valueOf(user.getId())))) {
            assertThat(stream.count()).isEqualTo(1);
        }
    }

    @Test
    @SuppressWarnings("null")
    void uploadImages_shouldRejectDuplicateFilesForSameUser() throws Exception {
        persistUser();
        String token = loginAndGetToken();

        TestImageResource duplicateImage = TestImageResource.load("beach.jpeg");
        MockMultipartFile file = duplicateImage.asMultipart("files");

        mockMvc.perform(multipart("/api/images/upload")
                        .file(Objects.requireNonNull(file))
                        .param("privacyLevel", "PRIVATE")
                        .header("Authorization", "Bearer " + token)
                        .contentType(Objects.requireNonNull(MediaType.MULTIPART_FORM_DATA)))
                .andExpect(status().isOk());

        MockMultipartFile duplicateFile = duplicateImage.asMultipart("files");

        mockMvc.perform(multipart("/api/images/upload")
            .file(Objects.requireNonNull(duplicateFile))
                .param("privacyLevel", "PRIVATE")
                .header("Authorization", "Bearer " + token)
                .contentType(Objects.requireNonNull(MediaType.MULTIPART_FORM_DATA)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", hasToString(containsString("beach.jpeg"))))
            .andExpect(jsonPath("$.data.duplicates[0]").value("beach.jpeg"));
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