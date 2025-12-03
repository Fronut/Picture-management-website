package com.imagemanagement.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiTagSuggestionResponse(List<AiTagSuggestion> tags, Map<String, Object> metadata) {
}
