package com.prodigalgal.xaigateway.admin.application;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class CodexResponsesSmokeHttpClient {

    private static final String DEFAULT_CODEX_APP_BASE_URL = "https://chatgpt.com/backend-api/codex";
    private static final String DEFAULT_CODEX_MODEL_DESCRIPTOR = "gpt-5.4@low";
    private static final String CODEX_USER_AGENT = "codex_cli_rs/x-ai-gateway (Windows 10.0; x86_64)";

    private final ObjectMapper objectMapper;

    CodexResponsesSmokeHttpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Map<String, Object> requestPreview(String model, String input, String requestedBaseUrl, String chatGptAccountId) {
        EndpointPlan plan = endpointPlan(requestedBaseUrl);
        String previewSessionId = "generated-per-smoke";
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("authorization", "Bearer ***");
        headers.put("content-type", "application/json");
        if (plan.codexAppApi()) {
            headers.put("accept", "text/event-stream");
            headers.put("openai-beta", "responses=experimental");
            headers.put("originator", "codex_cli_rs");
            headers.put("session_id", previewSessionId);
            headers.put("conversation_id", previewSessionId);
            headers.put("x-client-request-id", previewSessionId);
            headers.put("x-codex-installation-id", "generated-per-smoke");
            headers.put("x-codex-window-id", previewSessionId + ":0");
            headers.put("user-agent", CODEX_USER_AGENT);
            if (!isBlank(chatGptAccountId)) {
                headers.put("ChatGPT-Account-Id", "***");
            }
        } else {
            headers.put("accept", "application/json");
        }

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("method", "POST");
        preview.put("baseUrl", plan.baseUrl());
        preview.put("path", plan.responsesPath());
        preview.put("codexAppApi", plan.codexAppApi());
        preview.put("headers", headers);
        preview.put("body", requestBody(model, input, previewSessionId, plan.codexAppApi()));
        if (plan.codexAppApi()) {
            preview.put("keepalive", Map.of(
                    "method", "GET",
                    "path", plan.usagePath(),
                    "baseUrl", plan.usageBaseUrl()
            ));
        }
        return preview;
    }

    CodexResponsesSmokeResult execute(
            String accessToken,
            String model,
            String input,
            String requestedBaseUrl,
            Integer requestedTimeoutSeconds,
            String chatGptAccountId) {
        int timeoutSeconds = requestedTimeoutSeconds == null || requestedTimeoutSeconds <= 0
                ? 20
                : Math.min(60, Math.max(3, requestedTimeoutSeconds));
        EndpointPlan plan = endpointPlan(requestedBaseUrl);
        String sessionId = UUID.randomUUID().toString();
        long startedNanos = System.nanoTime();
        CodexUsageProbeResult usageProbe = plan.codexAppApi()
                ? fetchUsage(accessToken, plan, timeoutSeconds, chatGptAccountId)
                : null;
        String usageBlockFailureType = usageBlockFailureType(usageProbe);
        if (usageBlockFailureType != null) {
            return guardedFailure(
                    usageBlockFailureType,
                    usageBlockMessage(usageProbe, usageBlockFailureType),
                    startedNanos,
                    plan,
                    usageProbe
            );
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                    .build();
            String body = writeJson(requestBody(model, input, sessionId, plan.codexAppApi()));
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(plan.responsesUrl()))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("authorization", "Bearer " + accessToken)
                    .header("content-type", "application/json");
            applyResponsesHeaders(builder, plan, sessionId, chatGptAccountId);
            HttpResponse<String> response = client.send(
                    builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
            Map<String, Object> responseBody = readJsonMap(response.body());
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            return new CodexResponsesSmokeResult(
                    success,
                    response.statusCode(),
                    responseRequestId(response),
                    extractResponseId(response.body(), responseBody),
                    durationMs,
                    plan.baseUrl(),
                    plan.responsesPath(),
                    plan.codexAppApi(),
                    success ? null : failureType(response.statusCode(), responseBody, response.body()),
                    success ? null : sanitizeFailureMessage(failureMessage(responseBody, response.body()), accessToken),
                    usageProbe
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure("INTERRUPTED", "真实 smoke 被中断。", startedNanos, plan, usageProbe);
        } catch (IOException | IllegalArgumentException exception) {
            return failure(exception.getClass().getSimpleName(), exception.getMessage(), startedNanos, plan, usageProbe);
        }
    }

    private CodexUsageProbeResult fetchUsage(
            String accessToken,
            EndpointPlan plan,
            int timeoutSeconds,
            String chatGptAccountId) {
        long startedNanos = System.nanoTime();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                    .build();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(plan.usageUrl()))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("authorization", "Bearer " + accessToken)
                    .header("accept", "application/json");
            if (!isBlank(chatGptAccountId)) {
                builder.header("ChatGPT-Account-Id", chatGptAccountId);
            }
            HttpResponse<String> response = client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
            long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
            Map<String, Object> body = readJsonMap(response.body());
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            return new CodexUsageProbeResult(
                    success,
                    response.statusCode(),
                    responseRequestId(response),
                    durationMs,
                    plan.usageBaseUrl(),
                    plan.usagePath(),
                    text(body.get("plan_type")),
                    booleanValue(nestedValue(body, "rate_limit", "allowed")),
                    booleanValue(nestedValue(body, "rate_limit", "limit_reached")),
                    windowSummary(nestedMap(body, "rate_limit", "primary_window")),
                    windowSummary(nestedMap(body, "rate_limit", "secondary_window")),
                    success ? null : failureType(response.statusCode(), body, response.body())
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
            return new CodexUsageProbeResult(false, null, null, durationMs, plan.usageBaseUrl(), plan.usagePath(), null, null, null, null, null, "INTERRUPTED");
        } catch (IOException | IllegalArgumentException exception) {
            long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
            return new CodexUsageProbeResult(false, null, null, durationMs, plan.usageBaseUrl(), plan.usagePath(), null, null, null, null, null, exception.getClass().getSimpleName());
        }
    }

    private void applyResponsesHeaders(
            HttpRequest.Builder builder,
            EndpointPlan plan,
            String sessionId,
            String chatGptAccountId) {
        if (!plan.codexAppApi()) {
            builder.header("accept", "application/json");
            return;
        }
        builder.header("accept", "text/event-stream");
        builder.header("openai-beta", "responses=experimental");
        builder.header("originator", "codex_cli_rs");
        builder.header("session_id", sessionId);
        builder.header("conversation_id", sessionId);
        builder.header("x-client-request-id", sessionId);
        builder.header("x-codex-installation-id", "x-ai-gateway-smoke");
        builder.header("x-codex-window-id", sessionId + ":0");
        builder.header("user-agent", CODEX_USER_AGENT);
        if (!isBlank(chatGptAccountId)) {
            builder.header("ChatGPT-Account-Id", chatGptAccountId);
        }
    }

    private Map<String, Object> requestBody(String model, String input, String sessionId, boolean codexAppApi) {
        ParsedSmokeModel safeModel = parseSmokeModel(model);
        String safeInput = isBlank(input) ? "x-ai-gateway codex live smoke" : input.trim();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", safeModel.model());
        if (codexAppApi) {
            body.put("instructions", "");
            body.put("input", List.of(Map.of(
                    "type", "message",
                    "role", "user",
                    "content", List.of(Map.of(
                            "type", "input_text",
                            "text", safeInput
                    ))
            )));
            body.put("tools", List.of());
            body.put("tool_choice", "auto");
            body.put("parallel_tool_calls", false);
            if (!isBlank(safeModel.reasoningEffort())) {
                body.put("reasoning", Map.of("effort", safeModel.reasoningEffort()));
            }
            body.put("store", false);
            body.put("stream", true);
            body.put("include", List.of("reasoning.encrypted_content"));
            body.put("prompt_cache_key", sessionId);
            body.put("client_metadata", Map.of("x-codex-installation-id", "x-ai-gateway-smoke"));
            return body;
        }
        body.put("input", safeInput);
        body.put("store", false);
        return body;
    }

    private ParsedSmokeModel parseSmokeModel(String rawModel) {
        String descriptor = isBlank(rawModel) ? DEFAULT_CODEX_MODEL_DESCRIPTOR : rawModel.trim();
        int separatorIndex = Math.max(descriptor.lastIndexOf('@'), descriptor.lastIndexOf('#'));
        String model = descriptor;
        String effort = null;
        if (separatorIndex > 0 && separatorIndex < descriptor.length() - 1) {
            String suffix = descriptor.substring(separatorIndex + 1).trim().toLowerCase(Locale.ROOT);
            if (isAllowedReasoningEffort(suffix)) {
                model = descriptor.substring(0, separatorIndex).trim();
                effort = suffix;
            }
        }
        if (isBlank(effort) && supportsReasoningEffort(model)) {
            effort = "low";
        }
        return new ParsedSmokeModel(model, effort);
    }

    private boolean isAllowedReasoningEffort(String value) {
        return switch (value) {
            case "minimal", "low", "medium", "high", "xhigh" -> true;
            default -> false;
        };
    }

    private boolean supportsReasoningEffort(String model) {
        if (isBlank(model)) {
            return false;
        }
        String lower = model.toLowerCase(Locale.ROOT);
        return lower.startsWith("gpt-5") || lower.startsWith("o1") || lower.startsWith("o3") || lower.startsWith("o4");
    }

    private CodexResponsesSmokeResult failure(
            String type,
            String message,
            long startedNanos,
            EndpointPlan plan,
            CodexUsageProbeResult usageProbe) {
        long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
        return new CodexResponsesSmokeResult(
                false,
                null,
                null,
                null,
                durationMs,
                plan.baseUrl(),
                plan.responsesPath(),
                plan.codexAppApi(),
                type,
                truncate(message, 240),
                usageProbe
        );
    }

    private CodexResponsesSmokeResult guardedFailure(
            String type,
            String message,
            long startedNanos,
            EndpointPlan plan,
            CodexUsageProbeResult usageProbe) {
        long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
        return new CodexResponsesSmokeResult(
                false,
                usageProbe == null ? null : usageProbe.httpStatus(),
                usageProbe == null ? null : usageProbe.upstreamRequestId(),
                null,
                durationMs,
                plan.baseUrl(),
                plan.responsesPath(),
                plan.codexAppApi(),
                type,
                truncate(message, 240),
                usageProbe
        );
    }

    private String usageBlockFailureType(CodexUsageProbeResult usageProbe) {
        if (usageProbe == null) {
            return null;
        }
        if (usageProbe.httpStatus() != null && (usageProbe.httpStatus() == 401 || usageProbe.httpStatus() == 403)) {
            return "NO_PERMISSION";
        }
        if (containsAny(usageProbe.failureType(), "auth", "permission", "unauthorized", "forbidden")) {
            return "NO_PERMISSION";
        }
        if (Boolean.TRUE.equals(usageProbe.limitReached()) || Boolean.FALSE.equals(usageProbe.allowed())) {
            return "BUDGET_BLOCKED";
        }
        if (usageProbe.httpStatus() != null && usageProbe.httpStatus() == 429) {
            return "BUDGET_BLOCKED";
        }
        if (containsAny(usageProbe.failureType(), "quota", "rate_limit", "rate", "budget", "limit")) {
            return "BUDGET_BLOCKED";
        }
        return null;
    }

    private String usageBlockMessage(CodexUsageProbeResult usageProbe, String failureType) {
        if ("NO_PERMISSION".equals(failureType)) {
            return "Codex usage probe 返回认证或权限失败，已跳过 responses 真实请求。";
        }
        if (usageProbe != null && Boolean.TRUE.equals(usageProbe.limitReached())) {
            return "Codex usage probe 显示 limit_reached=true，已跳过 responses 真实请求。";
        }
        if (usageProbe != null && Boolean.FALSE.equals(usageProbe.allowed())) {
            return "Codex usage probe 显示 allowed=false，已跳过 responses 真实请求。";
        }
        return "Codex usage probe 显示额度或速率限制风险，已跳过 responses 真实请求。";
    }

    private EndpointPlan endpointPlan(String requestedBaseUrl) {
        String normalized = normalizeBaseUrl(requestedBaseUrl);
        if (normalized == null) {
            return codexAppPlan(DEFAULT_CODEX_APP_BASE_URL);
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("api.openai.com")) {
            return openAiApiPlan(normalized);
        }
        if (lower.startsWith("https://chatgpt.com") || lower.startsWith("https://chat.openai.com")) {
            String base = normalized;
            if (!lower.contains("/backend-api")) {
                base = base + "/backend-api/codex";
            } else if (lower.endsWith("/backend-api")) {
                base = base + "/codex";
            }
            return codexAppPlan(base);
        }
        if (lower.contains("/backend-api/codex")) {
            return codexAppPlan(normalized);
        }
        if (lower.endsWith("/v1") || lower.endsWith("/v1/responses")) {
            return openAiApiPlan(normalized);
        }
        return codexAppPlan(normalized);
    }

    private EndpointPlan codexAppPlan(String requestedBaseUrl) {
        String base = normalizeBaseUrl(requestedBaseUrl);
        String responsesBase = stripSuffix(base, "/responses");
        String backendBase = stripSuffix(responsesBase, "/codex");
        String responsesUrl = responsesBase.endsWith("/responses") ? responsesBase : responsesBase + "/responses";
        String usageUrl = backendBase + "/wham/usage";
        return new EndpointPlan(
                responsesBase,
                responsesUrl,
                URI.create(responsesUrl).getPath(),
                backendBase,
                usageUrl,
                URI.create(usageUrl).getPath(),
                true
        );
    }

    private EndpointPlan openAiApiPlan(String requestedBaseUrl) {
        String base = normalizeBaseUrl(requestedBaseUrl);
        String responsesUrl;
        String displayBase;
        if (base.endsWith("/v1/responses")) {
            responsesUrl = base;
            displayBase = stripSuffix(base, "/v1/responses");
        } else if (base.endsWith("/v1")) {
            responsesUrl = base + "/responses";
            displayBase = stripSuffix(base, "/v1");
        } else {
            responsesUrl = base + "/v1/responses";
            displayBase = base;
        }
        return new EndpointPlan(
                displayBase,
                responsesUrl,
                URI.create(responsesUrl).getPath(),
                null,
                null,
                null,
                false
        );
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

    private String extractResponseId(String rawBody, Map<String, Object> responseBody) {
        String direct = text(responseBody.get("id"));
        if (!isBlank(direct)) {
            return direct;
        }
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        for (String line : rawBody.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("data:")) {
                continue;
            }
            String payload = trimmed.substring("data:".length()).trim();
            if (payload.isBlank() || "[DONE]".equals(payload)) {
                continue;
            }
            Map<String, Object> event = readJsonMap(payload);
            String id = firstNonBlank(
                    text(event.get("id")),
                    text(nestedValue(event, "response", "id")),
                    text(nestedValue(event, "item", "id"))
            );
            if (!isBlank(id)) {
                return id;
            }
        }
        return null;
    }

    private String failureType(int statusCode, Map<String, Object> body, String rawBody) {
        Object error = body.get("error");
        if (error instanceof Map<?, ?> errorMap) {
            String type = firstNonBlank(text(errorMap.get("type")), text(errorMap.get("code")));
            if (!isBlank(type)) {
                if (isModelUnsupported(statusCode, rawBody, type)) {
                    return "MODEL_NOT_SUPPORTED";
                }
                return type;
            }
        }
        String detailCode = text(nestedValue(body, "detail", "code"));
        if (!isBlank(detailCode)) {
            if (isModelUnsupported(statusCode, rawBody, detailCode)) {
                return "MODEL_NOT_SUPPORTED";
            }
            return detailCode;
        }
        String code = text(body.get("code"));
        if (!isBlank(code)) {
            if (isModelUnsupported(statusCode, rawBody, code)) {
                return "MODEL_NOT_SUPPORTED";
            }
            return code;
        }
        String detail = text(body.get("detail"));
        if (isModelUnsupported(statusCode, rawBody, detail)) {
            return "MODEL_NOT_SUPPORTED";
        }
        String lowerRaw = rawBody == null ? "" : rawBody.toLowerCase(Locale.ROOT);
        if (lowerRaw.contains("unsupported_parameter") || lowerRaw.contains("unsupported parameter")) {
            return "UNSUPPORTED_PARAMETER";
        }
        return "HTTP_" + statusCode;
    }

    private boolean isModelUnsupported(int statusCode, String rawBody, String structuredCodeOrMessage) {
        if (statusCode < 400 || statusCode >= 500) {
            return false;
        }
        String haystack = (defaultString(structuredCodeOrMessage, "") + "\n" + defaultString(rawBody, "")).toLowerCase(Locale.ROOT);
        if (!haystack.contains("model")) {
            return false;
        }
        return haystack.contains("model_not_found")
                || haystack.contains("model not found")
                || haystack.contains("does not exist")
                || haystack.contains("invalid_model")
                || haystack.contains("invalid model")
                || haystack.contains("unknown_model")
                || haystack.contains("unknown model")
                || haystack.contains("is not a valid model")
                || haystack.contains("not supported when using codex")
                || haystack.contains("model is not supported");
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

    private String sanitizeFailureMessage(String message, String accessToken) {
        if (message == null) {
            return null;
        }
        String sanitized = message;
        if (!isBlank(accessToken)) {
            sanitized = sanitized.replace(accessToken, "***");
        }
        sanitized = sanitized.replaceAll("Bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer ***");
        return truncate(sanitized, 240);
    }

    private Map<String, Object> windowSummary(Map<String, Object> window) {
        if (window == null || window.isEmpty()) {
            return null;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("present", true);
        Integer usedPercent = integerValue(window.get("used_percent"));
        if (usedPercent != null) {
            summary.put("remainingPercent", Math.max(0, Math.min(100, 100 - usedPercent)));
        }
        putIfPresent(summary, "limitWindowSeconds", integerValue(window.get("limit_window_seconds")));
        putIfPresent(summary, "resetAfterSeconds", integerValue(window.get("reset_after_seconds")));
        putIfPresent(summary, "resetAt", window.get("reset_at"));
        return summary;
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("无法序列化 Codex smoke 请求。", exception);
        }
    }

    private Map<String, Object> nestedMap(Map<String, Object> source, String first, String second) {
        Object value = nestedValue(source, first, second);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                if (key != null) {
                    result.put(String.valueOf(key), item);
                }
            });
            return result;
        }
        return null;
    }

    private Object nestedValue(Map<String, Object> source, String first, String second) {
        Object nested = source.get(first);
        if (nested instanceof Map<?, ?> map) {
            return map.get(second);
        }
        return null;
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return null;
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String defaultString(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private boolean containsAny(String value, String... needles) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (lower.contains(needle)) {
                return true;
            }
        }
        return false;
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

    private record EndpointPlan(
            String baseUrl,
            String responsesUrl,
            String responsesPath,
            String usageBaseUrl,
            String usageUrl,
            String usagePath,
            boolean codexAppApi) {
    }

    private record ParsedSmokeModel(String model, String reasoningEffort) {
    }

    record CodexResponsesSmokeResult(
            boolean success,
            Integer httpStatus,
            String upstreamRequestId,
            String upstreamResponseId,
            long durationMs,
            String baseUrl,
            String path,
            boolean codexAppApi,
            String failureType,
            String failureMessage,
            CodexUsageProbeResult usageProbe) {
    }

    record CodexUsageProbeResult(
            boolean success,
            Integer httpStatus,
            String upstreamRequestId,
            long durationMs,
            String baseUrl,
            String path,
            String planType,
            Boolean allowed,
            Boolean limitReached,
            Map<String, Object> primaryWindow,
            Map<String, Object> secondaryWindow,
            String failureType) {

        Map<String, Object> toSafeMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", success);
            putNullable(result, "httpStatus", httpStatus);
            putNullable(result, "upstreamRequestId", upstreamRequestId);
            result.put("durationMs", durationMs);
            putNullable(result, "baseUrl", baseUrl);
            putNullable(result, "path", path);
            putNullable(result, "planType", planType);
            putNullable(result, "allowed", allowed);
            putNullable(result, "limitReached", limitReached);
            putNullable(result, "primaryWindow", primaryWindow);
            putNullable(result, "secondaryWindow", secondaryWindow);
            putNullable(result, "failureType", failureType);
            return result;
        }

        private void putNullable(Map<String, Object> target, String key, Object value) {
            if (value != null) {
                target.put(key, value);
            }
        }
    }
}
