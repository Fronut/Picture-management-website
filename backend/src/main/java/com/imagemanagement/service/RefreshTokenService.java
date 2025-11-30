package com.imagemanagement.service;

import com.imagemanagement.entity.RefreshToken;

public interface RefreshTokenService {

    RefreshToken issueToken(Long userId);

    RefreshToken refreshToken(String tokenValue);

    void revokeToken(String tokenValue);

    void revokeAllSessions(String tokenValue);

    void revokeTokensForUser(Long userId);

    long getRefreshTokenTtlMillis();
}