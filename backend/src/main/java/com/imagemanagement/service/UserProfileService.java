package com.imagemanagement.service;

import com.imagemanagement.dto.response.UserResponse;
import com.imagemanagement.entity.User;

public interface UserProfileService {

    UserResponse getUserProfile(Long userId);

    UserResponse cacheProfile(User user);

    void evict(Long userId);
}