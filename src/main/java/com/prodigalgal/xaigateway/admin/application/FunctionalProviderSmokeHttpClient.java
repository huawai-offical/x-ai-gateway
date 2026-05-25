package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.FunctionalProviderSmokeItemResponse;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
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

class FunctionalProviderSmokeHttpClient {

    static final String PROTOCOL_GEMINI_NATIVE = "GEMINI_NATIVE";
    static final String PROTOCOL_OPENAI_COMPATIBLE = "XIAOMI_MIMO_OPENAI_COMPATIBLE";
    static final String PROTOCOL_ANTHROPIC_COMPATIBLE = "XIAOMI_MIMO_ANTHROPIC_COMPATIBLE";
    static final String PROTOCOL_DEEPSEEK_OPENAI_COMPATIBLE = "DEEPSEEK_OPENAI_COMPATIBLE";
    static final String PROTOCOL_XAI_OPENAI_COMPATIBLE = "XAI_OPENAI_COMPATIBLE";
    static final String PROTOCOL_QWEN_OPENAI_COMPATIBLE = "QWEN_OPENAI_COMPATIBLE";
    static final String PROTOCOL_MOONSHOT_OPENAI_COMPATIBLE = "MOONSHOT_OPENAI_COMPATIBLE";
    static final String PROTOCOL_VOLCENGINE_OPENAI_COMPATIBLE = "VOLCENGINE_OPENAI_COMPATIBLE";
    static final String PROTOCOL_MINIMAX_OPENAI_COMPATIBLE = "MINIMAX_OPENAI_COMPATIBLE";
    static final String PROTOCOL_MISTRAL_OPENAI_COMPATIBLE = "MISTRAL_OPENAI_COMPATIBLE";
    static final String PROTOCOL_PERPLEXITY_OPENAI_COMPATIBLE = "PERPLEXITY_OPENAI_COMPATIBLE";
    static final String PROTOCOL_COHERE_NATIVE = "COHERE_NATIVE";
    static final String PROTOCOL_JINA_NATIVE = "JINA_NATIVE";
    private static final String PROTOCOL_OPENAI_COMPATIBLE_GENERIC = "OPENAI_COMPATIBLE";
    private static final String PROTOCOL_ANTHROPIC_COMPATIBLE_GENERIC = "ANTHROPIC_COMPATIBLE";

    static final String GENERATE_CONTENT = "GENERATE_CONTENT";
    static final String STREAM_GENERATE_CONTENT = "STREAM_GENERATE_CONTENT";
    static final String TOOL_CALLING = "TOOL_CALLING";
    static final String CHAT_COMPLETIONS = "CHAT_COMPLETIONS";
    static final String CHAT_STREAMING = "CHAT_STREAMING";
    static final String CHAT_TOOLS = "CHAT_TOOLS";
    static final String MESSAGES = "MESSAGES";
    static final String MESSAGES_STREAMING = "MESSAGES_STREAMING";
    static final String TOOL_USE = "TOOL_USE";
    static final String EMBEDDINGS = "EMBEDDINGS";
    static final String RERANK = "RERANK";

    private static final String DEFAULT_GEMINI_BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String DEFAULT_MIMO_OPENAI_BASE_URL = "https://token-plan-sgp.xiaomimimo.com/v1";
    private static final String DEFAULT_MIMO_ANTHROPIC_BASE_URL = "https://token-plan-sgp.xiaomimimo.com/anthropic";
    private static final String DEFAULT_DEEPSEEK_BASE_URL = "https://api.deepseek.com";
    private static final String DEFAULT_XAI_BASE_URL = "https://api.x.ai/v1";
    private static final String DEFAULT_QWEN_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private static final String DEFAULT_MOONSHOT_BASE_URL = "https://api.moonshot.cn/v1";
    private static final String DEFAULT_VOLCENGINE_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3";
    private static final String DEFAULT_MINIMAX_BASE_URL = "https://api.minimax.chat/v1";
    private static final String DEFAULT_MISTRAL_BASE_URL = "https://api.mistral.ai/v1";
    private static final String DEFAULT_PERPLEXITY_BASE_URL = "https://api.perplexity.ai";
    private static final String DEFAULT_COHERE_BASE_URL = "https://api.cohere.ai";
    private static final String DEFAULT_JINA_BASE_URL = "https://api.jina.ai/v1";
    private static final String DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";
    private static final String DEFAULT_MIMO_MODEL = "mimo-v2-pro";
    private static final String DEFAULT_DEEPSEEK_MODEL = "deepseek-chat";
    private static final String DEFAULT_XAI_MODEL = "grok-4.3";
    private static final String DEFAULT_QWEN_MODEL = "qwen-plus";
    private static final String DEFAULT_MOONSHOT_MODEL = "moonshot-v1-8k";
    private static final String DEFAULT_VOLCENGINE_MODEL = "doubao-seed-1-6";
    private static final String DEFAULT_MINIMAX_MODEL = "abab6.5s-chat";
    private static final String DEFAULT_MISTRAL_MODEL = "mistral-large-latest";
    private static final String DEFAULT_PERPLEXITY_MODEL = "sonar";
    private static final String DEFAULT_COHERE_MODEL = "embed-v4.0";
    private static final String DEFAULT_JINA_MODEL = "jina-embeddings-v3";

    private static final List<String> GEMINI_DEFAULT_FAMILIES = List.of(
            GENERATE_CONTENT,
            STREAM_GENERATE_CONTENT,
            TOOL_CALLING
    );
    private static final List<String> OPENAI_COMPAT_DEFAULT_FAMILIES = List.of(
            CHAT_COMPLETIONS,
            CHAT_STREAMING,
            CHAT_TOOLS
    );
    private static final List<String> ANTHROPIC_COMPAT_DEFAULT_FAMILIES = List.of(
            MESSAGES,
            MESSAGES_STREAMING,
            TOOL_USE
    );
    private static final List<String> EMBED_RERANK_DEFAULT_FAMILIES = List.of(
            EMBEDDINGS,
            RERANK
    );

    private final ObjectMapper objectMapper;

    FunctionalProviderSmokeHttpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String resolveProtocol(ProviderType providerType, String requestedProtocol, String requestedBaseUrl) {
        String protocol = normalizeProtocol(requestedProtocol);
        if (!isBlank(protocol)) {
        if (PROTOCOL_OPENAI_COMPATIBLE_GENERIC.equals(protocol)) {
            if (isCohereBaseUrl(requestedBaseUrl)) {
                return PROTOCOL_COHERE_NATIVE;
                }
                if (isJinaBaseUrl(requestedBaseUrl)) {
                    return PROTOCOL_JINA_NATIVE;
                }
                if (isDeepSeekBaseUrl(requestedBaseUrl)) {
                    return PROTOCOL_DEEPSEEK_OPENAI_COMPATIBLE;
                }
                if (isXaiBaseUrl(requestedBaseUrl)) {
                    return PROTOCOL_XAI_OPENAI_COMPATIBLE;
                }
                if (isQwenBaseUrl(requestedBaseUrl)) {
                    return PROTOCOL_QWEN_OPENAI_COMPATIBLE;
                }
                if (isMoonshotBaseUrl(requestedBaseUrl)) {
                    return PROTOCOL_MOONSHOT_OPENAI_COMPATIBLE;
                }
                if (isVolcengineBaseUrl(requestedBaseUrl)) {
                    return PROTOCOL_VOLCENGINE_OPENAI_COMPATIBLE;
                }
                if (isMiniMaxBaseUrl(requestedBaseUrl)) {
                    return PROTOCOL_MINIMAX_OPENAI_COMPATIBLE;
                }
                if (isMistralBaseUrl(requestedBaseUrl)) {
                    return PROTOCOL_MISTRAL_OPENAI_COMPATIBLE;
                }
                if (isPerplexityBaseUrl(requestedBaseUrl)) {
                    return PROTOCOL_PERPLEXITY_OPENAI_COMPATIBLE;
                }
                return isMimoBaseUrl(requestedBaseUrl) || (isBlank(requestedBaseUrl) && providerType == ProviderType.OPENAI_COMPATIBLE)
                        ? PROTOCOL_OPENAI_COMPATIBLE
                        : PROTOCOL_OPENAI_COMPATIBLE_GENERIC;
            }
            if (PROTOCOL_ANTHROPIC_COMPATIBLE_GENERIC.equals(protocol)) {
                return isMimoBaseUrl(requestedBaseUrl) || (isBlank(requestedBaseUrl) && providerType == ProviderType.ANTHROPIC_DIRECT)
                        ? PROTOCOL_ANTHROPIC_COMPATIBLE
                        : PROTOCOL_ANTHROPIC_COMPATIBLE_GENERIC;
            }
            return protocol;
        }
        if (providerType == ProviderType.GEMINI_DIRECT) {
            return PROTOCOL_GEMINI_NATIVE;
        }
        String baseUrl = requestedBaseUrl == null ? "" : requestedBaseUrl.toLowerCase(Locale.ROOT);
        if (baseUrl.contains("generativelanguage.googleapis.com")) {
            return PROTOCOL_GEMINI_NATIVE;
        }
        if (isCohereBaseUrl(baseUrl)) {
            return PROTOCOL_COHERE_NATIVE;
        }
        if (isJinaBaseUrl(baseUrl)) {
            return PROTOCOL_JINA_NATIVE;
        }
        if (isMimoBaseUrl(baseUrl)) {
            return baseUrl.contains("/anthropic") ? PROTOCOL_ANTHROPIC_COMPATIBLE : PROTOCOL_OPENAI_COMPATIBLE;
        }
        if (isDeepSeekBaseUrl(baseUrl)) {
            return PROTOCOL_DEEPSEEK_OPENAI_COMPATIBLE;
        }
        if (isXaiBaseUrl(baseUrl)) {
            return PROTOCOL_XAI_OPENAI_COMPATIBLE;
        }
        if (isQwenBaseUrl(baseUrl)) {
            return PROTOCOL_QWEN_OPENAI_COMPATIBLE;
        }
        if (isMoonshotBaseUrl(baseUrl)) {
            return PROTOCOL_MOONSHOT_OPENAI_COMPATIBLE;
        }
        if (isVolcengineBaseUrl(baseUrl)) {
            return PROTOCOL_VOLCENGINE_OPENAI_COMPATIBLE;
        }
        if (isMiniMaxBaseUrl(baseUrl)) {
            return PROTOCOL_MINIMAX_OPENAI_COMPATIBLE;
        }
        if (isMistralBaseUrl(baseUrl)) {
            return PROTOCOL_MISTRAL_OPENAI_COMPATIBLE;
        }
        if (isPerplexityBaseUrl(baseUrl)) {
            return PROTOCOL_PERPLEXITY_OPENAI_COMPATIBLE;
        }
        if (!baseUrl.isBlank() && baseUrl.contains("/anthropic")) {
            return PROTOCOL_ANTHROPIC_COMPATIBLE_GENERIC;
        }
        if (!baseUrl.isBlank() && providerType == ProviderType.OPENAI_COMPATIBLE) {
            return PROTOCOL_OPENAI_COMPATIBLE_GENERIC;
        }
        if (!baseUrl.isBlank() && providerType == ProviderType.ANTHROPIC_DIRECT) {
            return PROTOCOL_ANTHROPIC_COMPATIBLE_GENERIC;
        }
        if (providerType == ProviderType.OPENAI_COMPATIBLE) {
            return PROTOCOL_OPENAI_COMPATIBLE;
        }
        if (providerType == ProviderType.ANTHROPIC_DIRECT) {
            return PROTOCOL_ANTHROPIC_COMPATIBLE;
        }
        return PROTOCOL_OPENAI_COMPATIBLE;
    }

    List<String> normalizeFamilies(ProviderType providerType, String protocol, List<String> requestedFamilies) {
        if (requestedFamilies == null || requestedFamilies.isEmpty()) {
            return defaultFamilies(providerType, protocol);
        }
        return requestedFamilies.stream()
                .map(value -> normalizeFamily(value, protocol))
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    FunctionalProviderSmokeItemResponse dryRunItem(
            ProviderType providerType,
            String protocol,
            String family,
            String requestedBaseUrl,
            String requestedModel) {
        ProbePlan plan = probePlan(providerType, protocol, family, requestedBaseUrl, requestedModel);
        if (plan.unsupported()) {
            return unsupportedItem(providerType, protocol, family, plan);
        }
        return new FunctionalProviderSmokeItemResponse(
                providerType,
                protocol,
                family,
                "DRY_RUN_READY",
                "SKIPPED",
                "DRY_RUN",
                plan.method(),
                plan.path(),
                plan.model(),
                plan.billable(),
                plan.writeOperation(),
                null,
                null,
                null,
                null,
                null,
                Map.of("probeKind", plan.probeKind()),
                requestPreview(plan)
        );
    }

    FunctionalProviderSmokeItemResponse liveGuardItem(
            ProviderType providerType,
            String protocol,
            String family,
            String requestedBaseUrl,
            String requestedModel) {
        ProbePlan plan = probePlan(providerType, protocol, family, requestedBaseUrl, requestedModel);
        if (plan.unsupported()) {
            return unsupportedItem(providerType, protocol, family, plan);
        }
        return new FunctionalProviderSmokeItemResponse(
                providerType,
                protocol,
                family,
                "LIVE_GUARD_BLOCKED",
                "SKIPPED",
                "LIVE_NOT_ALLOWED",
                plan.method(),
                plan.path(),
                plan.model(),
                plan.billable(),
                plan.writeOperation(),
                null,
                null,
                null,
                null,
                null,
                Map.of("probeKind", plan.probeKind(), "guard", "LIVE_NOT_ALLOWED"),
                requestPreview(plan)
        );
    }

    FunctionalProviderSmokeItemResponse routeBlockedItem(
            ProviderType providerType,
            String protocol,
            String family,
            String requestedBaseUrl,
            String requestedModel,
            String routeBlockReason) {
        ProbePlan plan = probePlan(providerType, protocol, family, requestedBaseUrl, requestedModel);
        String classification = "PROVIDER_NOT_FUNCTIONAL_SMOKE_COMPATIBLE".equals(routeBlockReason)
                ? "UNSUPPORTED"
                : ("CREDENTIAL_COOLDOWN".equals(routeBlockReason) ? "BUDGET_BLOCKED" : "SKIPPED");
        return new FunctionalProviderSmokeItemResponse(
                providerType,
                protocol,
                family,
                "ROUTE_BLOCKED",
                classification,
                routeBlockReason,
                plan.method(),
                plan.path(),
                plan.model(),
                plan.billable(),
                plan.writeOperation(),
                null,
                null,
                null,
                null,
                null,
                Map.of("routeBlockReason", routeBlockReason),
                requestPreview(plan)
        );
    }

    FunctionalProviderSmokeItemResponse executeProbe(
            ProviderType providerType,
            String protocol,
            String family,
            String secret,
            String requestedBaseUrl,
            String requestedModel,
            Integer requestedTimeoutSeconds,
            boolean allowBillableProbes) {
        ProbePlan plan = probePlan(providerType, protocol, family, requestedBaseUrl, requestedModel);
        if (plan.unsupported()) {
            return unsupportedItem(providerType, protocol, family, plan);
        }
        if (plan.billable() && !allowBillableProbes) {
            return blockedItem(providerType, protocol, family, plan);
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
                    .header("accept", "application/json");
            authHeaders(plan, secret).forEach(builder::header);
            String requestBody = requestBody(plan);
            HttpRequest httpRequest = builder
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
            Map<String, Object> body = readJsonMap(response.body());
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            String failureType = success ? null : failureType(response.statusCode(), body, response.body());
            return new FunctionalProviderSmokeItemResponse(
                    providerType,
                    protocol,
                    family,
                    success ? "LIVE_SMOKE_OK" : "LIVE_SMOKE_FAILED",
                    success ? "PASS" : classification(response.statusCode(), failureType),
                    success ? null : skippedReason(response.statusCode(), failureType),
                    plan.method(),
                    plan.path(),
                    plan.model(),
                    plan.billable(),
                    plan.writeOperation(),
                    response.statusCode(),
                    responseRequestId(response),
                    durationMs,
                    failureType,
                    success ? null : sanitizeFailureMessage(failureMessage(body, response.body()), secret),
                    evidence(body),
                    requestPreview(plan)
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failedItem(providerType, protocol, family, plan, "INTERRUPTED", "功能性 provider smoke 被中断。", startedNanos);
        } catch (IOException | IllegalArgumentException exception) {
            return failedItem(providerType, protocol, family, plan, exception.getClass().getSimpleName(), exception.getMessage(), startedNanos);
        }
    }

    private FunctionalProviderSmokeItemResponse unsupportedItem(
            ProviderType providerType,
            String protocol,
            String family,
            ProbePlan plan) {
        return new FunctionalProviderSmokeItemResponse(
                providerType,
                protocol,
                family,
                "OUT_OF_SCOPE",
                "UNSUPPORTED",
                "OUT_OF_FUNCTIONAL_API_SCOPE",
                plan.method(),
                plan.path(),
                plan.model(),
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                Map.of(
                        "reason", plan.unsupportedReason(),
                        "supportedFamilies", defaultFamilies(providerType, protocol)
                ),
                requestPreview(plan)
        );
    }

    private FunctionalProviderSmokeItemResponse blockedItem(
            ProviderType providerType,
            String protocol,
            String family,
            ProbePlan plan) {
        return new FunctionalProviderSmokeItemResponse(
                providerType,
                protocol,
                family,
                "BUDGET_GUARD_BLOCKED",
                "BUDGET_BLOCKED",
                "BILLABLE_PROBE_BLOCKED",
                plan.method(),
                plan.path(),
                plan.model(),
                plan.billable(),
                plan.writeOperation(),
                null,
                null,
                null,
                null,
                null,
                Map.of("probeKind", plan.probeKind(), "guard", "BILLABLE_PROBE_BLOCKED"),
                requestPreview(plan)
        );
    }

    private FunctionalProviderSmokeItemResponse failedItem(
            ProviderType providerType,
            String protocol,
            String family,
            ProbePlan plan,
            String failureType,
            String message,
            long startedNanos) {
        long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
        return new FunctionalProviderSmokeItemResponse(
                providerType,
                protocol,
                family,
                "LIVE_SMOKE_FAILED",
                "FAIL",
                null,
                plan.method(),
                plan.path(),
                plan.model(),
                plan.billable(),
                plan.writeOperation(),
                null,
                null,
                durationMs,
                failureType,
                truncate(message, 240),
                Map.of("probeKind", plan.probeKind()),
                requestPreview(plan)
        );
    }

    private ProbePlan probePlan(
            ProviderType providerType,
            String protocol,
            String family,
            String requestedBaseUrl,
            String requestedModel) {
        if (!List.of(
                PROTOCOL_GEMINI_NATIVE,
                PROTOCOL_OPENAI_COMPATIBLE,
                PROTOCOL_ANTHROPIC_COMPATIBLE,
                PROTOCOL_DEEPSEEK_OPENAI_COMPATIBLE,
                PROTOCOL_XAI_OPENAI_COMPATIBLE,
                PROTOCOL_QWEN_OPENAI_COMPATIBLE,
                PROTOCOL_MOONSHOT_OPENAI_COMPATIBLE,
                PROTOCOL_VOLCENGINE_OPENAI_COMPATIBLE,
                PROTOCOL_MINIMAX_OPENAI_COMPATIBLE,
                PROTOCOL_MISTRAL_OPENAI_COMPATIBLE,
                PROTOCOL_PERPLEXITY_OPENAI_COMPATIBLE,
                PROTOCOL_COHERE_NATIVE,
                PROTOCOL_JINA_NATIVE,
                PROTOCOL_OPENAI_COMPATIBLE_GENERIC,
                PROTOCOL_ANTHROPIC_COMPATIBLE_GENERIC
        ).contains(protocol)) {
            return unsupportedPlan(providerType, protocol, family, requestedBaseUrl, requestedModel, "UNSUPPORTED_PROTOCOL");
        }
        String baseUrl = normalizeBaseUrl(firstNonBlank(requestedBaseUrl, defaultBaseUrl(protocol)));
        String model = firstNonBlank(requestedModel, defaultModel(protocol));
        if (PROTOCOL_GEMINI_NATIVE.equals(protocol)) {
            String normalizedBase = removeSuffix(baseUrl, "/v1beta");
            String modelPath = model.startsWith("models/") ? model : "models/" + model;
            if (GENERATE_CONTENT.equals(family)) {
                return plan(providerType, protocol, family, normalizedBase, "POST",
                        "/v1beta/" + modelPath + ":generateContent", "gemini_generate_content", model, true, false);
            }
            if (STREAM_GENERATE_CONTENT.equals(family)) {
                return plan(providerType, protocol, family, normalizedBase, "POST",
                        "/v1beta/" + modelPath + ":streamGenerateContent", "gemini_stream_generate_content", model, true, false);
            }
            if (TOOL_CALLING.equals(family)) {
                return plan(providerType, protocol, family, normalizedBase, "POST",
                        "/v1beta/" + modelPath + ":generateContent", "gemini_tool_calling", model, true, false);
            }
            return unsupportedPlan(providerType, protocol, family, normalizedBase, model, "UNSUPPORTED_FAMILY");
        }
        if (PROTOCOL_COHERE_NATIVE.equals(protocol)) {
            String normalizedBase = removeSuffix(baseUrl, "/compatibility/v1");
            normalizedBase = removeSuffix(normalizedBase, "/v1");
            normalizedBase = removeSuffix(normalizedBase, "/v2");
            if (EMBEDDINGS.equals(family)) {
                return plan(providerType, protocol, family, normalizedBase, "POST",
                        "/v2/embed", "cohere_native_embed", model, true, false);
            }
            if (RERANK.equals(family)) {
                return plan(providerType, protocol, family, normalizedBase, "POST",
                        "/v2/rerank", "cohere_native_rerank", firstNonBlank(requestedModel, "rerank-v3.5"), true, false);
            }
            return unsupportedPlan(providerType, protocol, family, normalizedBase, model, "UNSUPPORTED_FAMILY");
        }
        if (PROTOCOL_JINA_NATIVE.equals(protocol)) {
            String normalizedBase = removeSuffix(baseUrl, "/v1");
            if (EMBEDDINGS.equals(family)) {
                return plan(providerType, protocol, family, normalizedBase, "POST",
                        "/v1/embeddings", "jina_native_embeddings", model, true, false);
            }
            if (RERANK.equals(family)) {
                return plan(providerType, protocol, family, normalizedBase, "POST",
                        "/v1/rerank", "jina_native_rerank", firstNonBlank(requestedModel, "jina-reranker-v2-base-multilingual"), true, false);
            }
            return unsupportedPlan(providerType, protocol, family, normalizedBase, model, "UNSUPPORTED_FAMILY");
        }
        if (isOpenAiCompatibleProtocol(protocol)) {
            String normalizedBase = normalizeOpenAiCompatibleBaseUrl(protocol, baseUrl);
            if (CHAT_COMPLETIONS.equals(family)) {
                return plan(providerType, protocol, family, normalizedBase, "POST",
                        openAiCompatibleChatPath(protocol), "openai_compatible_chat", model, true, false);
            }
            if (CHAT_STREAMING.equals(family)) {
                return plan(providerType, protocol, family, normalizedBase, "POST",
                        openAiCompatibleChatPath(protocol), "openai_compatible_chat_streaming", model, true, false);
            }
            if (CHAT_TOOLS.equals(family)) {
                return plan(providerType, protocol, family, normalizedBase, "POST",
                        openAiCompatibleChatPath(protocol), "openai_compatible_chat_tools", model, true, false);
            }
            return unsupportedPlan(providerType, protocol, family, normalizedBase, model, "UNSUPPORTED_FAMILY");
        }
        if (isAnthropicCompatibleProtocol(protocol)) {
            if (MESSAGES.equals(family)) {
                return plan(providerType, protocol, family, baseUrl, "POST",
                        "/v1/messages", "anthropic_compatible_messages", model, true, false);
            }
            if (MESSAGES_STREAMING.equals(family)) {
                return plan(providerType, protocol, family, baseUrl, "POST",
                        "/v1/messages", "anthropic_compatible_messages_streaming", model, true, false);
            }
            if (TOOL_USE.equals(family)) {
                return plan(providerType, protocol, family, baseUrl, "POST",
                        "/v1/messages", "anthropic_compatible_tool_use", model, true, false);
            }
            return unsupportedPlan(providerType, protocol, family, baseUrl, model, "UNSUPPORTED_FAMILY");
        }
        return unsupportedPlan(providerType, protocol, family, baseUrl, model, "UNSUPPORTED_PROTOCOL");
    }

    private ProbePlan plan(
            ProviderType providerType,
            String protocol,
            String family,
            String baseUrl,
            String method,
            String path,
            String probeKind,
            String model,
            boolean billable,
            boolean writeOperation) {
        return new ProbePlan(
                providerType,
                protocol,
                family,
                baseUrl,
                method,
                path,
                baseUrl + path,
                probeKind,
                model,
                billable,
                writeOperation,
                false,
                null
        );
    }

    private ProbePlan unsupportedPlan(
            ProviderType providerType,
            String protocol,
            String family,
            String baseUrl,
            String model,
            String reason) {
        return new ProbePlan(
                providerType,
                protocol,
                family,
                normalizeBaseUrl(firstNonBlank(baseUrl, defaultBaseUrl(protocol))),
                null,
                null,
                null,
                "unsupported",
                firstNonBlank(model, defaultModel(protocol)),
                false,
                false,
                true,
                reason
        );
    }

    private Map<String, String> authHeaders(ProbePlan plan, String secret) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (PROTOCOL_GEMINI_NATIVE.equals(plan.protocol())) {
            headers.put("x-goog-api-key", secret);
            return headers;
        }
        if (isAnthropicCompatibleProtocol(plan.protocol())) {
            headers.put("anthropic-version", "2023-06-01");
            headers.put("api-key", secret);
            return headers;
        }
        if (usesBearerOpenAiCompatibleAuth(plan.protocol())) {
            headers.put("authorization", "Bearer " + secret);
            return headers;
        }
        if (isOpenAiCompatibleProtocol(plan.protocol())) {
            headers.put("api-key", secret);
            return headers;
        }
        headers.put("authorization", "Bearer " + secret);
        return headers;
    }

    private Map<String, Object> requestPreview(ProbePlan plan) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("accept", "application/json");
        if (!plan.unsupported()) {
            headers.put("content-type", "application/json");
        }
        if (PROTOCOL_GEMINI_NATIVE.equals(plan.protocol())) {
            headers.put("x-goog-api-key", "***");
        } else if (isAnthropicCompatibleProtocol(plan.protocol())) {
            headers.put("anthropic-version", "2023-06-01");
            headers.put("api-key", "***");
        } else if (usesBearerOpenAiCompatibleAuth(plan.protocol())) {
            headers.put("authorization", "Bearer ***");
        } else if (isOpenAiCompatibleProtocol(plan.protocol())) {
            headers.put("api-key", "***");
        } else {
            headers.put("authorization", "Bearer ***");
        }
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("providerType", plan.providerType() == null ? null : plan.providerType().name());
        preview.put("protocol", plan.protocol());
        preview.put("method", plan.method());
        preview.put("baseUrl", plan.baseUrl());
        preview.put("path", plan.path());
        preview.put("model", plan.model());
        preview.put("headers", headers);
        preview.put("body", plan.unsupported() ? null : previewBody(plan));
        return preview;
    }

    private String requestBody(ProbePlan plan) {
        try {
            return objectMapper.writeValueAsString(previewBody(plan));
        } catch (JacksonException exception) {
            return "{}";
        }
    }

    private Map<String, Object> previewBody(ProbePlan plan) {
        return switch (plan.probeKind()) {
            case "gemini_generate_content", "gemini_stream_generate_content" -> Map.of(
                    "contents", List.of(Map.of(
                            "role", "user",
                            "parts", List.of(Map.of("text", "ping"))
                    )),
                    "generationConfig", Map.of("maxOutputTokens", 1)
            );
            case "gemini_tool_calling" -> Map.of(
                    "contents", List.of(Map.of(
                            "role", "user",
                            "parts", List.of(Map.of("text", "call the ping tool"))
                    )),
                    "tools", List.of(Map.of("functionDeclarations", List.of(Map.of(
                            "name", "ping",
                            "description", "Return pong.",
                            "parameters", Map.of(
                                    "type", "object",
                                    "properties", Map.of("value", Map.of("type", "string")),
                                    "required", List.of("value")
                            )
                    )))),
                    "generationConfig", Map.of("maxOutputTokens", 1)
            );
            case "openai_compatible_chat" -> Map.of(
                    "model", plan.model(),
                    "messages", List.of(Map.of("role", "user", "content", "ping")),
                    "max_tokens", 1,
                    "stream", false
            );
            case "openai_compatible_chat_streaming" -> Map.of(
                    "model", plan.model(),
                    "messages", List.of(Map.of("role", "user", "content", "ping")),
                    "max_tokens", 1,
                    "stream", true
            );
            case "openai_compatible_chat_tools" -> Map.of(
                    "model", plan.model(),
                    "messages", List.of(Map.of("role", "user", "content", "call the ping tool")),
                    "max_tokens", 1,
                    "tools", List.of(Map.of(
                            "type", "function",
                            "function", Map.of(
                                    "name", "ping",
                                    "description", "Return pong.",
                                    "parameters", Map.of(
                                            "type", "object",
                                            "properties", Map.of("value", Map.of("type", "string")),
                                            "required", List.of("value")
                                    )
                            )
                    ))
            );
            case "anthropic_compatible_messages" -> Map.of(
                    "model", plan.model(),
                    "max_tokens", 1,
                    "messages", List.of(Map.of("role", "user", "content", "ping"))
            );
            case "anthropic_compatible_messages_streaming" -> Map.of(
                    "model", plan.model(),
                    "max_tokens", 1,
                    "stream", true,
                    "messages", List.of(Map.of("role", "user", "content", "ping"))
            );
            case "anthropic_compatible_tool_use" -> Map.of(
                    "model", plan.model(),
                    "max_tokens", 1,
                    "messages", List.of(Map.of("role", "user", "content", "call the ping tool")),
                    "tools", List.of(Map.of(
                            "name", "ping",
                            "description", "Return pong.",
                            "input_schema", Map.of(
                                    "type", "object",
                                    "properties", Map.of("value", Map.of("type", "string")),
                                    "required", List.of("value")
                            )
                    ))
            );
            case "cohere_native_embed" -> Map.of(
                    "model", plan.model(),
                    "texts", List.of("ping"),
                    "input_type", "search_document"
            );
            case "jina_native_embeddings" -> Map.of(
                    "model", plan.model(),
                    "input", List.of("ping")
            );
            case "cohere_native_rerank", "jina_native_rerank" -> Map.of(
                    "model", plan.model(),
                    "query", "ping",
                    "documents", List.of("pong", "other"),
                    "top_n", 1
            );
            default -> Map.of();
        };
    }

    private Map<String, Object> evidence(Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        Object object = body.get("object");
        if (!isBlank(text(object))) {
            result.put("object", object);
        }
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
        Object choices = body.get("choices");
        if (choices instanceof List<?> choicesList) {
            result.put("choicesSeen", choicesList.size());
        }
        Object content = body.get("content");
        if (content instanceof List<?> contentList) {
            result.put("contentBlocksSeen", contentList.size());
        }
        Object candidates = body.get("candidates");
        if (candidates instanceof List<?> candidatesList) {
            result.put("candidatesSeen", candidatesList.size());
        }
        String responseSummary = responseSummary(body);
        if (!isBlank(responseSummary)) {
            result.put("responseSummary", responseSummary);
        }
        Object data = body.get("data");
        if (data instanceof List<?> dataList) {
            result.put("dataSeen", dataList.size());
        }
        Object embeddings = body.get("embeddings");
        if (embeddings instanceof List<?> embeddingsList) {
            result.put("embeddingsSeen", embeddingsList.size());
        } else if (embeddings instanceof Map<?, ?> embeddingsMap) {
            result.put("embeddingFields", embeddingsMap.keySet().stream().map(String::valueOf).toList());
            Object floatVectors = embeddingsMap.get("float");
            if (floatVectors instanceof List<?> floatVectorList) {
                result.put("embeddingFloatVectorsSeen", floatVectorList.size());
            }
        }
        Object results = body.get("results");
        if (results instanceof List<?> resultsList) {
            result.put("resultsSeen", resultsList.size());
            if (!resultsList.isEmpty() && resultsList.getFirst() instanceof Map<?, ?> firstResult) {
                result.put("firstResultFields", firstResult.keySet().stream().map(String::valueOf).toList());
            }
        }
        Object meta = body.get("meta");
        if (meta instanceof Map<?, ?> metaMap) {
            Object billedUnits = metaMap.get("billed_units");
            if (billedUnits instanceof Map<?, ?> billedUnitsMap) {
                result.put("billedUnitFields", billedUnitsMap.keySet().stream().map(String::valueOf).toList());
            }
        }
        if (body.containsKey("_rawText")) {
            result.put("rawTextSeen", true);
        }
        return result;
    }

    private String responseSummary(Map<String, Object> body) {
        Object choices = body.get("choices");
        if (choices instanceof List<?> choicesList
                && !choicesList.isEmpty()
                && choicesList.getFirst() instanceof Map<?, ?> firstChoice) {
            Object message = firstChoice.get("message");
            if (message instanceof Map<?, ?> messageMap) {
                String content = text(messageMap.get("content"));
                if (!isBlank(content)) {
                    return truncate(content, 240);
                }
            }
            String text = text(firstChoice.get("text"));
            if (!isBlank(text)) {
                return truncate(text, 240);
            }
        }
        Object content = body.get("content");
        if (content instanceof List<?> contentList
                && !contentList.isEmpty()
                && contentList.getFirst() instanceof Map<?, ?> firstContent) {
            String text = text(firstContent.get("text"));
            if (!isBlank(text)) {
                return truncate(text, 240);
            }
        }
        Object candidates = body.get("candidates");
        if (candidates instanceof List<?> candidatesList
                && !candidatesList.isEmpty()
                && candidatesList.getFirst() instanceof Map<?, ?> firstCandidate) {
            Object candidateContent = firstCandidate.get("content");
            if (candidateContent instanceof Map<?, ?> candidateContentMap) {
                Object parts = candidateContentMap.get("parts");
                if (parts instanceof List<?> partsList
                        && !partsList.isEmpty()
                        && partsList.getFirst() instanceof Map<?, ?> firstPart) {
                    String text = text(firstPart.get("text"));
                    if (!isBlank(text)) {
                        return truncate(text, 240);
                    }
                }
            }
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
            return Map.of("_rawText", truncate(value, 240));
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
            String type = firstNonBlank(
                    text(errorMap.get("type")),
                    text(errorMap.get("status")),
                    text(errorMap.get("code"))
            );
            if (!isBlank(type)) {
                return type;
            }
        }
        String code = firstNonBlank(text(body.get("code")), text(body.get("status")));
        if (!isBlank(code)) {
            return code;
        }
        String lowerRaw = rawBody == null ? "" : rawBody.toLowerCase(Locale.ROOT);
        if (lowerRaw.contains("rate limit") || lowerRaw.contains("rate_limit")) {
            return "rate_limit_error";
        }
        if (lowerRaw.contains("quota")) {
            return "quota_exceeded";
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
        sanitized = sanitized.replaceAll("api-key\\s*[:=]\\s*[A-Za-z0-9._~+/=-]+", "api-key=***");
        sanitized = sanitized.replaceAll("AIza[A-Za-z0-9_-]+", "AIza***");
        return truncate(sanitized, 240);
    }

    private String responseRequestId(HttpResponse<?> response) {
        return firstNonBlank(
                response.headers().firstValue("x-request-id").orElse(null),
                response.headers().firstValue("request-id").orElse(null),
                response.headers().firstValue("x-cloud-trace-context").orElse(null),
                response.headers().firstValue("cf-ray").orElse(null)
        );
    }

    private List<String> defaultFamilies(ProviderType providerType, String protocol) {
        if (PROTOCOL_GEMINI_NATIVE.equals(protocol)) {
            return GEMINI_DEFAULT_FAMILIES;
        }
        if (isEmbedRerankNativeProtocol(protocol)) {
            return EMBED_RERANK_DEFAULT_FAMILIES;
        }
        if (isAnthropicCompatibleProtocol(protocol)) {
            return ANTHROPIC_COMPAT_DEFAULT_FAMILIES;
        }
        if (isOpenAiCompatibleProtocol(protocol)) {
            return OPENAI_COMPAT_DEFAULT_FAMILIES;
        }
        if (providerType == ProviderType.GEMINI_DIRECT) {
            return GEMINI_DEFAULT_FAMILIES;
        }
        if (providerType == ProviderType.ANTHROPIC_DIRECT) {
            return ANTHROPIC_COMPAT_DEFAULT_FAMILIES;
        }
        if (providerType == ProviderType.OPENAI_COMPATIBLE) {
            return OPENAI_COMPAT_DEFAULT_FAMILIES;
        }
        return List.of();
    }

    private String defaultBaseUrl(String protocol) {
        if (PROTOCOL_GEMINI_NATIVE.equals(protocol)) {
            return DEFAULT_GEMINI_BASE_URL;
        }
        if (isAnthropicCompatibleProtocol(protocol)) {
            return DEFAULT_MIMO_ANTHROPIC_BASE_URL;
        }
        if (PROTOCOL_DEEPSEEK_OPENAI_COMPATIBLE.equals(protocol)) {
            return DEFAULT_DEEPSEEK_BASE_URL;
        }
        if (PROTOCOL_XAI_OPENAI_COMPATIBLE.equals(protocol)) {
            return DEFAULT_XAI_BASE_URL;
        }
        if (PROTOCOL_QWEN_OPENAI_COMPATIBLE.equals(protocol)) {
            return DEFAULT_QWEN_BASE_URL;
        }
        if (PROTOCOL_MOONSHOT_OPENAI_COMPATIBLE.equals(protocol)) {
            return DEFAULT_MOONSHOT_BASE_URL;
        }
        if (PROTOCOL_VOLCENGINE_OPENAI_COMPATIBLE.equals(protocol)) {
            return DEFAULT_VOLCENGINE_BASE_URL;
        }
        if (PROTOCOL_MINIMAX_OPENAI_COMPATIBLE.equals(protocol)) {
            return DEFAULT_MINIMAX_BASE_URL;
        }
        if (PROTOCOL_MISTRAL_OPENAI_COMPATIBLE.equals(protocol)) {
            return DEFAULT_MISTRAL_BASE_URL;
        }
        if (PROTOCOL_PERPLEXITY_OPENAI_COMPATIBLE.equals(protocol)) {
            return DEFAULT_PERPLEXITY_BASE_URL;
        }
        if (PROTOCOL_COHERE_NATIVE.equals(protocol)) {
            return DEFAULT_COHERE_BASE_URL;
        }
        if (PROTOCOL_JINA_NATIVE.equals(protocol)) {
            return DEFAULT_JINA_BASE_URL;
        }
        return DEFAULT_MIMO_OPENAI_BASE_URL;
    }

    private String defaultModel(String protocol) {
        if (PROTOCOL_GEMINI_NATIVE.equals(protocol)) {
            return DEFAULT_GEMINI_MODEL;
        }
        if (PROTOCOL_DEEPSEEK_OPENAI_COMPATIBLE.equals(protocol)) {
            return DEFAULT_DEEPSEEK_MODEL;
        }
        if (PROTOCOL_XAI_OPENAI_COMPATIBLE.equals(protocol)) {
            return DEFAULT_XAI_MODEL;
        }
        if (PROTOCOL_QWEN_OPENAI_COMPATIBLE.equals(protocol)) {
            return DEFAULT_QWEN_MODEL;
        }
        if (PROTOCOL_MOONSHOT_OPENAI_COMPATIBLE.equals(protocol)) {
            return DEFAULT_MOONSHOT_MODEL;
        }
        if (PROTOCOL_VOLCENGINE_OPENAI_COMPATIBLE.equals(protocol)) {
            return DEFAULT_VOLCENGINE_MODEL;
        }
        if (PROTOCOL_MINIMAX_OPENAI_COMPATIBLE.equals(protocol)) {
            return DEFAULT_MINIMAX_MODEL;
        }
        if (PROTOCOL_MISTRAL_OPENAI_COMPATIBLE.equals(protocol)) {
            return DEFAULT_MISTRAL_MODEL;
        }
        if (PROTOCOL_PERPLEXITY_OPENAI_COMPATIBLE.equals(protocol)) {
            return DEFAULT_PERPLEXITY_MODEL;
        }
        if (PROTOCOL_COHERE_NATIVE.equals(protocol)) {
            return DEFAULT_COHERE_MODEL;
        }
        if (PROTOCOL_JINA_NATIVE.equals(protocol)) {
            return DEFAULT_JINA_MODEL;
        }
        return DEFAULT_MIMO_MODEL;
    }

    private String normalizeProtocol(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "GEMINI", "GOOGLE", "GEMINI_NATIVE" -> PROTOCOL_GEMINI_NATIVE;
            case "COHERE", "COHERE_NATIVE", "COHERE_EMBED", "COHERE_RERANK" -> PROTOCOL_COHERE_NATIVE;
            case "JINA", "JINA_NATIVE", "JINA_EMBEDDINGS", "JINA_RERANK" -> PROTOCOL_JINA_NATIVE;
            case "MIMO_OPENAI", "MIMO_OPENAI_COMPATIBLE", "XIAOMI_MIMO_OPENAI_COMPATIBLE" -> PROTOCOL_OPENAI_COMPATIBLE;
            case "DEEPSEEK", "DEEPSEEK_OPENAI", "DEEPSEEK_OPENAI_COMPATIBLE" -> PROTOCOL_DEEPSEEK_OPENAI_COMPATIBLE;
            case "XAI", "XAI_OPENAI", "XAI_OPENAI_COMPATIBLE", "GROK", "GROK_OPENAI", "GROK_OPENAI_COMPATIBLE" -> PROTOCOL_XAI_OPENAI_COMPATIBLE;
            case "QWEN", "QWEN_OPENAI", "QWEN_OPENAI_COMPATIBLE" -> PROTOCOL_QWEN_OPENAI_COMPATIBLE;
            case "MOONSHOT", "MOONSHOT_OPENAI", "MOONSHOT_OPENAI_COMPATIBLE", "KIMI", "KIMI_OPENAI", "KIMI_OPENAI_COMPATIBLE" -> PROTOCOL_MOONSHOT_OPENAI_COMPATIBLE;
            case "VOLCENGINE", "VOLCENGINE_OPENAI", "VOLCENGINE_OPENAI_COMPATIBLE", "DOUBAO", "DOUBAO_OPENAI", "DOUBAO_OPENAI_COMPATIBLE" -> PROTOCOL_VOLCENGINE_OPENAI_COMPATIBLE;
            case "MINIMAX", "MINIMAX_OPENAI", "MINIMAX_OPENAI_COMPATIBLE" -> PROTOCOL_MINIMAX_OPENAI_COMPATIBLE;
            case "MISTRAL", "MISTRAL_OPENAI", "MISTRAL_OPENAI_COMPATIBLE" -> PROTOCOL_MISTRAL_OPENAI_COMPATIBLE;
            case "PERPLEXITY", "PERPLEXITY_OPENAI", "PERPLEXITY_OPENAI_COMPATIBLE", "SONAR", "SONAR_OPENAI", "SONAR_OPENAI_COMPATIBLE" -> PROTOCOL_PERPLEXITY_OPENAI_COMPATIBLE;
            case "OPENAI", "OPENAI_COMPAT", "OPENAI_COMPATIBLE" -> PROTOCOL_OPENAI_COMPATIBLE_GENERIC;
            case "MIMO_ANTHROPIC", "MIMO_ANTHROPIC_COMPATIBLE", "XIAOMI_MIMO_ANTHROPIC_COMPATIBLE" -> PROTOCOL_ANTHROPIC_COMPATIBLE;
            case "ANTHROPIC", "CLAUDE", "ANTHROPIC_COMPAT", "ANTHROPIC_COMPATIBLE" -> PROTOCOL_ANTHROPIC_COMPATIBLE_GENERIC;
            default -> normalized;
        };
    }

    private String normalizeFamily(String value, String protocol) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        normalized = switch (normalized) {
            case "GENERATECONTENT" -> GENERATE_CONTENT;
            case "STREAMGENERATECONTENT" -> STREAM_GENERATE_CONTENT;
            case "TOOL", "TOOLS", "FUNCTION_CALLING" -> toolFamily(protocol);
            case "CHAT", "CHAT_COMPLETION", "CHAT_COMPLETIONS" -> CHAT_COMPLETIONS;
            case "CHAT_STREAM", "CHAT_STREAMING", "STREAMING_CHAT" -> CHAT_STREAMING;
            case "CHAT_TOOL", "CHAT_TOOLS", "TOOL_CHAT", "OPENAI_TOOLS" -> CHAT_TOOLS;
            case "MESSAGE", "MESSAGES" -> MESSAGES;
            case "MESSAGE_STREAM", "MESSAGE_STREAMING", "MESSAGES_STREAMING" -> MESSAGES_STREAMING;
            case "TOOL_USE", "ANTHROPIC_TOOLS" -> TOOL_USE;
            case "EMBED", "EMBEDDING", "EMBEDDINGS" -> EMBEDDINGS;
            case "RERANK", "RERANKING" -> RERANK;
            default -> normalized;
        };
        return normalized;
    }

    private String toolFamily(String protocol) {
        if (PROTOCOL_GEMINI_NATIVE.equals(protocol)) {
            return TOOL_CALLING;
        }
        if (isAnthropicCompatibleProtocol(protocol)) {
            return TOOL_USE;
        }
        return CHAT_TOOLS;
    }

    private boolean isOpenAiCompatibleProtocol(String protocol) {
        return PROTOCOL_OPENAI_COMPATIBLE.equals(protocol)
                || PROTOCOL_DEEPSEEK_OPENAI_COMPATIBLE.equals(protocol)
                || PROTOCOL_XAI_OPENAI_COMPATIBLE.equals(protocol)
                || PROTOCOL_QWEN_OPENAI_COMPATIBLE.equals(protocol)
                || PROTOCOL_MOONSHOT_OPENAI_COMPATIBLE.equals(protocol)
                || PROTOCOL_VOLCENGINE_OPENAI_COMPATIBLE.equals(protocol)
                || PROTOCOL_MINIMAX_OPENAI_COMPATIBLE.equals(protocol)
                || PROTOCOL_MISTRAL_OPENAI_COMPATIBLE.equals(protocol)
                || PROTOCOL_PERPLEXITY_OPENAI_COMPATIBLE.equals(protocol)
                || PROTOCOL_OPENAI_COMPATIBLE_GENERIC.equals(protocol);
    }

    private boolean isAnthropicCompatibleProtocol(String protocol) {
        return PROTOCOL_ANTHROPIC_COMPATIBLE.equals(protocol)
                || PROTOCOL_ANTHROPIC_COMPATIBLE_GENERIC.equals(protocol);
    }

    private boolean isMimoBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return false;
        }
        String normalized = baseUrl.toLowerCase(Locale.ROOT);
        return normalized.contains("xiaomimimo.com")
                || normalized.contains("api.mimo-v2.com");
    }

    private boolean isDeepSeekBaseUrl(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("deepseek.com");
    }

    private boolean isXaiBaseUrl(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("api.x.ai");
    }

    private boolean isQwenBaseUrl(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("dashscope.aliyuncs.com");
    }

    private boolean isMoonshotBaseUrl(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("moonshot.cn");
    }

    private boolean isVolcengineBaseUrl(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("volces.com");
    }

    private boolean isMiniMaxBaseUrl(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("minimax.chat");
    }

    private boolean isMistralBaseUrl(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("mistral.ai");
    }

    private boolean isPerplexityBaseUrl(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("perplexity.ai");
    }

    private boolean isCohereBaseUrl(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("api.cohere.ai");
    }

    private boolean isJinaBaseUrl(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("api.jina.ai");
    }

    private boolean isEmbedRerankNativeProtocol(String protocol) {
        return PROTOCOL_COHERE_NATIVE.equals(protocol)
                || PROTOCOL_JINA_NATIVE.equals(protocol);
    }

    private boolean usesBearerOpenAiCompatibleAuth(String protocol) {
        return PROTOCOL_DEEPSEEK_OPENAI_COMPATIBLE.equals(protocol)
                || PROTOCOL_XAI_OPENAI_COMPATIBLE.equals(protocol)
                || PROTOCOL_QWEN_OPENAI_COMPATIBLE.equals(protocol)
                || PROTOCOL_MOONSHOT_OPENAI_COMPATIBLE.equals(protocol)
                || PROTOCOL_VOLCENGINE_OPENAI_COMPATIBLE.equals(protocol)
                || PROTOCOL_MINIMAX_OPENAI_COMPATIBLE.equals(protocol)
                || PROTOCOL_MISTRAL_OPENAI_COMPATIBLE.equals(protocol)
                || PROTOCOL_PERPLEXITY_OPENAI_COMPATIBLE.equals(protocol)
                || PROTOCOL_OPENAI_COMPATIBLE_GENERIC.equals(protocol)
                || isEmbedRerankNativeProtocol(protocol);
    }

    private String normalizeOpenAiCompatibleBaseUrl(String protocol, String baseUrl) {
        if (PROTOCOL_QWEN_OPENAI_COMPATIBLE.equals(protocol)) {
            return removeSuffix(baseUrl, "/compatible-mode/v1");
        }
        if (PROTOCOL_VOLCENGINE_OPENAI_COMPATIBLE.equals(protocol)) {
            return removeSuffix(baseUrl, "/api/v3");
        }
        if (PROTOCOL_PERPLEXITY_OPENAI_COMPATIBLE.equals(protocol)) {
            return removeSuffix(baseUrl, "/v1");
        }
        return removeSuffix(baseUrl, "/v1");
    }

    private String openAiCompatibleChatPath(String protocol) {
        if (PROTOCOL_QWEN_OPENAI_COMPATIBLE.equals(protocol)) {
            return "/compatible-mode/v1/chat/completions";
        }
        if (PROTOCOL_VOLCENGINE_OPENAI_COMPATIBLE.equals(protocol)) {
            return "/api/v3/chat/completions";
        }
        if (PROTOCOL_PERPLEXITY_OPENAI_COMPATIBLE.equals(protocol)) {
            return "/chat/completions";
        }
        return "/v1/chat/completions";
    }

    private String normalizeBaseUrl(String requestedBaseUrl) {
        if (requestedBaseUrl == null || requestedBaseUrl.isBlank()) {
            return null;
        }
        String trimmed = requestedBaseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String removeSuffix(String value, String suffix) {
        if (value == null) {
            return null;
        }
        return value.endsWith(suffix) ? value.substring(0, value.length() - suffix.length()) : value;
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
            ProviderType providerType,
            String protocol,
            String family,
            String baseUrl,
            String method,
            String path,
            String url,
            String probeKind,
            String model,
            boolean billable,
            boolean writeOperation,
            boolean unsupported,
            String unsupportedReason) {
    }
}
