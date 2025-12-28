package com.imagemanagement.ai;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import com.imagemanagement.ai.dto.AiHealthStatus;
import com.imagemanagement.ai.dto.AiTagSuggestionResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
class AiServiceConnectivityTest {

    private static final Path SAMPLE_IMAGE = Path.of("..", "test", "Pictures", "beach.jpeg").toAbsolutePath().normalize();
    private static final String HEALTH_RESPONSE = """
        {"status":"ok","data":{"service":"picture-ai-service","version":"1.0.0","status":"healthy","timestamp":"2025-01-01T00:00:00Z","python":"3.11.8"},"message":null}
        """;
    private static final String TAGS_RESPONSE = """
        {"status":"ok","data":{"tags":[{"name":"vacation","confidence":0.92,"source":"vision"},{"name":"ocean","confidence":0.88,"source":"vision"}],"metadata":{"width":1920,"height":1080,"aspect_ratio":"16:9"}},"message":null}
        """;
    private static final String ERROR_RESPONSE = """
        {"status":"error","data":null,"message":"Provide a valid image"}
        """;

    private static MockWebServer aiServiceServer;

    @DynamicPropertySource
    static void configureAiServiceUrl(DynamicPropertyRegistry registry) {
        ensureAiServiceServer();
        registry.add("app.ai.service-url", AiServiceConnectivityTest::baseUrl);
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (aiServiceServer != null) {
            aiServiceServer.shutdown();
            aiServiceServer = null;
        }
    }

    private static synchronized void ensureAiServiceServer() {
        if (aiServiceServer != null) {
            return;
        }
        aiServiceServer = new MockWebServer();
        aiServiceServer.setDispatcher(createDispatcher());
        try {
            aiServiceServer.start();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start mock AI service", ex);
        }
    }

    private static Dispatcher createDispatcher() {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if ("/ai/v1/health".equals(path)) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(HEALTH_RESPONSE);
                }
                if ("/ai/v1/tags/suggest".equals(path)) {
                    return handleTagSuggestion(request);
                }
                return new MockResponse().setResponseCode(404);
            }
        };
    }

    private static MockResponse handleTagSuggestion(RecordedRequest request) {
        String payload = request.getBody().readUtf8();
        if (payload.contains("filename=\"invalid.txt\"")) {
            return new MockResponse()
                    .setResponseCode(400)
                    .setHeader("Content-Type", "application/json")
                    .setBody(ERROR_RESPONSE);
        }
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(TAGS_RESPONSE);
    }

    private static String baseUrl() {
        String url = aiServiceServer.url("/").toString();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Autowired
    private AiServiceClient aiServiceClient;

    @Test
    @DisplayName("health endpoint should respond via Java client")
    void shouldFetchHealthStatus() {
        AiHealthStatus status = aiServiceClient.getHealth();
        Assertions.assertThat(status.service()).isEqualTo("picture-ai-service");
        Assertions.assertThat(status.status()).isEqualTo("healthy");
        Assertions.assertThat(status.python()).isNotBlank();
    }

    @Test
    @DisplayName("tag suggestion should analyze uploaded images")
    void shouldSuggestTagsFromUploadedImage() throws IOException {
        byte[] imageBytes = Files.readAllBytes(SAMPLE_IMAGE);
        AiTagSuggestionResponse response = aiServiceClient.suggestTags(imageBytes, SAMPLE_IMAGE.getFileName().toString(), List.of("vacation", "ocean"), 5);
        Assertions.assertThat(response.tags()).isNotEmpty();
        Assertions.assertThat(response.metadata()).containsKeys("width", "height", "aspect_ratio");
    }

    @Test
    @DisplayName("AI service errors should propagate through the client")
    void shouldPropagateAiServiceErrors() {
        byte[] invalidPayload = "not-an-image".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Assertions.assertThatThrownBy(() -> aiServiceClient.suggestTags(invalidPayload, "invalid.txt", List.of(), null))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("valid image");
    }
}
