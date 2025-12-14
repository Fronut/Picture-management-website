package com.imagemanagement.ai;

import com.imagemanagement.ai.dto.AiHealthStatus;
import com.imagemanagement.ai.dto.AiResponseEnvelope;
import com.imagemanagement.ai.dto.AiTagSuggestionResponse;
import com.imagemanagement.ai.dto.AiChatSearchResult;
import com.imagemanagement.dto.request.AiChatRequest;
import com.imagemanagement.config.AiServiceProperties;
import java.util.List;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.io.ByteArrayResource;

@Component
public class AiServiceClient {

    private static final ParameterizedTypeReference<AiResponseEnvelope<AiHealthStatus>> HEALTH_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<AiResponseEnvelope<AiTagSuggestionResponse>> TAGS_TYPE =
            new ParameterizedTypeReference<>() {
            };
        private static final ParameterizedTypeReference<AiResponseEnvelope<AiChatSearchResult>> CHAT_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestTemplate restTemplate;
    private final String serviceUrl;

    public AiServiceClient(RestTemplateBuilder restTemplateBuilder, AiServiceProperties properties) {
        this.serviceUrl = properties.getServiceUrl();
        this.restTemplate = restTemplateBuilder
            .rootUri(properties.getServiceUrl())
                .setConnectTimeout(properties.getTimeout())
                .setReadTimeout(properties.getTimeout())
                .build();
    }

    public AiHealthStatus getHealth() {
        return exchange("/ai/v1/health", HttpMethod.GET, null, HEALTH_TYPE);
    }

    public AiChatSearchResult chatSearch(AiChatRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        AiChatRequest normalized = request.normalized();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<AiChatRequest> entity = new HttpEntity<>(normalized, headers);
        return exchange("/ai/v1/search/chat", HttpMethod.POST, entity, CHAT_TYPE);
    }

    public AiTagSuggestionResponse suggestTags(byte[] imageBytes, String filename, List<String> hints, Integer limit) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("imageBytes must not be empty");
        }
        String resolvedName = StringUtils.hasText(filename) ? filename : "upload.jpg";
        ByteArrayResource fileResource = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return resolvedName;
            }
        };

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", fileResource);
        if (limit != null && limit > 0) {
            form.add("limit", String.valueOf(limit));
        }
        if (hints != null) {
            for (String hint : hints) {
                if (StringUtils.hasText(hint)) {
                    form.add("hints", hint);
                }
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(form, headers);
        return exchange("/ai/v1/tags/suggest", HttpMethod.POST, request, TAGS_TYPE);
    }

    private <T> T exchange(String path,
            HttpMethod method,
            HttpEntity<?> request,
            ParameterizedTypeReference<AiResponseEnvelope<T>> targetType) {
        try {
                ResponseEntity<AiResponseEnvelope<T>> response = restTemplate.exchange(
                    java.util.Objects.requireNonNull(path, "path must not be null"),
                    java.util.Objects.requireNonNull(method, "method must not be null"),
                    request,
                    java.util.Objects.requireNonNull(targetType, "targetType must not be null"));
            return unwrap(response);
        } catch (RestClientException ex) {
            String message = String.format("Failed to communicate with AI service at %s", serviceUrl);
            if (ex instanceof HttpStatusCodeException statusException) {
                message = String.format("AI service responded with %s: %s",
                        statusException.getStatusCode(),
                        statusException.getResponseBodyAsString());
            }
            throw new AiServiceException(message, ex);
        }
    }

    private <T> T unwrap(ResponseEntity<AiResponseEnvelope<T>> response) {
        if (response == null) {
            throw new AiServiceException("AI service returned no response");
        }
        AiResponseEnvelope<T> envelope = response.getBody();
        if (envelope == null) {
            throw new AiServiceException("AI service returned empty response body");
        }
        if (!envelope.isOk()) {
            throw new AiServiceException(envelope.message() != null ? envelope.message() : "AI service reported an error");
        }
        T data = envelope.data();
        if (data == null) {
            throw new AiServiceException("AI service returned no data");
        }
        return data;
    }
}
