package com.imagemanagement.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImageDeletionControllerTest {

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
    void deleteImage_shouldRemoveMetadataAndFiles() throws Exception {
        User owner = persistUser("delete-owner", "delete-owner@example.com");
        String token = loginAndGetToken(owner.getUsername());
        long imageId = uploadSampleImage(token);

        Image stored = imageRepository.findWithUserAndThumbnailsById(imageId).orElseThrow();
        Path imagePath = Path.of(stored.getFilePath());
        List<Path> thumbnailPaths = stored.getThumbnails().stream()
                .map(thumbnail -> Path.of(thumbnail.getFilePath()))
                .toList();

        mockMvc.perform(delete("/api/images/{imageId}", imageId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedImageId").value(imageId));

        assertThat(imageRepository.findById(imageId)).isEmpty();
        assertThat(Files.exists(imagePath)).isFalse();
        thumbnailPaths.forEach(path -> assertThat(Files.exists(path)).isFalse());
    }

    @Test
    void deleteImage_shouldFailForNonOwner() throws Exception {
        User owner = persistUser("delete-owner", "delete-owner@example.com");
        String ownerToken = loginAndGetToken(owner.getUsername());
        long imageId = uploadSampleImage(ownerToken);
        Image stored = imageRepository.findById(imageId).orElseThrow();

        User other = persistUser("delete-other", "delete-other@example.com");
        String otherToken = loginAndGetToken(other.getUsername());

        mockMvc.perform(delete("/api/images/{imageId}", imageId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        assertThat(imageRepository.findById(imageId)).isPresent();
        assertThat(Files.exists(Path.of(stored.getFilePath()))).isTrue();
    }

    private User persistUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Password123"));
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);
        return userRepository.save(user);
    }

    private String loginAndGetToken(String usernameOrEmail) throws Exception {
        String payload = "{" +
                "\"usernameOrEmail\":\"" + usernameOrEmail + "\"," +
                "\"password\":\"Password123\"}";

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("data").path("token").asText();
    }

    private long uploadSampleImage(String token) throws Exception {
        TestImageResource testImage = TestImageResource.load("road2.jpeg");
        MockMultipartFile file = testImage.asMultipart("files");

        MvcResult result = mockMvc.perform(multipart("/api/images/upload")
                        .file(Objects.requireNonNull(file))
                        .header("Authorization", "Bearer " + token)
                        .contentType(Objects.requireNonNull(MediaType.MULTIPART_FORM_DATA)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("data").get(0).path("id").asLong();
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
