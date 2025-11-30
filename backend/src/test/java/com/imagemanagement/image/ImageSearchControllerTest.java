package com.imagemanagement.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanagement.dto.request.ImageSearchRequest;
import com.imagemanagement.entity.Image;
import com.imagemanagement.entity.ImageTag;
import com.imagemanagement.entity.Tag;
import com.imagemanagement.entity.User;
import com.imagemanagement.entity.enums.ImagePrivacyLevel;
import com.imagemanagement.entity.enums.TagType;
import com.imagemanagement.entity.enums.UserRole;
import com.imagemanagement.entity.enums.UserStatus;
import com.imagemanagement.repository.ImageRepository;
import com.imagemanagement.repository.TagRepository;
import com.imagemanagement.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.lang.NonNull;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImageSearchControllerTest {

    private static final String DEFAULT_PASSWORD = "Password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        imageRepository.deleteAll();
        tagRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void searchImages_shouldRespectOwnershipAndTags() throws Exception {
        User owner = persistUser("search-owner", "owner@example.com");
        Tag city = persistTag("city");
        Tag travel = persistTag("travel");

        Image publicImage = persistImage(owner, "public.jpg", ImagePrivacyLevel.PUBLIC, 1280, 720, List.of(city));
        persistImage(owner, "private.jpg", ImagePrivacyLevel.PRIVATE, 1280, 720, List.of(travel));

        String token = loginAndGetToken(owner.getUsername());
        ImageSearchRequest request = new ImageSearchRequest();
        request.setOnlyOwn(true);
        request.setTags(List.of("city"));
        request.setPage(0);
        request.setSize(10);

        mockMvc.perform(post("/api/images/search")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(json(request))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()" ).value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(publicImage.getId()))
            .andExpect(jsonPath("$.data.content[0].tags[0]").value("city"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void searchImages_shouldHidePrivateImagesFromOtherUsers() throws Exception {
        User owner = persistUser("search-owner", "owner@example.com");
        User viewer = persistUser("search-viewer", "viewer@example.com");
        Tag city = persistTag("city");

        Image publicImage = persistImage(owner, "public.jpg", ImagePrivacyLevel.PUBLIC, 1280, 720, List.of(city));
        persistImage(owner, "private.jpg", ImagePrivacyLevel.PRIVATE, 1280, 720, List.of(city));

        String token = loginAndGetToken(viewer.getUsername());
        ImageSearchRequest request = new ImageSearchRequest();
        request.setOnlyOwn(false);
        request.setPage(0);
        request.setSize(10);

        mockMvc.perform(post("/api/images/search")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(json(request))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()" ).value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(publicImage.getId()));
    }

    @Test
    void searchImages_shouldRejectInvalidDimensionRange() throws Exception {
        User owner = persistUser("dimension-owner", "dimension@example.com");
        String token = loginAndGetToken(owner.getUsername());

        ImageSearchRequest request = new ImageSearchRequest();
        request.setOnlyOwn(true);
        request.setMinWidth(1000);
        request.setMaxWidth(500);

        mockMvc.perform(post("/api/images/search")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(json(request))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    private User persistUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);
        return userRepository.save(user);
    }

    private Tag persistTag(String name) {
        Tag tag = new Tag();
        tag.setTagName(name);
        tag.setTagType(TagType.CUSTOM);
        return tagRepository.save(tag);
    }

    private Image persistImage(User owner,
                               String originalFilename,
                               ImagePrivacyLevel privacyLevel,
                               int width,
                               int height,
                               List<Tag> tags) {
        Image image = new Image();
        image.setUser(owner);
        image.setOriginalFilename(originalFilename);
        image.setStoredFilename(owner.getId() + "-" + originalFilename);
        image.setFilePath("/app/uploads/" + owner.getId() + "/" + originalFilename);
        image.setFileSize(2048L);
        image.setMimeType("image/jpeg");
        image.setDescription("test image");
        image.setPrivacyLevel(privacyLevel);
        image.setWidth(width);
        image.setHeight(height);
        image.setUploadTime(LocalDateTime.now());

        if (tags != null) {
            tags.forEach(tag -> {
                ImageTag imageTag = new ImageTag();
                imageTag.setImage(image);
                imageTag.setTag(tag);
                image.addImageTag(imageTag);
            });
        }

        return imageRepository.save(image);
    }

    private String loginAndGetToken(String usernameOrEmail) throws Exception {
        String payload = "{" +
                "\"usernameOrEmail\":\"" + usernameOrEmail + "\"," +
                "\"password\":\"" + DEFAULT_PASSWORD + "\"}";

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = jsonNode.path("data").path("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    @NonNull
    private String json(Object value) throws Exception {
        return Objects.requireNonNull(objectMapper.writeValueAsString(value));
    }
}
