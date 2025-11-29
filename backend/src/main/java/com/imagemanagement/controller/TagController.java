package com.imagemanagement.controller;

import com.imagemanagement.dto.request.AiTagAssignmentRequest;
import com.imagemanagement.dto.request.TagAssignmentRequest;
import com.imagemanagement.dto.response.ApiResponse;
import com.imagemanagement.dto.response.ImageTagResponse;
import com.imagemanagement.dto.response.TagResponse;
import com.imagemanagement.exception.BadRequestException;
import com.imagemanagement.security.CustomUserDetails;
import com.imagemanagement.service.TagService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping("/images/{imageId}/tags")
    public ResponseEntity<ApiResponse<List<ImageTagResponse>>> getTags(@PathVariable Long imageId) {
        return ResponseEntity.ok(ApiResponse.success(tagService.getTagsForImage(imageId)));
    }

    @PostMapping("/images/{imageId}/tags/custom")
    public ResponseEntity<ApiResponse<List<ImageTagResponse>>> addCustomTags(
            @PathVariable Long imageId,
            @Valid @RequestBody TagAssignmentRequest request,
            Authentication authentication) {
        CustomUserDetails principal = requirePrincipal(authentication);
        List<ImageTagResponse> responses = tagService.assignCustomTags(principal.getId(), imageId, request);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @PostMapping("/images/{imageId}/tags/ai")
    public ResponseEntity<ApiResponse<List<ImageTagResponse>>> addAiTags(
            @PathVariable Long imageId,
            @Valid @RequestBody AiTagAssignmentRequest request,
            Authentication authentication) {
        CustomUserDetails principal = requirePrincipal(authentication);
        List<ImageTagResponse> responses = tagService.assignAiTags(principal.getId(), imageId, request);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @DeleteMapping("/images/{imageId}/tags/{tagId}")
    public ResponseEntity<ApiResponse<Void>> deleteTag(
            @PathVariable Long imageId,
            @PathVariable Long tagId,
            Authentication authentication) {
        CustomUserDetails principal = requirePrincipal(authentication);
        tagService.removeTag(principal.getId(), imageId, tagId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/tags/popular")
    public ResponseEntity<ApiResponse<List<TagResponse>>> getPopularTags(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.success(tagService.getPopularTags(limit)));
    }

    private CustomUserDetails requirePrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails principal)) {
            throw new BadRequestException("Authentication required");
        }
        return principal;
    }
}
