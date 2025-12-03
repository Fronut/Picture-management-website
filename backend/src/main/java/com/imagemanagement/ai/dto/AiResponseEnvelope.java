package com.imagemanagement.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiResponseEnvelope<T>(String status, T data, String message) {

    public boolean isOk() {
        return status != null && "ok".equalsIgnoreCase(status.trim());
    }
}
