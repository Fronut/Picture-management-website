package com.imagemanagement.controller;

import com.imagemanagement.ai.AiServiceClient;
import com.imagemanagement.ai.dto.AiChatSearchResult;
import com.imagemanagement.dto.request.AiChatRequest;
import com.imagemanagement.dto.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ApiResponse<AiChatSearchResult>> chat(@Valid @RequestBody AiChatRequest request) {
        AiChatSearchResult result = aiServiceClient.chatSearch(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
