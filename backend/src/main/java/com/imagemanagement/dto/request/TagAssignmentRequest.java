package com.imagemanagement.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record TagAssignmentRequest(
                @NotEmpty(message = "tags cannot be empty")
                List<@Valid TagInput> tags
) {

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public static TagAssignmentRequest from(
                        @JsonProperty("tags") List<TagInput> structuredTags,
                        @JsonProperty("tagNames") List<String> legacyTagNames) {
                List<TagInput> resolved = resolveTags(structuredTags, legacyTagNames);
                return new TagAssignmentRequest(resolved);
        }

        private static List<TagInput> resolveTags(List<TagInput> structuredTags, List<String> legacyTagNames) {
                if (structuredTags != null && !structuredTags.isEmpty()) {
                        return structuredTags;
                }
                if (legacyTagNames == null || legacyTagNames.isEmpty()) {
                        return structuredTags != null ? structuredTags : Collections.emptyList();
                }
                List<TagInput> converted = new ArrayList<>(legacyTagNames.size());
                for (String legacyName : legacyTagNames) {
                        if (!hasText(legacyName)) {
                                continue;
                        }
                        converted.add(new TagInput(legacyName.trim(), null));
                }
                return converted;
        }

        private static boolean hasText(String value) {
                return value != null && !value.trim().isEmpty();
        }

        public record TagInput(
                        @NotBlank(message = "tag name cannot be blank")
                        @Size(max = 50, message = "tag name too long")
                        String name,

                        @DecimalMin(value = "0.0", inclusive = true, message = "confidence must be >= 0")
                        @DecimalMax(value = "1.0", inclusive = true, message = "confidence must be <= 1")
                        BigDecimal confidence
        ) {
        }
}
