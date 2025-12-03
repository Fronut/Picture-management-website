package com.imagemanagement.service.impl;

import com.imagemanagement.ai.AiServiceClient;
import com.imagemanagement.ai.dto.AiTagSuggestionResponse;
import com.imagemanagement.dto.request.AiTagAssignmentRequest;
import com.imagemanagement.dto.request.AiTagGenerationRequest;
import com.imagemanagement.dto.request.TagAssignmentRequest;
import com.imagemanagement.dto.response.ImageTagResponse;
import com.imagemanagement.dto.response.TagResponse;
import com.imagemanagement.entity.ExifData;
import com.imagemanagement.entity.Image;
import com.imagemanagement.entity.ImageTag;
import com.imagemanagement.entity.Tag;
import com.imagemanagement.entity.User;
import com.imagemanagement.entity.enums.TagType;
import com.imagemanagement.exception.BadRequestException;
import com.imagemanagement.exception.ResourceNotFoundException;
import com.imagemanagement.repository.ImageRepository;
import com.imagemanagement.repository.ImageTagRepository;
import com.imagemanagement.repository.TagRepository;
import com.imagemanagement.service.TagService;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class TagServiceImpl implements TagService {

    private static final BigDecimal CONFIDENCE_STRONG = BigDecimal.valueOf(1.00).setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal CONFIDENCE_AUTO = BigDecimal.valueOf(0.85).setScale(2, RoundingMode.HALF_UP);

    private final TagRepository tagRepository;
    private final ImageRepository imageRepository;
    private final ImageTagRepository imageTagRepository;
    private final AiServiceClient aiServiceClient;

    public TagServiceImpl(TagRepository tagRepository,
            ImageRepository imageRepository,
            ImageTagRepository imageTagRepository,
            AiServiceClient aiServiceClient) {
        this.tagRepository = tagRepository;
        this.imageRepository = imageRepository;
        this.imageTagRepository = imageTagRepository;
        this.aiServiceClient = aiServiceClient;
    }

    @Override
    public List<ImageTagResponse> getTagsForImage(Long imageId) {
        Objects.requireNonNull(imageId, "imageId cannot be null");
        ensureImageExists(imageId);
        List<ImageTag> tags = imageTagRepository.findAllByImageId(imageId);
        return tags.stream()
                .map(this::toImageTagResponse)
                .toList();
    }

    @Override
    public List<ImageTagResponse> assignCustomTags(Long userId, Long imageId, TagAssignmentRequest request) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(imageId, "imageId cannot be null");
        Image image = loadOwnedImage(userId, imageId);
        List<TagCandidate> candidates = toCandidates(request.tagNames(), TagType.CUSTOM, CONFIDENCE_STRONG);
        attachCandidates(image, candidates);
        return getTagsForImage(imageId);
    }

    @Override
    public List<ImageTagResponse> assignAiTags(Long userId, Long imageId, AiTagAssignmentRequest request) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(imageId, "imageId cannot be null");
        Image image = loadOwnedImage(userId, imageId);
        if (CollectionUtils.isEmpty(request.tags())) {
            throw new BadRequestException("tags cannot be empty");
        }

        List<TagCandidate> candidates = request.tags().stream()
                .map(tag -> new TagCandidate(tag.name(), TagType.AI, normalizeConfidence(tag.confidence(), BigDecimal.valueOf(0.75))))
                .toList();
        attachCandidates(image, candidates);
        return getTagsForImage(imageId);
    }

    @Override
    public List<ImageTagResponse> generateAiTags(Long userId, Long imageId, AiTagGenerationRequest request) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(imageId, "imageId cannot be null");
        Image image = loadOwnedImage(userId, imageId);
        byte[] payload = resolveImageBytes(image);

        List<String> hints = request != null ? sanitizeHints(request.hints()) : Collections.emptyList();
        Integer limit = request != null ? request.limit() : null;

        AiTagSuggestionResponse response = aiServiceClient.suggestTags(
                payload,
                image.getOriginalFilename(),
                hints.isEmpty() ? null : hints,
                limit);

        if (response == null || CollectionUtils.isEmpty(response.tags())) {
            throw new BadRequestException("AI service returned no tag suggestions");
        }

        List<AiTagAssignmentRequest.TagSuggestion> suggestions = response.tags().stream()
                .filter(suggestion -> StringUtils.hasText(suggestion.name()))
                .map(suggestion -> new AiTagAssignmentRequest.TagSuggestion(
                        suggestion.name(),
                        BigDecimal.valueOf(suggestion.confidence())))
                .toList();

        if (suggestions.isEmpty()) {
            throw new BadRequestException("AI service returned no valid tag names");
        }

        return assignAiTags(userId, imageId, new AiTagAssignmentRequest(suggestions));
    }

    @Override
    public void removeTag(Long userId, Long imageId, Long tagId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(imageId, "imageId cannot be null");
        Objects.requireNonNull(tagId, "tagId cannot be null");
        Image image = loadOwnedImage(userId, imageId);
        ImageTag imageTag = imageTagRepository.findByImageIdAndTagId(image.getId(), tagId)
                .orElseThrow(() -> new BadRequestException("Tag is not attached to image"));
        imageTagRepository.delete(Objects.requireNonNull(imageTag));
        Tag tag = imageTag.getTag();
        decrementUsage(tag);
    }

    @Override
    public List<TagResponse> getPopularTags(int limit) {
        int resolvedLimit = limit <= 0 ? 10 : Math.min(limit, 100);
        return tagRepository.findTopTags(PageRequest.of(0, resolvedLimit)).stream()
                .map(tag -> new TagResponse(tag.getId(), tag.getTagName(), tag.getTagType(), tag.getUsageCount()))
                .toList();
    }

    @Override
    public void applyAutomaticTags(Image image) {
        if (image == null || image.getId() == null) {
            return;
        }

        List<TagCandidate> candidates = buildAutoCandidates(image);
        attachCandidates(image, candidates);
    }

    private List<TagCandidate> buildAutoCandidates(Image image) {
        List<TagCandidate> candidates = new ArrayList<>();
        ExifData exif = image.getExifData();
        if (exif != null) {
            LocalDateTime taken = exif.getTakenTime();
            if (taken != null) {
                candidates.add(new TagCandidate("year:" + taken.getYear(), TagType.AUTO, CONFIDENCE_AUTO));
                candidates.add(new TagCandidate("month:" + String.format(Locale.ROOT, "%02d", taken.getMonthValue()), TagType.AUTO, CONFIDENCE_AUTO));
                candidates.add(new TagCandidate("season:" + detectSeason(taken.getMonthValue()), TagType.AUTO, CONFIDENCE_AUTO));
                candidates.add(new TagCandidate("daypart:" + detectDaypart(taken.getHour()), TagType.AUTO, CONFIDENCE_AUTO));
            }

            if (StringUtils.hasText(exif.getCameraMake())) {
                candidates.add(new TagCandidate("camera:" + normalizeWord(exif.getCameraMake()), TagType.AUTO, CONFIDENCE_AUTO));
            }
            if (StringUtils.hasText(exif.getCameraModel())) {
                candidates.add(new TagCandidate("camera-model:" + normalizeWord(exif.getCameraModel()), TagType.AUTO, CONFIDENCE_AUTO));
            }
            if (StringUtils.hasText(exif.getLocationName())) {
                candidates.add(new TagCandidate("location:" + normalizeWord(exif.getLocationName()), TagType.AUTO, CONFIDENCE_AUTO));
            }
        }

        if (image.getWidth() != null && image.getHeight() != null) {
            String orientation = image.getWidth() >= image.getHeight() ? "landscape" : "portrait";
            candidates.add(new TagCandidate("orientation:" + orientation, TagType.AUTO, CONFIDENCE_AUTO));
        }
        if (StringUtils.hasText(image.getMimeType())) {
            candidates.add(new TagCandidate("format:" + image.getMimeType().toLowerCase(Locale.ROOT), TagType.AUTO, CONFIDENCE_AUTO));
        }

        return candidates;
    }

    private byte[] resolveImageBytes(Image image) {
        if (image == null || !StringUtils.hasText(image.getFilePath())) {
            throw new ResourceNotFoundException("Image binary data is not available");
        }
        Path path = Paths.get(image.getFilePath());
        try {
            if (!Files.exists(path)) {
                throw new ResourceNotFoundException("Image binary data is not available");
            }
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read stored image from disk", ex);
        }
    }

    private List<String> sanitizeHints(List<String> hints) {
        if (CollectionUtils.isEmpty(hints)) {
            return Collections.emptyList();
        }
        return hints.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
    }

    private String detectSeason(int month) {
        return switch ((month - 1) / 3) {
            case 0 -> "spring";
            case 1 -> "summer";
            case 2 -> "autumn";
            default -> "winter";
        };
    }

    private String detectDaypart(int hour) {
        if (hour >= 5 && hour < 11) {
            return "morning";
        } else if (hour >= 11 && hour < 17) {
            return "afternoon";
        } else if (hour >= 17 && hour < 21) {
            return "evening";
        }
        return "night";
    }

    private void attachCandidates(Image image, Collection<TagCandidate> rawCandidates) {
        if (CollectionUtils.isEmpty(rawCandidates)) {
            return;
        }

        List<TagCandidate> candidates = rawCandidates.stream()
                .filter(candidate -> StringUtils.hasText(candidate.tagName()))
                .map(candidate -> new TagCandidate(normalizeTagName(candidate.tagName()), candidate.tagType(), candidate.confidence()))
                .filter(candidate -> StringUtils.hasText(candidate.tagName()))
                .collect(Collectors.collectingAndThen(Collectors.toMap(
                        candidate -> candidate.tagName().toLowerCase(Locale.ROOT),
                        Function.identity(),
                        this::preferHigherConfidence
                ), map -> new ArrayList<>(map.values())));

        if (candidates.isEmpty()) {
            return;
        }

        List<ImageTag> newLinks = new ArrayList<>();
        for (TagCandidate candidate : candidates) {
            Tag tag = resolveTag(candidate);
            if (imageTagRepository.existsByImage_IdAndTag_Id(image.getId(), tag.getId())) {
                continue;
            }
            ImageTag imageTag = new ImageTag();
            imageTag.setImage(image);
            imageTag.setTag(tag);
            imageTag.setConfidence(candidate.confidence());
            newLinks.add(imageTag);
            incrementUsage(tag);
        }
        if (!newLinks.isEmpty()) {
            imageTagRepository.saveAll(newLinks);
        }
    }

    private TagCandidate preferHigherConfidence(TagCandidate left, TagCandidate right) {
        return left.confidence().compareTo(right.confidence()) >= 0 ? left : right;
    }

    private Tag resolveTag(TagCandidate candidate) {
        return tagRepository.findByTagNameIgnoreCase(candidate.tagName())
                .orElseGet(() -> {
                    Tag tag = new Tag();
                    tag.setTagName(candidate.tagName());
                    tag.setTagType(candidate.tagType());
                    tag.setUsageCount(0);
                    return tagRepository.save(tag);
                });
    }

    private void incrementUsage(Tag tag) {
        tag.setUsageCount(tag.getUsageCount() + 1);
        tagRepository.save(tag);
    }

    private void decrementUsage(Tag tag) {
        int next = Math.max(0, tag.getUsageCount() - 1);
        tag.setUsageCount(next);
        tagRepository.save(tag);
    }

    private List<TagCandidate> toCandidates(List<String> tagNames, TagType tagType, BigDecimal confidence) {
        if (CollectionUtils.isEmpty(tagNames)) {
            throw new BadRequestException("tagNames cannot be empty");
        }
        return tagNames.stream()
                .filter(StringUtils::hasText)
                .map(name -> new TagCandidate(name, tagType, confidence))
                .toList();
    }

    private BigDecimal normalizeConfidence(BigDecimal value, BigDecimal fallback) {
        BigDecimal reference = value != null ? value : fallback;
        if (reference.compareTo(BigDecimal.ZERO) < 0) {
            return fallback;
        }
        if (reference.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP);
        }
        return reference.setScale(2, RoundingMode.HALF_UP);
    }

    private Image loadOwnedImage(Long userId, Long imageId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(imageId, "imageId cannot be null");
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new BadRequestException("Image not found"));
        User owner = image.getUser();
        if (owner == null || !Objects.equals(owner.getId(), userId)) {
            throw new BadRequestException("You do not have permission to modify this image");
        }
        return image;
    }

    private void ensureImageExists(Long imageId) {
        Objects.requireNonNull(imageId, "imageId cannot be null");
        if (!imageRepository.existsById(imageId)) {
            throw new BadRequestException("Image not found");
        }
    }

    private ImageTagResponse toImageTagResponse(ImageTag imageTag) {
        Tag tag = imageTag.getTag();
        return new ImageTagResponse(
                tag.getId(),
                tag.getTagName(),
                tag.getTagType(),
                tag.getUsageCount(),
                imageTag.getConfidence()
        );
    }

    private String normalizeTagName(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (!StringUtils.hasText(trimmed)) {
            return null;
        }
        return trimmed.replaceAll("\\s+", " ");
    }

    private String normalizeWord(String raw) {
        return raw.trim().replaceAll("\\s+", " ");
    }

    private record TagCandidate(String tagName, TagType tagType, BigDecimal confidence) {
    }
}
