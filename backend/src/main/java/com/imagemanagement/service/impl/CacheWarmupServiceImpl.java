package com.imagemanagement.service.impl;

import com.imagemanagement.dto.request.ImageSearchRequest;
import com.imagemanagement.service.CacheWarmupService;
import com.imagemanagement.service.ImageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class CacheWarmupServiceImpl implements CacheWarmupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheWarmupServiceImpl.class);

    private final ImageService imageService;

    public CacheWarmupServiceImpl(ImageService imageService) {
        this.imageService = imageService;
    }

    @Override
    @Async
    public void warmupUserCaches(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            ImageSearchRequest request = new ImageSearchRequest();
            request.setOnlyOwn(Boolean.TRUE);
            request.setPage(0);
            request.setSize(20);
            imageService.searchImages(userId, request);
        } catch (Exception ex) {
            LOGGER.warn("Failed to warm up caches for user {}", userId, ex);
        }
    }
}