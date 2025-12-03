package com.imagemanagement.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanagement.ai.AiServiceClient;
import com.imagemanagement.ai.dto.AiTagSuggestion;
import com.imagemanagement.ai.dto.AiTagSuggestionResponse;
import com.imagemanagement.dto.request.AiTagAssignmentRequest;
import com.imagemanagement.dto.request.AiTagGenerationRequest;
import com.imagemanagement.dto.request.TagAssignmentRequest;
import com.imagemanagement.entity.Image;
import com.imagemanagement.entity.ImageTag;
import com.imagemanagement.entity.Tag;
import com.imagemanagement.entity.User;
import com.imagemanagement.entity.enums.TagType;
import com.imagemanagement.entity.enums.UserRole;
import com.imagemanagement.entity.enums.UserStatus;
import com.imagemanagement.repository.ImageRepository;
import com.imagemanagement.repository.ImageTagRepository;
import com.imagemanagement.repository.TagRepository;
import com.imagemanagement.repository.UserRepository;
import com.imagemanagement.security.CustomUserDetails;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@SuppressWarnings({"null"})
class TagControllerIntegrationTest {

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
    private ImageTagRepository imageTagRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private AiServiceClient aiServiceClient;

    private User owner;

    private Image image;

    @BeforeEach
    void setUp() {
        imageTagRepository.deleteAll();
        imageRepository.deleteAll();
        tagRepository.deleteAll();
        userRepository.deleteAll();

        owner = persistUser("tag-owner", "tag-owner@example.com");
        image = persistImage(owner, "sample.jpg");
    }

    @Test
    void getTags_shouldReturnExistingTagsForImage() throws Exception {
        Tag tag = createTag("sunset", TagType.CUSTOM, 1);
        linkTag(image, tag);

        mockMvc.perform(get("/api/images/{imageId}/tags", image.getId())
                .with(authentication(buildAuthentication(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].tagName").value("sunset"))
                .andExpect(jsonPath("$.data[0].tagType").value("CUSTOM"));
    }

    @Test
    void addCustomTags_shouldAttachTagsForOwner() throws Exception {
        TagAssignmentRequest payload = new TagAssignmentRequest(List.of("Travel", "Portrait"));

        mockMvc.perform(post("/api/images/{imageId}/tags/custom", image.getId())
                        .with(authentication(buildAuthentication(owner)))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].tagName").value("Portrait"))
                .andExpect(jsonPath("$.data[1].tagName").value("Travel"));

        assertThat(imageTagRepository.findAllByImageId(image.getId())).hasSize(2);
    }

    @Test
    void deleteTag_shouldRemoveAssociationAndDecrementUsage() throws Exception {
        TagAssignmentRequest payload = new TagAssignmentRequest(List.of("Macro"));
        var createResult = mockMvc.perform(post("/api/images/{imageId}/tags/custom", image.getId())
                        .with(authentication(buildAuthentication(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long tagId = json.at("/data/0/tagId").asLong();

        mockMvc.perform(delete("/api/images/{imageId}/tags/{tagId}", image.getId(), tagId)
                        .with(authentication(buildAuthentication(owner))))
                .andExpect(status().isOk());

        assertThat(imageTagRepository.existsByImage_IdAndTag_Id(image.getId(), tagId)).isFalse();
        Tag tag = tagRepository.findById(tagId).orElseThrow();
        assertThat(tag.getUsageCount()).isZero();
    }

    @Test
    void getPopularTags_shouldRespectLimitAndOrder() throws Exception {
        createTag("alpha", TagType.CUSTOM, 5);
        createTag("beta", TagType.CUSTOM, 2);

        mockMvc.perform(get("/api/tags/popular").param("limit", "1")
                .with(authentication(buildAuthentication(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].tagName").value("alpha"));
    }

    @Test
    void addAiTags_shouldDeduplicateAndStoreWithAiType() throws Exception {
        AiTagAssignmentRequest request = new AiTagAssignmentRequest(List.of(
                new AiTagAssignmentRequest.TagSuggestion("Sky", new BigDecimal("0.60")),
                new AiTagAssignmentRequest.TagSuggestion("sky", new BigDecimal("0.95"))
        ));

        mockMvc.perform(post("/api/images/{imageId}/tags/ai", image.getId())
                        .with(authentication(buildAuthentication(owner)))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].tagType").value("AI"))
                .andExpect(jsonPath("$.data[0].confidence").value(0.95));

        Tag stored = tagRepository.findByTagNameIgnoreCase("sky").orElseThrow();
        assertThat(stored.getTagType()).isEqualTo(TagType.AI);
        assertThat(stored.getUsageCount()).isEqualTo(1);
    }

        @Test
        void generateAiTags_shouldInvokeAiServiceAndPersistResponse() throws Exception {
        AiTagSuggestionResponse aiResponse = new AiTagSuggestionResponse(
            List.of(new AiTagSuggestion("ocean", 0.91, "test")),
            java.util.Map.of("width", 100));
        given(aiServiceClient.suggestTags(any(), anyString(), anyList(), any())).willReturn(aiResponse);

        AiTagGenerationRequest request = new AiTagGenerationRequest(List.of("vacation"), 3);

        mockMvc.perform(post("/api/images/{imageId}/tags/ai/generate", image.getId())
                .with(authentication(buildAuthentication(owner)))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].tagName").value("ocean"))
            .andExpect(jsonPath("$.data[0].tagType").value("AI"));

        assertThat(imageTagRepository.findAllByImageId(image.getId())).hasSize(1);
        }

    @Test
    void addCustomTags_shouldRejectWhenUserDoesNotOwnImage() throws Exception {
        User otherUser = persistUser("intruder", "intruder@example.com");
        TagAssignmentRequest payload = new TagAssignmentRequest(List.of("Forbidden"));

        mockMvc.perform(post("/api/images/{imageId}/tags/custom", image.getId())
                        .with(authentication(buildAuthentication(otherUser)))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You do not have permission to modify this image"));

        assertThat(imageTagRepository.findAllByImageId(image.getId())).isEmpty();
    }

    private Authentication buildAuthentication(User user) {
        CustomUserDetails principal = new CustomUserDetails(userRepository.findById(user.getId()).orElseThrow());
        return new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities());
    }

    private User persistUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);
        return userRepository.save(user);
    }

    private Image persistImage(User owner, String originalFilename) {
        Image newImage = new Image();
        newImage.setUser(owner);
        newImage.setOriginalFilename(originalFilename);
        newImage.setStoredFilename(originalFilename + "-stored");
        try {
            Path tempFile = Files.createTempFile("img-", originalFilename);
            Files.writeString(tempFile, "dummy-image", StandardCharsets.UTF_8);
            tempFile.toFile().deleteOnExit();
            newImage.setFilePath(tempFile.toAbsolutePath().toString());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to prepare test image", ex);
        }
        newImage.setFileSize(1_024L);
        newImage.setMimeType("image/jpeg");
        newImage.setUploadTime(LocalDateTime.now());
        newImage.setWidth(800);
        newImage.setHeight(600);
        return imageRepository.save(newImage);
    }

    private Tag createTag(String name, TagType type, int usageCount) {
        Tag tag = new Tag();
        tag.setTagName(name);
        tag.setTagType(type);
        tag.setUsageCount(usageCount);
        tag.setCreatedTime(LocalDateTime.now().minusDays(1));
        return tagRepository.save(tag);
    }

    private void linkTag(Image image, Tag tag) {
        ImageTag imageTag = new ImageTag();
        imageTag.setImage(image);
        imageTag.setTag(tag);
        imageTag.setConfidence(BigDecimal.ONE);
        imageTagRepository.save(imageTag);
    }
}
