package com.imagemanagement.service.impl;

import com.imagemanagement.dto.request.AiTagAssignmentRequest;
import com.imagemanagement.dto.request.TagAssignmentRequest;
import com.imagemanagement.dto.response.ImageTagResponse;
import com.imagemanagement.entity.ExifData;
import com.imagemanagement.entity.Image;
import com.imagemanagement.entity.User;
import com.imagemanagement.repository.ImageRepository;
import com.imagemanagement.repository.ImageTagRepository;
import com.imagemanagement.repository.TagRepository;
import com.imagemanagement.repository.UserRepository;
import com.imagemanagement.service.TagService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TagServiceImpl.class)
@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@SuppressWarnings({"DataFlowIssue", "null"})
class TagServiceImplTest {

    @Autowired
    private TagService tagService;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private ImageTagRepository imageTagRepository;

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;
    private Image image;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("secret");
        user = userRepository.save(user);

        image = new Image();
        image.setUser(user);
        image.setOriginalFilename("sunset.jpg");
        image.setStoredFilename("stored-sunset.jpg");
        image.setFilePath("/tmp/stored-sunset.jpg");
        image.setFileSize(1024L);
        image.setMimeType("image/jpeg");
        image.setWidth(1920);
        image.setHeight(1080);
        image = imageRepository.save(image);
    }

    @Test
    void assignCustomTags_shouldCreateTagsAndAssociations() {
        TagAssignmentRequest request = new TagAssignmentRequest(List.of("Travel", " Sunset "));

        List<ImageTagResponse> responses = tagService.assignCustomTags(user.getId(), image.getId(), request);

        assertThat(responses).hasSize(2);
        assertThat(tagRepository.count()).isEqualTo(2);
        assertThat(imageTagRepository.findAll()).hasSize(2);
        assertThat(responses).extracting(ImageTagResponse::tagName).containsExactlyInAnyOrder("Travel", "Sunset");
    }

    @Test
    void removeTag_shouldDetachAssociationAndDecrementUsage() {
        TagAssignmentRequest request = new TagAssignmentRequest(List.of("Portrait"));
        tagService.assignCustomTags(user.getId(), image.getId(), request);

        Long tagId = Objects.requireNonNull(tagRepository.findByTagNameIgnoreCase("Portrait").orElseThrow().getId());

        tagService.removeTag(user.getId(), image.getId(), tagId);

        assertThat(imageTagRepository.findAll()).isEmpty();
        assertThat(tagRepository.findById(tagId)).isPresent().hasValueSatisfying(tag ->
                assertThat(tag.getUsageCount()).isZero());
    }

    @Test
    void assignAiTags_shouldRespectConfidenceAndDeduplicate() {
        AiTagAssignmentRequest.TagSuggestion suggestionA = new AiTagAssignmentRequest.TagSuggestion("Cat", BigDecimal.valueOf(0.42));
        AiTagAssignmentRequest.TagSuggestion suggestionB = new AiTagAssignmentRequest.TagSuggestion("cat", BigDecimal.valueOf(0.91));
        AiTagAssignmentRequest request = new AiTagAssignmentRequest(List.of(suggestionA, suggestionB));

        List<ImageTagResponse> responses = tagService.assignAiTags(user.getId(), image.getId(), request);

        assertThat(responses).hasSize(1);
        ImageTagResponse response = responses.get(0);
        assertThat(response.tagName()).isEqualTo("cat");
        assertThat(response.confidence()).isEqualByComparingTo(BigDecimal.valueOf(0.91).setScale(2));
    }

    @Test
    void applyAutomaticTags_shouldGenerateExifBasedTags() {
        ExifData exif = new ExifData();
        exif.setImage(image);
        exif.setCameraMake("Nikon");
        exif.setCameraModel("Z6 II");
        exif.setTakenTime(LocalDateTime.of(2023, 5, 6, 8, 30));
        exif.setLocationName("Shanghai");
        image.setExifData(exif);
        image.setWidth(4000);
        image.setHeight(3000);
        image = imageRepository.save(image);

        tagService.applyAutomaticTags(image);

        List<ImageTagResponse> responses = tagService.getTagsForImage(image.getId());
        assertThat(responses).isNotEmpty();
        assertThat(responses).extracting(ImageTagResponse::tagName)
                .contains("year:2023", "camera:Nikon", "location:Shanghai", "orientation:landscape");
    }
}
