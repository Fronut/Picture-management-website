package com.imagemanagement.dto.response;

import com.imagemanagement.entity.enums.UserRole;

public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private String avatarUrl;
    private UserRole role;

    public UserResponse() {
    }

    public UserResponse(Long id, String username, String email, String avatarUrl, UserRole role) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.avatarUrl = avatarUrl;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }
}
