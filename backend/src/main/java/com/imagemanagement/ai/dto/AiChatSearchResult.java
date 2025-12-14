package com.imagemanagement.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.imagemanagement.dto.response.ImageSummaryResponse;
import com.imagemanagement.dto.response.PageResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AiChatSearchResult {

    private String message;
    private AiChatPrimaryResult primaryResult;
    private List<AiChatPrimaryResult> results = new ArrayList<>();
    private List<AiToolCall> toolCalls = new ArrayList<>();

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public AiChatPrimaryResult getPrimaryResult() {
        return primaryResult;
    }

    public void setPrimaryResult(AiChatPrimaryResult primaryResult) {
        this.primaryResult = primaryResult;
    }

    public List<AiChatPrimaryResult> getResults() {
        return results;
    }

    public void setResults(List<AiChatPrimaryResult> results) {
        this.results = results != null ? results : new ArrayList<>();
    }

    public List<AiToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<AiToolCall> toolCalls) {
        this.toolCalls = toolCalls != null ? toolCalls : new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AiChatPrimaryResult {

        private String summary;
        private String query;
        private Integer requestedLimit;
        private Boolean onlyOwn;
        private AiSearchInterpretation interpretation;
        private Map<String, Object> searchPayload = new LinkedHashMap<>();
        private PageResponse<ImageSummaryResponse> page;
        private List<ImageSummaryResponse> matches = new ArrayList<>();

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public Integer getRequestedLimit() {
            return requestedLimit;
        }

        public void setRequestedLimit(Integer requestedLimit) {
            this.requestedLimit = requestedLimit;
        }

        public Boolean getOnlyOwn() {
            return onlyOwn;
        }

        public void setOnlyOwn(Boolean onlyOwn) {
            this.onlyOwn = onlyOwn;
        }

        public AiSearchInterpretation getInterpretation() {
            return interpretation;
        }

        public void setInterpretation(AiSearchInterpretation interpretation) {
            this.interpretation = interpretation;
        }

        public Map<String, Object> getSearchPayload() {
            return searchPayload;
        }

        public void setSearchPayload(Map<String, Object> searchPayload) {
            this.searchPayload = searchPayload != null ? searchPayload : new LinkedHashMap<>();
        }

        public PageResponse<ImageSummaryResponse> getPage() {
            return page;
        }

        public void setPage(PageResponse<ImageSummaryResponse> page) {
            this.page = page;
        }

        public List<ImageSummaryResponse> getMatches() {
            return matches;
        }

        public void setMatches(List<ImageSummaryResponse> matches) {
            this.matches = matches != null ? matches : new ArrayList<>();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AiSearchInterpretation {

        private String query;
        private List<String> keywords = new ArrayList<>();
        private List<String> tags = new ArrayList<>();
        private Map<String, Object> filters = new LinkedHashMap<>();
        private List<Map<String, Object>> explanations = new ArrayList<>();
        private Double confidence;

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public List<String> getKeywords() {
            return keywords;
        }

        public void setKeywords(List<String> keywords) {
            this.keywords = keywords != null ? keywords : new ArrayList<>();
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags != null ? tags : new ArrayList<>();
        }

        public Map<String, Object> getFilters() {
            return filters;
        }

        public void setFilters(Map<String, Object> filters) {
            this.filters = filters != null ? filters : new LinkedHashMap<>();
        }

        public List<Map<String, Object>> getExplanations() {
            return explanations;
        }

        public void setExplanations(List<Map<String, Object>> explanations) {
            this.explanations = explanations != null ? explanations : new ArrayList<>();
        }

        public Double getConfidence() {
            return confidence;
        }

        public void setConfidence(Double confidence) {
            this.confidence = confidence;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AiToolCall {

        private String id;
        private String type;
        private AiToolFunction function;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public AiToolFunction getFunction() {
            return function;
        }

        public void setFunction(AiToolFunction function) {
            this.function = function;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AiToolFunction {

        private String name;
        private String arguments;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getArguments() {
            return arguments;
        }

        public void setArguments(String arguments) {
            this.arguments = arguments;
        }
    }
}
