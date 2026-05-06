package com.prodigalgal.xaigateway.provider.adapter.gemini;

import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class GeminiCachedContentApiClient {

    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public GeminiCachedContentApiClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    public ObjectNode create(String baseUrl, String apiKey, ObjectNode payload) {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        String path = normalizedBaseUrl.endsWith("/v1beta") ? "/cachedContents" : "/v1beta/cachedContents";
        String rawResponse = webClientBuilder
                .baseUrl(normalizedBaseUrl)
                .build()
                .post()
                .uri(uriBuilder -> uriBuilder.path(path).queryParam("key", apiKey).build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload.toString())
                .retrieve()
                .bodyToMono(String.class)
                .block(REQUEST_TIMEOUT);
        try {
            JsonNode node = rawResponse == null || rawResponse.isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(rawResponse);
            return node instanceof ObjectNode objectNode ? objectNode : objectMapper.createObjectNode();
        } catch (JacksonException exception) {
            throw new IllegalStateException("解析 Gemini cachedContents 响应失败。", exception);
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
