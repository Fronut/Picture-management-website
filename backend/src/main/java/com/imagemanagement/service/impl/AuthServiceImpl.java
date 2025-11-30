package com.imagemanagement.service.impl;

import com.imagemanagement.dto.request.LoginRequest;
import com.imagemanagement.dto.request.RefreshTokenRequest;
import com.imagemanagement.dto.request.RegisterRequest;
import com.imagemanagement.dto.response.AuthResponse;
import com.imagemanagement.dto.response.UserResponse;
import com.imagemanagement.entity.User;
import com.imagemanagement.entity.enums.UserRole;
import com.imagemanagement.exception.BadRequestException;
import com.imagemanagement.repository.UserRepository;
import com.imagemanagement.security.CustomUserDetails;
import com.imagemanagement.security.jwt.JwtTokenProvider;
import com.imagemanagement.service.AuthService;
import com.imagemanagement.service.CacheWarmupService;
import com.imagemanagement.service.RefreshTokenService;
import com.imagemanagement.service.UserProfileService;
import java.util.Objects;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserProfileService userProfileService;
    private final CacheWarmupService cacheWarmupService;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider,
            RefreshTokenService refreshTokenService, UserProfileService userProfileService,
            CacheWarmupService cacheWarmupService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.userProfileService = userProfileService;
        this.cacheWarmupService = cacheWarmupService;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.getUsername())) {
            throw new BadRequestException("Username already exists");
        }
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new BadRequestException("Email already registered");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(UserRole.USER);

        User saved = userRepository.save(user);

        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String token = jwtTokenProvider.generateToken(authentication);

        var refreshToken = refreshTokenService.issueToken(saved.getId());
        var userResponse = userProfileService.cacheProfile(saved);
        cacheWarmupService.warmupUserCaches(saved.getId());
        return buildAuthResponse(token, refreshToken.getToken(), userResponse);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsernameOrEmail(), request.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        var principal = (CustomUserDetails) authentication.getPrincipal();
        Long principalId = Objects.requireNonNull(principal.getId(), "User id must not be null");
        User user = userRepository.findById(principalId).orElseThrow();
        String token = jwtTokenProvider.generateToken(authentication);
        var refreshToken = refreshTokenService.issueToken(user.getId());
        var userResponse = userProfileService.cacheProfile(user);
        cacheWarmupService.warmupUserCaches(user.getId());
        return buildAuthResponse(token, refreshToken.getToken(), userResponse);
    }

    @Override
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        var nextRefreshToken = refreshTokenService.refreshToken(request.getRefreshToken());
        User user = nextRefreshToken.getUser();
        String accessToken = jwtTokenProvider.generateToken(user.getId());
        var userResponse = userProfileService.cacheProfile(user);
        cacheWarmupService.warmupUserCaches(user.getId());
        return buildAuthResponse(accessToken, nextRefreshToken.getToken(), userResponse);
    }

    @Override
    public void logout(RefreshTokenRequest request) {
        if (request.isLogoutAllSessions()) {
            refreshTokenService.revokeAllSessions(request.getRefreshToken());
        } else {
            refreshTokenService.revokeToken(request.getRefreshToken());
        }
    }

    private AuthResponse buildAuthResponse(String accessToken, String refreshToken, UserResponse userResponse) {
        AuthResponse response = new AuthResponse();
        response.setToken(accessToken);
        response.setExpiresIn(jwtTokenProvider.getExpirationMillis());
        response.setRefreshToken(refreshToken);
        response.setRefreshExpiresIn(refreshTokenService.getRefreshTokenTtlMillis());
        response.setUser(userResponse);
        return response;
    }
}
