package com.imagemanagement.security.token;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRegistry {

    void register(String tokenValue, Long userId, Instant expiresAt);

    void markRevoked(String tokenValue);

    void markRevokedByUser(Long userId);

    Optional<RefreshTokenState> find(String tokenValue);

    void remove(String tokenValue);
}
