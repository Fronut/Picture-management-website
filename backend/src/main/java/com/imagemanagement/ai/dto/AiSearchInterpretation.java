package com.imagemanagement.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiSearchInterpretation(
        String query,
        List<String> keywords,
        List<String> tags,
        Map<String, Object> filters,
        List<Map<String, Object>> explanations,
        BigDecimal confidence) {
}
