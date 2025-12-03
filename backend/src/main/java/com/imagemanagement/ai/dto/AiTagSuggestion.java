package com.imagemanagement.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiTagSuggestion(String name, double confidence, String source) {
}
