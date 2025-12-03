package com.imagemanagement.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiSearchInterpretRequest(
        @NotBlank(message = "query cannot be blank")
        @Size(max = 200, message = "query is too long")
        String query,

        @Min(value = 1, message = "limit must be at least 1")
        @Max(value = 50, message = "limit must be at most 50")
        Integer limit
) {
}
