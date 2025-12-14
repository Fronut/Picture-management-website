package com.imagemanagement.dto.request;

import jakarta.validation.constraints.NotBlank;

public class AiChatMessage {

    @NotBlank(message = "role is required")
    private String role;

    @NotBlank(message = "content is required")
    private String content;

    public AiChatMessage() {
    }

    public AiChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
