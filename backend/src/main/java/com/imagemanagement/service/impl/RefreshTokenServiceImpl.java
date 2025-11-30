package com.imagemanagement.service.impl;

import com.imagemanagement.entity.RefreshToken;
import com.imagemanagement.entity.User;
import com.imagemanagement.exception.BadRequestException;
import com.imagemanagement.exception.UnauthorizedException;
import com.imagemanagement.repository.RefreshTokenRepository;
import com.imagemanagement.repository.UserRepository;
import com.imagemanagement.security.jwt.JwtTokenProvider;
import com.imagemanagement.security.token.RefreshTokenRegistry;
import com.imagemanagement.security.token.RefreshTokenState;
import com.imagemanagement.service.RefreshTokenService;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefreshTokenServiceImpl.class);
    private static final int TOKEN_BYTE_LENGTH = 64;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRegistry refreshTokenRegistry;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenServiceImpl(RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository, JwtTokenProvider jwtTokenProvider,
            RefreshTokenRegistry refreshTokenRegistry) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenRegistry = refreshTokenRegistry;
    }

    @Override
    public RefreshToken issueToken(Long userId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(generateTokenValue());
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(Instant.now().plusMillis(jwtTokenProvider.getRefreshExpirationMillis()));
        refreshToken.setRevoked(false);
        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        refreshTokenRegistry.register(saved.getToken(), userId, saved.getExpiresAt());
        return saved;
    }

    @Override
    public RefreshToken refreshToken(String tokenValue) {
        RefreshToken existing = Objects.requireNonNull(validateActiveToken(tokenValue));
        markTokenRevoked(existing);
        refreshTokenRepository.save(existing);
        refreshTokenRegistry.markRevoked(existing.getToken());
        return issueToken(existing.getUser().getId());
    }

    @Override
    public void revokeToken(String tokenValue) {
        try {
            RefreshToken token = Objects.requireNonNull(validateActiveToken(tokenValue));
            markTokenRevoked(token);
            refreshTokenRepository.save(Objects.requireNonNull(token));
            refreshTokenRegistry.markRevoked(tokenValue);
        } catch (UnauthorizedException ex) {
            LOGGER.debug("Attempted to revoke invalid refresh token", ex);
        }
    }

    @Override
    public void revokeAllSessions(String tokenValue) {
        RefreshToken token = Objects.requireNonNull(validateActiveToken(tokenValue));
        revokeTokensForUser(token.getUser().getId());
    }

    @Override
    public void revokeTokensForUser(Long userId) {
        if (userId == null) {
            return;
        }
        List<RefreshToken> activeTokens = refreshTokenRepository.findAllByUser_IdAndRevokedFalse(userId);
        if (activeTokens.isEmpty()) {
            refreshTokenRegistry.markRevokedByUser(userId);
            return;
        }
        activeTokens.forEach(this::markTokenRevoked);
        refreshTokenRepository.saveAll(activeTokens);
        refreshTokenRegistry.markRevokedByUser(userId);
    }

    @Override
    public long getRefreshTokenTtlMillis() {
        return jwtTokenProvider.getRefreshExpirationMillis();
    }

    private RefreshToken validateActiveToken(String tokenValue) {
        if (!StringUtils.hasText(tokenValue)) {
            throw new UnauthorizedException("Refresh token is missing");
        }
        Optional<RefreshTokenState> cachedState = refreshTokenRegistry.find(tokenValue);
        if (cachedState.isPresent()) {
            RefreshTokenState state = cachedState.get();
            if (state.isRevoked()) {
                throw new UnauthorizedException("Refresh token has been revoked");
            }
            if (state.isExpired(Instant.now())) {
                refreshTokenRegistry.remove(tokenValue);
                throw new UnauthorizedException("Refresh token has expired");
            }
        }
        RefreshToken refreshToken = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new UnauthorizedException("Refresh token is invalid"));
        if (refreshToken.isRevoked()) {
            throw new UnauthorizedException("Refresh token has been revoked");
        }
        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            refreshTokenRegistry.remove(tokenValue);
            throw new UnauthorizedException("Refresh token has expired");
        }
        return refreshToken;
    }

    private String generateTokenValue() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private void markTokenRevoked(RefreshToken token) {
        token.setRevoked(true);
    }
}