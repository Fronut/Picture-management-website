package com.imagemanagement.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Objects;

public record AiTagGenerationRequest(
        @Size(max = 20, message = "hints cannot contain more than 20 entries")
        List<@NotBlank(message = "hint cannot be blank")
                @Size(max = 50, message = "hint cannot exceed 50 characters")
                String> hints,

        @Min(value = 1, message = "limit must be at least 1")
        @Max(value = 50, message = "limit must be at most 50")
        Integer limit
) {

    public AiTagGenerationRequest {
        if (hints != null) {
            hints = hints.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(entry -> !entry.isEmpty())
                    .toList();
            if (hints.isEmpty()) {
                hints = List.of();
            } else {
                hints = List.copyOf(hints);
            }
        }
    }
}
