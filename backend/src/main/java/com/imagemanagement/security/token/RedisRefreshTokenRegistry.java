package com.imagemanagement.security.token;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("!test")
public class RedisRefreshTokenRegistry implements RefreshTokenRegistry {

    private static final String TOKEN_PREFIX = "auth:refresh:token:";
    private static final String USER_PREFIX = "auth:refresh:user:";

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisRefreshTokenRegistry(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void register(String tokenValue, Long userId, Instant expiresAt) {
        if (!StringUtils.hasText(tokenValue) || userId == null || expiresAt == null) {
            return;
        }
        String tokenHash = Objects.requireNonNull(TokenHashUtils.sha256(tokenValue), "tokenHash");
        RefreshTokenState state = new RefreshTokenState();
        state.setTokenHash(tokenHash);
        state.setUserId(userId);
        state.setExpiresAt(expiresAt);
        state.setRevoked(false);
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            ttl = Duration.ofSeconds(1);
        }
        redisTemplate.opsForValue().set(
            Objects.requireNonNull(tokenKey(tokenHash)),
            state,
            Objects.requireNonNull(ttl));
        redisTemplate.opsForSet().add(Objects.requireNonNull(userKey(userId)), tokenHash);
    }

    @Override
    public void markRevoked(String tokenValue) {
        if (!StringUtils.hasText(tokenValue)) {
            return;
        }
        String tokenHash = Objects.requireNonNull(TokenHashUtils.sha256(tokenValue), "tokenHash");
        markRevokedByHash(tokenHash);
    }

    @Override
    public void markRevokedByUser(Long userId) {
        if (userId == null) {
            return;
        }
        Set<Object> hashes = redisTemplate.opsForSet().members(Objects.requireNonNull(userKey(userId)));
        if (hashes == null || hashes.isEmpty()) {
            return;
        }
        for (Object hash : hashes) {
            if (hash instanceof String tokenHash) {
                markRevokedByHash(tokenHash);
            }
        }
        redisTemplate.delete(Objects.requireNonNull(userKey(userId)));
    }

    @Override
    public Optional<RefreshTokenState> find(String tokenValue) {
        if (!StringUtils.hasText(tokenValue)) {
            return Optional.empty();
        }
        String tokenHash = Objects.requireNonNull(TokenHashUtils.sha256(tokenValue), "tokenHash");
        RefreshTokenState state = getState(tokenHash);
        if (state == null) {
            return Optional.empty();
        }
        if (state.isExpired(Instant.now())) {
            remove(tokenValue);
            return Optional.empty();
        }
        return Optional.of(state);
    }

    @Override
    public void remove(String tokenValue) {
        if (!StringUtils.hasText(tokenValue)) {
            return;
        }
        String tokenHash = Objects.requireNonNull(TokenHashUtils.sha256(tokenValue), "tokenHash");
        String key = Objects.requireNonNull(tokenKey(tokenHash));
        RefreshTokenState state = getState(tokenHash);
        redisTemplate.delete(key);
        if (state != null && state.getUserId() != null) {
            redisTemplate.opsForSet().remove(Objects.requireNonNull(userKey(state.getUserId())), tokenHash);
        }
    }

    private void markRevokedByHash(String tokenHash) {
        String key = Objects.requireNonNull(tokenKey(tokenHash));
        RefreshTokenState state = getState(tokenHash);
        if (state == null) {
            return;
        }
        if (!state.isRevoked()) {
            state.markRevoked(Instant.now());
            Long ttlSeconds = redisTemplate.getExpire(key);
            if (ttlSeconds == null || ttlSeconds <= 0) {
                redisTemplate.opsForValue().set(key, state);
            } else {
                redisTemplate.opsForValue().set(key, state, Objects.requireNonNull(Duration.ofSeconds(ttlSeconds)));
            }
        }
        if (state.getUserId() != null) {
            redisTemplate.opsForSet().remove(Objects.requireNonNull(userKey(state.getUserId())), tokenHash);
        }
    }

    private RefreshTokenState getState(String tokenHash) {
        Object value = redisTemplate.opsForValue().get(Objects.requireNonNull(tokenKey(tokenHash)));
        if (value instanceof RefreshTokenState state) {
            return state;
        }
        return null;
    }

    private String tokenKey(String tokenHash) {
        return TOKEN_PREFIX + Objects.requireNonNull(tokenHash, "tokenHash");
    }

    private String userKey(Long userId) {
        return USER_PREFIX + Objects.requireNonNull(userId, "userId");
    }
}
