package com.imagemanagement.dto.request;

import jakarta.validation.constraints.NotBlank;

public class RefreshTokenRequest {

    @NotBlank
    private String refreshToken;

    private boolean logoutAllSessions;

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public boolean isLogoutAllSessions() {
        return logoutAllSessions;
    }

    public void setLogoutAllSessions(boolean logoutAllSessions) {
        this.logoutAllSessions = logoutAllSessions;
    }
}