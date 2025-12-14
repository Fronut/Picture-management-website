package com.imagemanagement.controller;

import com.imagemanagement.ai.AiServiceClient;
import com.imagemanagement.ai.dto.AiChatSearchResult;
import com.imagemanagement.dto.request.AiChatRequest;
import com.imagemanagement.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiServiceClient aiServiceClient;

    public AiChatController(AiServiceClient aiServiceClient) {
        this.aiServiceClient = aiServiceClient;
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AiChatSearchResult>> chat(
            @Valid @RequestBody AiChatRequest request,
            HttpServletRequest httpRequest) {
        String bearer = resolveAuthorizationHeader(httpRequest);
        if (StringUtils.hasText(bearer)) {
            request.setAuthToken(bearer);
        }
        AiChatSearchResult result = aiServiceClient.chatSearch(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private String resolveAuthorizationHeader(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String header = request.getHeader("Authorization");
        return StringUtils.hasText(header) ? header : null;
    }
}
