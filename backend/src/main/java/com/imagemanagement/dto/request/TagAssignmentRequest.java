package com.imagemanagement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TagAssignmentRequest(
        @NotEmpty(message = "tagNames cannot be empty")
        List<@NotBlank(message = "tag name cannot be blank") @Size(max = 50, message = "tag name too long") String> tagNames
) {
}
