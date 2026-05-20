package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.OpenAiDirectResourceSmokeItemResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class OpenAiDirectResourceSmokeHttpClient {

    static final String CHAT_COMPLETIONS = "CHAT_COMPLETIONS";
    static final String RESPONSES = "RESPONSES";
    static final String FILES = "FILES";
    static final String VECTOR_STORES = "VECTOR_STORES";
    static final String REALTIME_CLIENT_SECRET = "REALTIME_CLIENT_SECRET";

    private static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com";
    private static final String DEFAULT_GENERATION_MODEL = "gpt-4o-mini";
    private static final String DEFAULT_REALTIME_MODEL = "gpt-realtime-mini";
    private static final List<String> DEFAULT_FAMILIES = List.of(
            CHAT_COMPLETIONS,
            RESPONSES,
            FILES,
            VECTOR_STORES,
            REALTIME_CLIENT_SECRET
    );

    private final ObjectMapper objectMapper;

    OpenAiDirectResourceSmokeHttpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    List<String> normalizeFamilies(List<String> requestedFamilies) {
        if (requestedFamilies == null || requestedFamilies.isEmpty()) {
            return DEFAULT_FAMILIES;
        }
        return requestedFamilies.stream()
                .map(this::normalizeFamily)
                .filter(value -> value != null && DEFAULT_FAMILIES.contains(value))
                .distinct()
                .toList();
    }

    OpenAiDirectResourceSmokeItemResponse dryRunItem(
            String family,
            String requestedBaseUrl,
            String organization,
            String project) {
        ProbePlan plan = probePlan(family, requestedBaseUrl);
        return new OpenAiDirectResourceSmokeItemResponse(
                family,
                "DRY_RUN_READY",
                "SKIPPED",
                "DRY_RUN",
                plan.method(),
                plan.path(),
                plan.billable(),
                plan.writeOperation(),
                null,
                null,
                null,
                null,
                null,
                Map.of("probeKind", plan.probeKind()),
                requestPreview(plan, organization, project)
        );
    }

    OpenAiDirectResourceSmokeItemResponse blockedItem(
            String family,
            String requestedBaseUrl,
            String organization,
            String project) {
        ProbePlan plan = probePlan(family, requestedBaseUrl);
        String reason = plan.writeOperation() ? "WRITE_PROBE_BLOCKED" : "BILLABLE_PROBE_BLOCKED";
        return new OpenAiDirectResourceSmokeItemResponse(
                family,
                "BUDGET_GUARD_BLOCKED",
                "BUDGET_BLOCKED",
                reason,
                plan.method(),
                plan.path(),
                plan.billable(),
                plan.writeOperation(),
                null,
                null,
                null,
                null,
                null,
                Map.of("probeKind", plan.probeKind(), "guard", reason),
                requestPreview(plan, organization, project)
        );
    }

    OpenAiDirectResourceSmokeItemResponse executeReadOnlyProbe(
            String family,
            String secret,
            String requestedBaseUrl,
            Integer requestedTimeoutSeconds,
            String organization,
            String project) {
        return executeProbe(family, secret, requestedBaseUrl, requestedTimeoutSeconds, organization, project, false, false);
    }

    OpenAiDirectResourceSmokeItemResponse executeProbe(
            String family,
            String secret,
            String requestedBaseUrl,
            Integer requestedTimeoutSeconds,
            String organization,
            String project,
            boolean allowBillableProbes,
            boolean allowWriteProbes) {
        ProbePlan plan = probePlan(family, requestedBaseUrl);
        if (plan.billable() && !allowBillableProbes || plan.writeOperation() && !allowWriteProbes) {
            return blockedItem(family, requestedBaseUrl, organization, project);
        }
        int timeoutSeconds = requestedTimeoutSeconds == null || requestedTimeoutSeconds <= 0
                ? 10
                : Math.min(30, Math.max(3, requestedTimeoutSeconds));
        long startedNanos = System.nanoTime();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                    .build();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(plan.url()))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("authorization", "Bearer " + secret)
                    .header("accept", "application/json");
            String requestBody = requestBody(plan);
            if (!isBlank(organization)) {
                builder.header("OpenAI-Organization", organization.trim());
            }
            if (!isBlank(project)) {
                builder.header("OpenAI-Project", project.trim());
            }
            HttpRequest httpRequest = "GET".equals(plan.method())
                    ? builder.GET().build()
                    : builder.header("content-type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                            .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
            Map<String, Object> body = readJsonMap(response.body());
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            String failureType = success ? null : failureType(response.statusCode(), body, response.body());
            return new OpenAiDirectResourceSmokeItemResponse(
                    family,
                    success ? "LIVE_SMOKE_OK" : "LIVE_SMOKE_FAILED",
                    success ? "PASS" : classification(response.statusCode(), failureType),
                    success ? null : skippedReason(response.statusCode(), failureType),
                    plan.method(),
                    plan.path(),
                    plan.billable(),
                    plan.writeOperation(),
                    response.statusCode(),
                    responseRequestId(response),
                    durationMs,
                    failureType,
                    success ? null : sanitizeFailureMessage(failureMessage(body, response.body()), secret),
                    evidence(body),
                    requestPreview(plan, organization, project)
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failedItem(family, plan, "INTERRUPTED", "OpenAI Direct resource smoke 被中断。", startedNanos, organization, project);
        } catch (IOException | IllegalArgumentException exception) {
            return failedItem(family, plan, exception.getClass().getSimpleName(), exception.getMessage(), startedNanos, organization, project);
        }
    }

    private OpenAiDirectResourceSmokeItemResponse failedItem(
            String family,
            ProbePlan plan,
            String failureType,
            String message,
            long startedNanos,
            String organization,
            String project) {
        long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
        return new OpenAiDirectResourceSmokeItemResponse(
                family,
                "LIVE_SMOKE_FAILED",
                "FAIL",
                null,
                plan.method(),
                plan.path(),
                plan.billable(),
                plan.writeOperation(),
                null,
                null,
                durationMs,
                failureType,
                truncate(message, 240),
                Map.of("probeKind", plan.probeKind()),
                requestPreview(plan, organization, project)
        );
    }

    private ProbePlan probePlan(String family, String requestedBaseUrl) {
        String baseUrl = normalizeBaseUrl(requestedBaseUrl);
        if (baseUrl == null) {
            baseUrl = DEFAULT_OPENAI_BASE_URL;
        }
        String normalizedBase = normalizeOpenAiBase(baseUrl);
        return switch (family) {
            case CHAT_COMPLETIONS -> plan(normalizedBase, "POST", "/v1/chat/completions", "chat_billable_generation", true, false);
            case RESPONSES -> plan(normalizedBase, "POST", "/v1/responses", "responses_billable_generation", true, false);
            case FILES -> plan(normalizedBase, "GET", "/v1/files?limit=1", "read_only_list", false, false);
            case VECTOR_STORES -> plan(normalizedBase, "GET", "/v1/vector_stores?limit=1", "read_only_list", false, false);
            case REALTIME_CLIENT_SECRET -> plan(normalizedBase, "POST", "/v1/realtime/client_secrets", "write_client_secret", false, true);
            default -> throw new IllegalArgumentException("未知 OpenAI resource smoke family：" + family);
        };
    }

    private ProbePlan plan(
            String baseUrl,
            String method,
            String path,
            String probeKind,
            boolean billable,
            boolean writeOperation) {
        return new ProbePlan(
                baseUrl,
                method,
                path,
                baseUrl + path,
                probeKind,
                billable,
                writeOperation
        );
    }

    private String normalizeFamily(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }

    private String normalizeBaseUrl(String requestedBaseUrl) {
        if (requestedBaseUrl == null || requestedBaseUrl.isBlank()) {
            return null;
        }
        String trimmed = requestedBaseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String normalizeOpenAiBase(String baseUrl) {
        if (baseUrl.endsWith("/v1")) {
            return baseUrl.substring(0, baseUrl.length() - "/v1".length());
        }
        return baseUrl;
    }

    private Map<String, Object> requestPreview(ProbePlan plan, String organization, String project) {
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
        preview.put("method", plan.method());
        preview.put("baseUrl", plan.baseUrl());
        preview.put("path", plan.path());
        preview.put("headers", headers);
        preview.put("body", previewBody(plan));
        return preview;
    }

    private Map<String, Object> evidence(Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("object", body.get("object"));
        Object id = body.get("id");
        if (!isBlank(text(id))) {
            result.put("id", id);
        }
        Object model = body.get("model");
        if (!isBlank(text(model))) {
            result.put("model", model);
        }
        Object usage = body.get("usage");
        if (usage instanceof Map<?, ?> usageMap) {
            result.put("usageFields", usageMap.keySet().stream().map(String::valueOf).toList());
        }
        Object clientSecret = body.get("client_secret");
        if (clientSecret instanceof Map<?, ?>) {
            result.put("clientSecretReturned", true);
        }
        Object data = body.get("data");
        if (data instanceof List<?> list) {
            result.put("itemsSeen", list.size());
        }
        String firstId = firstDataId(data);
        if (!isBlank(firstId)) {
            result.put("firstId", firstId);
        }
        return result;
    }

    private String requestBody(ProbePlan plan) {
        Map<String, Object> body = previewBody(plan);
        if (body == null || body.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JacksonException exception) {
            return "{}";
        }
    }

    private Map<String, Object> previewBody(ProbePlan plan) {
        return switch (plan.probeKind()) {
            case "chat_billable_generation" -> Map.of(
                    "model", DEFAULT_GENERATION_MODEL,
                    "messages", List.of(Map.of("role", "user", "content", "ping")),
                    "max_completion_tokens", 1,
                    "store", false
            );
            case "responses_billable_generation" -> Map.of(
                    "model", DEFAULT_GENERATION_MODEL,
                    "input", "ping",
                    "max_output_tokens", 1,
                    "store", false
            );
            case "write_client_secret" -> Map.of(
                    "expires_after", Map.of(
                            "anchor", "created_at",
                            "seconds", 60
                    ),
                    "session", Map.of(
                            "type", "realtime",
                            "model", DEFAULT_REALTIME_MODEL,
                            "output_modalities", List.of("text"),
                            "max_output_tokens", 1,
                            "instructions", "Smoke probe."
                    )
            );
            default -> null;
        };
    }

    private String firstDataId(Object data) {
        if (!(data instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        Object first = list.getFirst();
        if (first instanceof Map<?, ?> map) {
            return text(map.get("id"));
        }
        return null;
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

    private String classification(Integer statusCode, String failureType) {
        if (statusCode != null && (statusCode == 401 || statusCode == 403)) {
            return "NO_PERMISSION";
        }
        if (statusCode != null && statusCode == 404) {
            return "UNSUPPORTED";
        }
        if (statusCode != null && statusCode == 429) {
            return "BUDGET_BLOCKED";
        }
        String upper = upper(failureType);
        if (upper.contains("AUTH") || upper.contains("PERMISSION") || upper.contains("UNAUTHORIZED") || upper.contains("FORBIDDEN")) {
            return "NO_PERMISSION";
        }
        if (upper.contains("RATE") || upper.contains("LIMIT") || upper.contains("QUOTA") || upper.contains("BUDGET")) {
            return "BUDGET_BLOCKED";
        }
        return "FAIL";
    }

    private String skippedReason(Integer statusCode, String failureType) {
        String classification = classification(statusCode, failureType);
        return switch (classification) {
            case "NO_PERMISSION", "BUDGET_BLOCKED", "UNSUPPORTED" -> failureType;
            default -> null;
        };
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

    private String responseRequestId(HttpResponse<?> response) {
        return firstNonBlank(
                response.headers().firstValue("x-request-id").orElse(null),
                response.headers().firstValue("request-id").orElse(null),
                response.headers().firstValue("cf-ray").orElse(null)
        );
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

    private String upper(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record ProbePlan(
            String baseUrl,
            String method,
            String path,
            String url,
            String probeKind,
            boolean billable,
            boolean writeOperation) {
    }
}
