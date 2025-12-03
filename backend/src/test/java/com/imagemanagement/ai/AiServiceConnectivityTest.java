package com.imagemanagement.ai;

import com.imagemanagement.ai.dto.AiHealthStatus;
import com.imagemanagement.ai.dto.AiSearchInterpretation;
import com.imagemanagement.ai.dto.AiTagSuggestionResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AiServiceConnectivityTest {

    private static final Path AI_SERVICE_DIR = Path.of("..", "ai-service").toAbsolutePath().normalize();
    private static final Path SAMPLE_IMAGE = Path.of("..", "test", "Pictures", "beach.jpeg").toAbsolutePath().normalize();
    private static final boolean DOCKER_AVAILABLE = DockerClientFactory.instance().isDockerAvailable();

        @Container
        @SuppressWarnings("resource")
        static final GenericContainer<?> aiService = new GenericContainer<>(DockerImageName.parse("python:3.11-slim"))
            .withExposedPorts(5000)
            .withEnv("PYTHONUNBUFFERED", "1")
            .withCopyFileToContainer(MountableFile.forHostPath(AI_SERVICE_DIR), "/opt/ai-service")
            .withCommand("/bin/sh", "-c", String.join(" && ",
                "set -e",
                "apt-get update",
                "apt-get install -y gcc g++",
                "pip install --no-cache-dir -r /opt/ai-service/requirements.txt",
                "cd /opt/ai-service",
                "exec gunicorn --bind 0.0.0.0:5000 --workers 2 app.main:app"))
            .waitingFor(Wait.forHttp("/ai/v1/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(5)));

    @DynamicPropertySource
    static void configureAiServiceUrl(DynamicPropertyRegistry registry) {
        Assumptions.assumeTrue(DOCKER_AVAILABLE, "Docker is required for AI service connectivity tests");
        Startables.deepStart(java.util.stream.Stream.of(aiService)).join();
        registry.add("app.ai.service-url", () -> String.format("http://%s:%d", aiService.getHost(), aiService.getMappedPort(5000)));
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
    @DisplayName("interpret endpoint should convert natural language into filters")
    void shouldInterpretSearchQuery() {
        AiSearchInterpretation interpretation = aiServiceClient.interpretSearch("sunset beach 4k portrait", 5);
        Assertions.assertThat(interpretation.tags()).isNotEmpty();
        Assertions.assertThat(interpretation.filters()).containsKeys("tags", "keyword", "minWidth", "minHeight");
        Assertions.assertThat(interpretation.confidence()).isNotNull();
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
