package com.imagemanagement.controller;

import com.imagemanagement.security.CustomUserDetails;
import com.imagemanagement.service.ImageContentService;
import com.imagemanagement.service.ImageContentService.ContentResource;
import java.util.Objects;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/images")
public class ImageContentController {

    private final ImageContentService imageContentService;

    public ImageContentController(ImageContentService imageContentService) {
        this.imageContentService = imageContentService;
    }

    @GetMapping("/{imageId}/content")
    public ResponseEntity<Resource> getOriginalImage(@PathVariable Long imageId, Authentication authentication) {
        Long userId = extractUserId(authentication);
        ContentResource content = imageContentService.loadOriginal(imageId, userId);
        return ResponseEntity.ok()
            .contentType(Objects.requireNonNull(content.mediaType()))
                .contentLength(content.contentLength())
                .body(content.resource());
    }

    @GetMapping("/{imageId}/thumbnails/{thumbnailId}")
    public ResponseEntity<Resource> getThumbnail(@PathVariable Long imageId,
                                                 @PathVariable Long thumbnailId,
                                                 Authentication authentication) {
        Long userId = extractUserId(authentication);
        ContentResource content = imageContentService.loadThumbnail(imageId, thumbnailId, userId);
        return ResponseEntity.ok()
            .contentType(Objects.requireNonNull(content.mediaType()))
                .contentLength(content.contentLength())
                .body(content.resource());
    }

    private Long extractUserId(Authentication authentication) {
        Object principal = authentication != null ? authentication.getPrincipal() : null;
        if (principal instanceof CustomUserDetails customUserDetails) {
            return customUserDetails.getId();
        }
        return null;
    }
}