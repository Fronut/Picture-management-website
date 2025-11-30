package com.imagemanagement.service.impl;

import com.imagemanagement.entity.RefreshToken;
import com.imagemanagement.entity.User;
import com.imagemanagement.exception.BadRequestException;
import com.imagemanagement.exception.UnauthorizedException;
import com.imagemanagement.repository.RefreshTokenRepository;
import com.imagemanagement.repository.UserRepository;
import com.imagemanagement.security.jwt.JwtTokenProvider;
import com.imagemanagement.service.RefreshTokenService;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefreshTokenServiceImpl.class);
    private static final int TOKEN_BYTE_LENGTH = 64;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenServiceImpl(RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository, JwtTokenProvider jwtTokenProvider) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public RefreshToken issueToken(Long userId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        revokeTokensForUser(userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(generateTokenValue());
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(Instant.now().plusMillis(jwtTokenProvider.getRefreshExpirationMillis()));
        refreshToken.setRevoked(false);
        return refreshTokenRepository.save(refreshToken);
    }

    @Override
    public RefreshToken refreshToken(String tokenValue) {
        RefreshToken existing = validateActiveToken(tokenValue);
        return issueToken(existing.getUser().getId());
    }

    @Override
    public void revokeToken(String tokenValue) {
        try {
            RefreshToken token = validateActiveToken(tokenValue);
            refreshTokenRepository.delete(Objects.requireNonNull(token));
        } catch (UnauthorizedException ex) {
            LOGGER.debug("Attempted to revoke invalid refresh token", ex);
        }
    }

    @Override
    public void revokeTokensForUser(Long userId) {
        if (userId == null) {
            return;
        }
        refreshTokenRepository.deleteByUser_Id(userId);
    }

    @Override
    public long getRefreshTokenTtlMillis() {
        return jwtTokenProvider.getRefreshExpirationMillis();
    }

    private RefreshToken validateActiveToken(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            throw new UnauthorizedException("Refresh token is missing");
        }
        RefreshToken refreshToken = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new UnauthorizedException("Refresh token is invalid"));
        if (refreshToken.isRevoked()) {
            throw new UnauthorizedException("Refresh token has been revoked");
        }
        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new UnauthorizedException("Refresh token has expired");
        }
        return refreshToken;
    }

    private String generateTokenValue() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}