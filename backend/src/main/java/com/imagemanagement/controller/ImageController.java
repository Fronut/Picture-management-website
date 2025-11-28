package com.imagemanagement.controller;

import com.imagemanagement.dto.response.ApiResponse;
import com.imagemanagement.dto.response.ImageUploadResponse;
import com.imagemanagement.entity.enums.ImagePrivacyLevel;
import com.imagemanagement.security.CustomUserDetails;
import com.imagemanagement.service.ImageService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
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
}