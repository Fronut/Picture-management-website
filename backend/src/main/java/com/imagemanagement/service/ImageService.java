package com.imagemanagement.service;

import com.imagemanagement.dto.request.ImageSearchRequest;
import com.imagemanagement.dto.response.ImageSummaryResponse;
import com.imagemanagement.dto.response.ImageUploadResponse;
import com.imagemanagement.dto.response.PageResponse;
import com.imagemanagement.entity.enums.ImagePrivacyLevel;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface ImageService {

    List<ImageUploadResponse> uploadImages(Long userId, List<MultipartFile> files, ImagePrivacyLevel privacyLevel, String description);

    PageResponse<ImageSummaryResponse> searchImages(Long userId, ImageSearchRequest request);
}