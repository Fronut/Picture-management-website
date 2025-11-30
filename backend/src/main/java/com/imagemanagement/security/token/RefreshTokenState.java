package com.imagemanagement.security.token;

import java.io.Serializable;
import java.time.Instant;

public class RefreshTokenState implements Serializable {

    private static final long serialVersionUID = 1L;

    private String tokenHash;
    private Long userId;
    private Instant expiresAt;
    private boolean revoked;
    private Instant revokedAt;

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    public void markRevoked(Instant when) {
        this.revoked = true;
        this.revokedAt = when;
    }
}
