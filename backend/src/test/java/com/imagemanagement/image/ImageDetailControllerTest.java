package com.imagemanagement.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanagement.entity.ExifData;
import com.imagemanagement.entity.Image;
import com.imagemanagement.entity.ImageTag;
import com.imagemanagement.entity.Tag;
import com.imagemanagement.entity.Thumbnail;
import com.imagemanagement.entity.User;
import com.imagemanagement.entity.enums.ImagePrivacyLevel;
import com.imagemanagement.entity.enums.TagType;
import com.imagemanagement.entity.enums.ThumbnailSizeType;
import com.imagemanagement.entity.enums.UserRole;
import com.imagemanagement.entity.enums.UserStatus;
import com.imagemanagement.repository.ImageRepository;
import com.imagemanagement.repository.TagRepository;
import com.imagemanagement.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImageDetailControllerTest {

    private static final String DEFAULT_PASSWORD = "Password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        imageRepository.deleteAll();
        tagRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void getImageDetail_shouldReturnRichPayloadForOwner() throws Exception {
        User owner = persistUser("detail-owner", "detail-owner@example.com");
        Tag tag = persistTag("sunset", TagType.AI);
        Image image = persistImage(owner, tag, ImagePrivacyLevel.PRIVATE);

        String token = loginAndGetToken(owner.getUsername());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/images/{imageId}", image.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.id").value(image.getId()))
                .andExpect(jsonPath("$.data.owner.username").value(owner.getUsername()))
                .andExpect(jsonPath("$.data.exif.fNumber").value("f/2.8"))
                .andExpect(jsonPath("$.data.tagDetails.length()").value(1))
                .andExpect(jsonPath("$.data.tagDetails[0].tagName").value(tag.getTagName()))
                .andExpect(jsonPath("$.data.access.canEdit").value(true))
                .andExpect(jsonPath("$.data.access.canManageTags").value(true))
                .andExpect(jsonPath("$.data.summary.thumbnails.length()").value(1));
    }

    @Test
    void getImageDetail_shouldForbidPrivateImageForNonOwner() throws Exception {
        User owner = persistUser("detail-owner2", "detail-owner2@example.com");
        User viewer = persistUser("detail-viewer", "detail-viewer@example.com");
        Tag tag = persistTag("secret", TagType.CUSTOM);
        Image image = persistImage(owner, tag, ImagePrivacyLevel.PRIVATE);

        String token = loginAndGetToken(viewer.getUsername());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/images/{imageId}", image.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void getImageDetail_shouldReturn404ForMissingImage() throws Exception {
        User viewer = persistUser("detail-missing", "detail-missing@example.com");
        String token = loginAndGetToken(viewer.getUsername());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/images/{imageId}", 999999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
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

    private Tag persistTag(String name, TagType type) {
        Tag tag = new Tag();
        tag.setTagName(name);
        tag.setTagType(type);
        tag.setUsageCount(0);
        return tagRepository.save(tag);
    }

    private Image persistImage(User owner, Tag tag, ImagePrivacyLevel privacyLevel) {
        Image image = new Image();
        image.setUser(owner);
        image.setOriginalFilename("detail.jpg");
        image.setStoredFilename(owner.getId() + "-detail.jpg");
        image.setFilePath("/app/uploads/" + owner.getId() + "/detail.jpg");
        image.setFileSize(4096L);
        image.setMimeType("image/jpeg");
        image.setDescription("golden hour test image");
        image.setPrivacyLevel(privacyLevel);
        image.setUploadTime(LocalDateTime.now());
        image.setWidth(1920);
        image.setHeight(1080);
        image.setContentHash("hash-value" + owner.getId());

        ExifData exifData = new ExifData();
        exifData.setImage(image);
        exifData.setCameraMake("Canon");
        exifData.setCameraModel("EOS R6");
        exifData.setFNumber("f/2.8");
        exifData.setExposureTime("1/160");
        exifData.setIsoSpeed(200);
        exifData.setFocalLength("85mm");
        exifData.setLatitude(BigDecimal.valueOf(31.2304));
        exifData.setLongitude(BigDecimal.valueOf(121.4737));
        exifData.setLocationName("Shanghai");
        exifData.setTakenTime(LocalDateTime.now().minusDays(2));
        image.setExifData(exifData);

        ImageTag imageTag = new ImageTag();
        imageTag.setImage(image);
        imageTag.setTag(tag);
        imageTag.setConfidence(new BigDecimal("0.92"));
        image.addImageTag(imageTag);

        Thumbnail thumbnail = new Thumbnail();
        thumbnail.setImage(image);
        thumbnail.setSizeType(ThumbnailSizeType.SMALL);
        thumbnail.setFilePath("/app/thumbnails/" + owner.getId() + "/detail-small.jpg");
        thumbnail.setFileSize(1024);
        thumbnail.setWidth(480);
        thumbnail.setHeight(270);
        image.addThumbnail(thumbnail);

        return imageRepository.save(image);
    }

    private String loginAndGetToken(String usernameOrEmail) throws Exception {
        String payload = "{" +
                "\"usernameOrEmail\":\"" + usernameOrEmail + "\"," +
                "\"password\":\"" + DEFAULT_PASSWORD + "\"}";

        String response = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(response);
        String token = jsonNode.path("data").path("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }
}
