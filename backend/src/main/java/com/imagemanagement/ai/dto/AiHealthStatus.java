package com.imagemanagement.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiHealthStatus(String service, String version, String status, String timestamp, String python) {
}
