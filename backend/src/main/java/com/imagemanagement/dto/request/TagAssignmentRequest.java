package com.imagemanagement.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record TagAssignmentRequest(
        @NotEmpty(message = "tags cannot be empty")
        List<@Valid TagInput> tags
) {

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
