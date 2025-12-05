package com.imagemanagement.controller;

import com.imagemanagement.dto.request.ImageEditRequest;
import com.imagemanagement.dto.request.ImageSearchRequest;
import com.imagemanagement.dto.response.ApiResponse;
import com.imagemanagement.dto.response.ImageDeleteResponse;
import com.imagemanagement.dto.response.ImageSummaryResponse;
import com.imagemanagement.dto.response.ImageUploadResponse;
import com.imagemanagement.dto.response.PageResponse;
import com.imagemanagement.entity.enums.ImagePrivacyLevel;
import com.imagemanagement.security.CustomUserDetails;
import com.imagemanagement.service.ImageService;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<ImageUploadResponse>>> uploadImages(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(value = "privacyLevel", required = false) ImagePrivacyLevel privacyLevel,
            @RequestParam(value = "description", required = false) String description,
            Authentication authentication) {

        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        List<ImageUploadResponse> responses = imageService.uploadImages(principal.getId(), files, privacyLevel, description);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<PageResponse<ImageSummaryResponse>>> searchImages(
            @Valid @RequestBody ImageSearchRequest request,
            Authentication authentication) {

        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        PageResponse<ImageSummaryResponse> page = imageService.searchImages(principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @PostMapping(value = "/{imageId}/edit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ImageSummaryResponse>> editImage(
            @PathVariable Long imageId,
            @Valid @RequestBody ImageEditRequest request,
            Authentication authentication) {

        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        ImageSummaryResponse response = imageService.editImage(principal.getId(), imageId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<ApiResponse<ImageDeleteResponse>> deleteImage(
            @PathVariable Long imageId,
            Authentication authentication) {
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        ImageDeleteResponse response = imageService.deleteImage(principal.getId(), imageId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/highlights")
    public ResponseEntity<ApiResponse<List<ImageSummaryResponse>>> getHighlights(
            @RequestParam(value = "size", defaultValue = "6") int size,
            Authentication authentication) {
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        List<ImageSummaryResponse> highlights = imageService.getHighlightImages(principal.getId(), size);
        return ResponseEntity.ok(ApiResponse.success(highlights));
    }
}