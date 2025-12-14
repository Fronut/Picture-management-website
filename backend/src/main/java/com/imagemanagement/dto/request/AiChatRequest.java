package com.imagemanagement.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

public class AiChatRequest {

    @NotBlank(message = "query is required")
    private String query;

    @Min(value = 1, message = "limit must be at least 1")
    @Max(value = 20, message = "limit must be at most 20")
    private Integer limit;

    private Boolean onlyOwn;

    @Valid
    private List<AiChatMessage> messages = new ArrayList<>();

    /**
     * Bearer token propagated from the caller so downstream AI services can impersonate the user
     * when querying internal APIs. This field is set by the backend and is never required from clients.
     */
    private String authToken;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Boolean getOnlyOwn() {
        return onlyOwn;
    }

    public void setOnlyOwn(Boolean onlyOwn) {
        this.onlyOwn = onlyOwn;
    }

    public List<AiChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<AiChatMessage> messages) {
        this.messages = messages != null ? messages : new ArrayList<>();
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    /**
     * Create a defensive copy with defaults and sane bounds applied.
     */
    public AiChatRequest normalized() {
        AiChatRequest copy = new AiChatRequest();
        copy.setQuery(this.query != null ? this.query.trim() : "");
        int resolvedLimit = this.limit == null ? 12 : Math.max(1, Math.min(20, this.limit));
        copy.setLimit(resolvedLimit);
        copy.setOnlyOwn(this.onlyOwn);
        copy.setMessages(this.messages);
        copy.setAuthToken(this.authToken);
        return copy;
    }
}
