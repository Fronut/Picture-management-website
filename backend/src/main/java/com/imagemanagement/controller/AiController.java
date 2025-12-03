package com.imagemanagement.controller;

import com.imagemanagement.ai.AiServiceClient;
import com.imagemanagement.ai.dto.AiSearchInterpretation;
import com.imagemanagement.dto.request.AiSearchInterpretRequest;
import com.imagemanagement.dto.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiServiceClient aiServiceClient;

    public AiController(AiServiceClient aiServiceClient) {
        this.aiServiceClient = aiServiceClient;
    }

    @PostMapping("/search/interpret")
    public ResponseEntity<ApiResponse<AiSearchInterpretation>> interpretSearch(
            @Valid @RequestBody AiSearchInterpretRequest request) {
        AiSearchInterpretation interpretation = aiServiceClient.interpretSearch(request.query(), request.limit());
        return ResponseEntity.ok(ApiResponse.success(interpretation));
    }
}
