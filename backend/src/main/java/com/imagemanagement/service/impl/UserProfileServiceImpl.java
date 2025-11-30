package com.imagemanagement.service.impl;

import com.imagemanagement.cache.CacheNames;
import com.imagemanagement.dto.response.UserResponse;
import com.imagemanagement.entity.User;
import com.imagemanagement.exception.BadRequestException;
import com.imagemanagement.repository.UserRepository;
import com.imagemanagement.service.UserProfileService;
import java.util.Objects;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@CacheConfig(cacheNames = CacheNames.USERS)
public class UserProfileServiceImpl implements UserProfileService {

    private final UserRepository userRepository;

    public UserProfileServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(key = "T(com.imagemanagement.cache.CacheKeyGenerator).userKey(#userId)")
    public UserResponse getUserProfile(Long userId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));
        return toResponse(user);
    }

    @Override
    @CachePut(key = "T(com.imagemanagement.cache.CacheKeyGenerator).userKey(#user.id)")
    public UserResponse cacheProfile(User user) {
        Objects.requireNonNull(user, "user cannot be null");
        Objects.requireNonNull(user.getId(), "user.id cannot be null");
        return toResponse(user);
    }

    @Override
    @CacheEvict(key = "T(com.imagemanagement.cache.CacheKeyGenerator).userKey(#userId)")
    public void evict(Long userId) {
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getAvatarUrl(), user.getRole());
    }
}