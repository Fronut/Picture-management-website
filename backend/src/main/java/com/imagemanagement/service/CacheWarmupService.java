package com.imagemanagement.service;

public interface CacheWarmupService {

    void warmupUserCaches(Long userId);
}