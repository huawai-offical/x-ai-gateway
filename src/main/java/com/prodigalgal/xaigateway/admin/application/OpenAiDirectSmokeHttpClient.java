package com.prodigalgal.xaigateway.admin.application;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class OpenAiDirectSmokeHttpClient {

    private static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com";

    private final ObjectMapper objectMapper;

    OpenAiDirectSmokeHttpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Map<String, Object> requestPreview(String requestedBaseUrl, String organization, String project) {
        EndpointPlan plan = endpointPlan(requestedBaseUrl);
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("authorization", "Bearer ***");
        headers.put("accept", "application/json");
        if (!isBlank(organization)) {
            headers.put("OpenAI-Organization", "***");
        }
        if (!isBlank(project)) {
            headers.put("OpenAI-Project", "***");
        }
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("method", "GET");
        preview.put("baseUrl", plan.baseUrl());
        preview.put("path", plan.path());
        preview.put("headers", headers);
        preview.put("body", null);
        return preview;
    }

    OpenAiDirectSmokeResult execute(
            String secret,
            String requestedBaseUrl,
            Integer requestedTimeoutSeconds,
            String organization,
            String project) {
        int timeoutSeconds = requestedTimeoutSeconds == null || requestedTimeoutSeconds <= 0
                ? 10
                : Math.min(30, Math.max(3, requestedTimeoutSeconds));
        EndpointPlan plan = endpointPlan(requestedBaseUrl);
        long startedNanos = System.nanoTime();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                    .build();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(plan.modelsUrl()))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("authorization", "Bearer " + secret)
                    .header("accept", "application/json");
            if (!isBlank(organization)) {
                builder.header("OpenAI-Organization", organization.trim());
            }
            if (!isBlank(project)) {
                builder.header("OpenAI-Project", project.trim());
            }
            HttpResponse<String> response = client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
            long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
            Map<String, Object> body = readJsonMap(response.body());
            List<String> sampleModels = sampleModels(body);
            Integer modelsCount = modelsCount(body);
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            return new OpenAiDirectSmokeResult(
                    success,
                    response.statusCode(),
                    responseRequestId(response),
                    durationMs,
                    plan.baseUrl(),
                    plan.path(),
                    success ? null : failureType(response.statusCode(), body, response.body()),
                    success ? null : sanitizeFailureMessage(failureMessage(body, response.body()), secret),
                    modelsCount,
                    sampleModels
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure("INTERRUPTED", "OpenAI Direct smoke 被中断。", startedNanos, plan);
        } catch (IOException | IllegalArgumentException exception) {
            return failure(exception.getClass().getSimpleName(), exception.getMessage(), startedNanos, plan);
        }
    }

    private OpenAiDirectSmokeResult failure(
            String type,
            String message,
            long startedNanos,
            EndpointPlan plan) {
        long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
        return new OpenAiDirectSmokeResult(
                false,
                null,
                null,
                durationMs,
                plan.baseUrl(),
                plan.path(),
                type,
                truncate(message, 240),
                null,
                List.of()
        );
    }

    private EndpointPlan endpointPlan(String requestedBaseUrl) {
        String base = normalizeBaseUrl(requestedBaseUrl);
        if (base == null) {
            base = DEFAULT_OPENAI_BASE_URL;
        }
        String lower = base.toLowerCase(Locale.ROOT);
        String modelsUrl;
        String displayBase;
        if (lower.endsWith("/v1/models")) {
            modelsUrl = base;
            displayBase = stripSuffix(base, "/v1/models");
        } else if (lower.endsWith("/v1")) {
            modelsUrl = base + "/models";
            displayBase = stripSuffix(base, "/v1");
        } else {
            modelsUrl = base + "/v1/models";
            displayBase = base;
        }
        return new EndpointPlan(displayBase, modelsUrl, URI.create(modelsUrl).getPath());
    }

    private String normalizeBaseUrl(String requestedBaseUrl) {
        if (requestedBaseUrl == null || requestedBaseUrl.isBlank()) {
            return null;
        }
        String trimmed = requestedBaseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String stripSuffix(String value, String suffix) {
        if (value == null || !value.endsWith(suffix)) {
            return value;
        }
        return value.substring(0, value.length() - suffix.length());
    }

    private String responseRequestId(HttpResponse<?> response) {
        return firstNonBlank(
                response.headers().firstValue("x-request-id").orElse(null),
                response.headers().firstValue("request-id").orElse(null),
                response.headers().firstValue("cf-ray").orElse(null)
        );
    }

    private Map<String, Object> readJsonMap(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(value, new TypeReference<>() {
            });
            return parsed == null ? Map.of() : new LinkedHashMap<>(parsed);
        } catch (JacksonException exception) {
            return Map.of();
        }
    }

    private Integer modelsCount(Map<String, Object> body) {
        Object data = body.get("data");
        if (data instanceof List<?> list) {
            return list.size();
        }
        return null;
    }

    private List<String> sampleModels(Map<String, Object> body) {
        Object data = body.get("data");
        if (!(data instanceof List<?> list)) {
            return List.of();
        }
        List<String> models = new ArrayList<>();
        for (Object item : list) {
            if (models.size() >= 10) {
                break;
            }
            if (item instanceof Map<?, ?> map) {
                String id = text(map.get("id"));
                if (!isBlank(id)) {
                    models.add(id);
                }
            }
        }
        return List.copyOf(models);
    }

    private String failureType(int statusCode, Map<String, Object> body, String rawBody) {
        Object error = body.get("error");
        if (error instanceof Map<?, ?> errorMap) {
            String type = firstNonBlank(text(errorMap.get("type")), text(errorMap.get("code")));
            if (!isBlank(type)) {
                return type;
            }
        }
        String code = text(body.get("code"));
        if (!isBlank(code)) {
            return code;
        }
        String lowerRaw = rawBody == null ? "" : rawBody.toLowerCase(Locale.ROOT);
        if (lowerRaw.contains("rate limit") || lowerRaw.contains("rate_limit")) {
            return "rate_limit_error";
        }
        return "HTTP_" + statusCode;
    }

    private String failureMessage(Map<String, Object> body, String rawBody) {
        Object error = body.get("error");
        if (error instanceof Map<?, ?> errorMap) {
            String message = text(errorMap.get("message"));
            if (!isBlank(message)) {
                return truncate(message, 240);
            }
        }
        String detail = text(body.get("detail"));
        if (!isBlank(detail)) {
            return truncate(detail, 240);
        }
        return rawBody == null || rawBody.isBlank() ? null : truncate(rawBody, 240);
    }

    private String sanitizeFailureMessage(String message, String secret) {
        if (message == null) {
            return null;
        }
        String sanitized = message;
        if (!isBlank(secret)) {
            sanitized = sanitized.replace(secret, "***");
        }
        sanitized = sanitized.replaceAll("Bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer ***");
        return truncate(sanitized, 240);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record EndpointPlan(String baseUrl, String modelsUrl, String path) {
    }

    record OpenAiDirectSmokeResult(
            boolean success,
            Integer httpStatus,
            String upstreamRequestId,
            long durationMs,
            String baseUrl,
            String path,
            String failureType,
            String failureMessage,
            Integer modelsCount,
            List<String> sampleModels) {
    }
}
