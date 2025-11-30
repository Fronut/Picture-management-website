package com.imagemanagement.security.token;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("test")
public class InMemoryRefreshTokenRegistry implements RefreshTokenRegistry {

    private final ConcurrentMap<String, RefreshTokenState> stateStore = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Set<String>> userIndex = new ConcurrentHashMap<>();

    @Override
    public void register(String tokenValue, Long userId, Instant expiresAt) {
        if (!StringUtils.hasText(tokenValue) || userId == null || expiresAt == null) {
            return;
        }
        String tokenHash = TokenHashUtils.sha256(tokenValue);
        RefreshTokenState state = new RefreshTokenState();
        state.setTokenHash(tokenHash);
        state.setUserId(userId);
        state.setExpiresAt(expiresAt);
        stateStore.put(tokenHash, state);
        userIndex.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(tokenHash);
    }

    @Override
    public void markRevoked(String tokenValue) {
        if (!StringUtils.hasText(tokenValue)) {
            return;
        }
        String tokenHash = TokenHashUtils.sha256(tokenValue);
        RefreshTokenState state = stateStore.get(tokenHash);
        if (state != null && !state.isRevoked()) {
            state.markRevoked(Instant.now());
            removeFromIndex(state.getUserId(), tokenHash);
        }
    }

    @Override
    public void markRevokedByUser(Long userId) {
        if (userId == null) {
            return;
        }
        Set<String> hashes = userIndex.getOrDefault(userId, Collections.emptySet());
        Instant now = Instant.now();
        for (String hash : hashes) {
            RefreshTokenState state = stateStore.get(hash);
            if (state != null && !state.isRevoked()) {
                state.markRevoked(now);
            }
        }
        userIndex.remove(userId);
    }

    @Override
    public Optional<RefreshTokenState> find(String tokenValue) {
        if (!StringUtils.hasText(tokenValue)) {
            return Optional.empty();
        }
        String tokenHash = TokenHashUtils.sha256(tokenValue);
        RefreshTokenState state = stateStore.get(tokenHash);
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
        String tokenHash = TokenHashUtils.sha256(tokenValue);
        RefreshTokenState state = stateStore.remove(tokenHash);
        if (state != null) {
            removeFromIndex(state.getUserId(), tokenHash);
        }
    }

    private void removeFromIndex(Long userId, String tokenHash) {
        if (userId == null) {
            return;
        }
        Set<String> hashes = userIndex.get(userId);
        if (hashes != null) {
            hashes.remove(tokenHash);
            if (hashes.isEmpty()) {
                userIndex.remove(userId);
            }
        }
    }
}
