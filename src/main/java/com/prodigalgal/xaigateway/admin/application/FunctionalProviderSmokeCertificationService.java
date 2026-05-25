package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.FunctionalProviderSmokeCertificationFixture;
import com.prodigalgal.xaigateway.admin.api.FunctionalProviderSmokeCertificationResponse;
import com.prodigalgal.xaigateway.admin.api.FunctionalProviderSmokeItemResponse;
import com.prodigalgal.xaigateway.admin.api.FunctionalProviderSmokeRecordReplayFixture;
import com.prodigalgal.xaigateway.admin.api.FunctionalProviderSmokeResponse;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class FunctionalProviderSmokeCertificationService {

    static final String RECORD_REPLAY_SCHEMA_VERSION = "2026-05-19.functional-provider-smoke-record-replay.v1";

    FunctionalProviderSmokeCertificationResponse certify(FunctionalProviderSmokeResponse smoke, Instant generatedAt) {
        List<FunctionalProviderSmokeCertificationFixture> fixtures = smoke.items().stream()
                .map(item -> fixture(item, smoke))
                .toList();
        String certificationStatus = certificationStatus(smoke.summary(), smoke.dryRun());
        FunctionalProviderSmokeRecordReplayFixture recordReplayFixture = recordReplayFixture(
                smoke,
                generatedAt,
                certificationStatus,
                fixtures
        );
        return new FunctionalProviderSmokeCertificationResponse(
                smoke.credentialId(),
                certificationStatus,
                smoke.dryRun(),
                generatedAt,
                Map.copyOf(smoke.summary()),
                fixtures,
                recordReplayFixture,
                smoke
        );
    }

    Map<String, Object> metadata(FunctionalProviderSmokeCertificationResponse certification) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("certificationStatus", certification.certificationStatus());
        result.put("dryRun", certification.dryRun());
        result.put("generatedAt", certification.generatedAt().toString());
        result.put("summary", certification.summary());
        List<Map<String, Object>> fixtures = new ArrayList<>();
        for (FunctionalProviderSmokeCertificationFixture fixture : certification.fixtureSnapshots()) {
            fixtures.add(fixtureMetadata(fixture));
        }
        result.put("fixtureSnapshots", fixtures);
        result.put("recordReplayFixture", recordReplayMetadata(certification.recordReplayFixture()));
        return sanitizeMap(result);
    }

    private FunctionalProviderSmokeRecordReplayFixture recordReplayFixture(
            FunctionalProviderSmokeResponse smoke,
            Instant generatedAt,
            String certificationStatus,
            List<FunctionalProviderSmokeCertificationFixture> fixtures) {
        Map<String, Object> replayPolicy = new LinkedHashMap<>();
        replayPolicy.put("network", "disabled_by_default");
        replayPolicy.put("billableOperations", "replay_only");
        replayPolicy.put("writeOperations", "replay_only");
        replayPolicy.put("secretMaterial", "redacted");
        replayPolicy.put("dryRunEvidenceAccepted", smoke.dryRun());
        replayPolicy.put("fixtureSource", "functional_provider_smoke_certification");
        replayPolicy.put("liveExecutionRequiresAllowLive", true);
        replayPolicy.put("billableExecutionRequiresAllowBillableProbes", true);
        return new FunctionalProviderSmokeRecordReplayFixture(
                RECORD_REPLAY_SCHEMA_VERSION,
                "record_replay",
                recordReplayProviderType(smoke.providerType(), smoke.protocol(), smoke.baseUrl()),
                recordReplayProtocol(smoke.protocol(), smoke.baseUrl()),
                sanitizeText(smoke.baseUrl()),
                baseUrlHost(smoke.baseUrl()),
                certificationStatus,
                smoke.dryRun(),
                generatedAt,
                Map.copyOf(smoke.summary()),
                Map.copyOf(replayPolicy),
                List.copyOf(fixtures)
        );
    }

    private Map<String, Object> recordReplayMetadata(FunctionalProviderSmokeRecordReplayFixture fixture) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", fixture.schemaVersion());
        result.put("replayMode", fixture.replayMode());
        result.put("providerType", fixture.providerType());
        result.put("protocol", fixture.protocol());
        result.put("baseUrl", fixture.baseUrl());
        result.put("baseUrlHost", fixture.baseUrlHost());
        result.put("certificationStatus", fixture.certificationStatus());
        result.put("dryRun", fixture.dryRun());
        result.put("recordedAt", fixture.recordedAt().toString());
        result.put("summary", fixture.summary());
        result.put("replayPolicy", fixture.replayPolicy());
        List<Map<String, Object>> fixtures = new ArrayList<>();
        for (FunctionalProviderSmokeCertificationFixture child : fixture.fixtures()) {
            fixtures.add(fixtureMetadata(child));
        }
        result.put("fixtures", fixtures);
        return result;
    }

    private Map<String, Object> fixtureMetadata(FunctionalProviderSmokeCertificationFixture fixture) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("providerType", fixture.providerType());
        item.put("protocol", fixture.protocol());
        item.put("resourceFamily", fixture.resourceFamily());
        item.put("status", fixture.status());
        item.put("classification", fixture.classification());
        putIfPresent(item, "skippedReason", fixture.skippedReason());
        item.put("method", fixture.method());
        item.put("path", fixture.path());
        item.put("model", fixture.model());
        item.put("billable", fixture.billable());
        item.put("writeOperation", fixture.writeOperation());
        putIfPresent(item, "httpStatus", fixture.httpStatus());
        putIfPresent(item, "upstreamRequestId", fixture.upstreamRequestId());
        putIfPresent(item, "durationMs", fixture.durationMs());
        putIfPresent(item, "failureType", fixture.failureType());
        putIfPresent(item, "failureMessage", fixture.failureMessage());
        item.put("evidence", fixture.evidence());
        item.put("requestPreview", fixture.requestPreview());
        return item;
    }

    private FunctionalProviderSmokeCertificationFixture fixture(
            FunctionalProviderSmokeItemResponse item,
            FunctionalProviderSmokeResponse smoke) {
        ProviderType providerType = item.providerType() == null ? smoke.providerType() : item.providerType();
        String protocol = firstNonBlank(item.protocol(), smoke.protocol());
        String baseUrl = firstNonBlank(baseUrlFromPreview(item.requestPreview()), smoke.baseUrl());
        return new FunctionalProviderSmokeCertificationFixture(
                recordReplayProviderType(providerType, protocol, baseUrl),
                recordReplayProtocol(protocol, baseUrl),
                item.resourceFamily(),
                item.status(),
                item.classification(),
                item.skippedReason(),
                item.method(),
                item.path(),
                item.model(),
                item.billable(),
                item.writeOperation(),
                item.httpStatus(),
                item.upstreamRequestId(),
                item.durationMs(),
                item.failureType(),
                sanitizeText(item.failureMessage()),
                sanitizeMap(item.evidence()),
                recordReplayRequestPreview(item.requestPreview(), providerType, protocol, baseUrl)
        );
    }

    private String recordReplayProviderType(ProviderType providerType, String protocol, String baseUrl) {
        if (providerType == ProviderType.GEMINI_DIRECT) {
            return "GEMINI_DIRECT";
        }
        if (isMimoProtocol(protocol) || isMimoBaseUrl(baseUrl)) {
            return "XIAOMI_MIMO";
        }
        if (isDeepSeekProtocol(protocol) || isDeepSeekBaseUrl(baseUrl)) {
            return "DEEPSEEK";
        }
        if (isXaiProtocol(protocol) || isXaiBaseUrl(baseUrl)) {
            return "XAI";
        }
        if (isQwenProtocol(protocol) || isQwenBaseUrl(baseUrl)) {
            return "QWEN";
        }
        if (isMoonshotProtocol(protocol) || isMoonshotBaseUrl(baseUrl)) {
            return "MOONSHOT";
        }
        if (isVolcengineProtocol(protocol) || isVolcengineBaseUrl(baseUrl)) {
            return "VOLCENGINE";
        }
        if (isMiniMaxProtocol(protocol) || isMiniMaxBaseUrl(baseUrl)) {
            return "MINIMAX";
        }
        if (isMistralProtocol(protocol) || isMistralBaseUrl(baseUrl)) {
            return "MISTRAL";
        }
        if (isPerplexityProtocol(protocol) || isPerplexityBaseUrl(baseUrl)) {
            return "PERPLEXITY";
        }
        if (isCohereProtocol(protocol) || isCohereBaseUrl(baseUrl)) {
            return "COHERE";
        }
        if (isJinaProtocol(protocol) || isJinaBaseUrl(baseUrl)) {
            return "JINA";
        }
        return providerType == null ? "UNKNOWN" : providerType.name();
    }

    private String recordReplayProtocol(String protocol, String baseUrl) {
        String normalized = protocol == null ? "" : protocol.trim().toUpperCase(Locale.ROOT);
        if ("GEMINI_NATIVE".equals(normalized)) {
            return "GEMINI_NATIVE";
        }
        if (isCohereProtocol(protocol) || isCohereBaseUrl(baseUrl)) {
            return "COHERE_NATIVE";
        }
        if (isJinaProtocol(protocol) || isJinaBaseUrl(baseUrl)) {
            return "JINA_NATIVE";
        }
        if ("ANTHROPIC_COMPATIBLE".equals(normalized) && (isMimoProtocol(protocol) || isMimoBaseUrl(baseUrl))) {
            return "XIAOMI_MIMO_ANTHROPIC_COMPATIBLE";
        }
        if ("OPENAI_COMPATIBLE".equals(normalized) && (isMimoProtocol(protocol) || isMimoBaseUrl(baseUrl))) {
            return "XIAOMI_MIMO_OPENAI_COMPATIBLE";
        }
        if ("OPENAI_COMPATIBLE".equals(normalized) && (isDeepSeekProtocol(protocol) || isDeepSeekBaseUrl(baseUrl))) {
            return "DEEPSEEK_OPENAI_COMPATIBLE";
        }
        if ("OPENAI_COMPATIBLE".equals(normalized) && (isXaiProtocol(protocol) || isXaiBaseUrl(baseUrl))) {
            return "XAI_OPENAI_COMPATIBLE";
        }
        if ("OPENAI_COMPATIBLE".equals(normalized) && (isQwenProtocol(protocol) || isQwenBaseUrl(baseUrl))) {
            return "QWEN_OPENAI_COMPATIBLE";
        }
        if ("OPENAI_COMPATIBLE".equals(normalized) && (isMoonshotProtocol(protocol) || isMoonshotBaseUrl(baseUrl))) {
            return "MOONSHOT_OPENAI_COMPATIBLE";
        }
        if ("OPENAI_COMPATIBLE".equals(normalized) && (isVolcengineProtocol(protocol) || isVolcengineBaseUrl(baseUrl))) {
            return "VOLCENGINE_OPENAI_COMPATIBLE";
        }
        if ("OPENAI_COMPATIBLE".equals(normalized) && (isMiniMaxProtocol(protocol) || isMiniMaxBaseUrl(baseUrl))) {
            return "MINIMAX_OPENAI_COMPATIBLE";
        }
        if ("OPENAI_COMPATIBLE".equals(normalized) && (isMistralProtocol(protocol) || isMistralBaseUrl(baseUrl))) {
            return "MISTRAL_OPENAI_COMPATIBLE";
        }
        if ("OPENAI_COMPATIBLE".equals(normalized) && (isPerplexityProtocol(protocol) || isPerplexityBaseUrl(baseUrl))) {
            return "PERPLEXITY_OPENAI_COMPATIBLE";
        }
        return normalized.isBlank() ? "UNKNOWN" : normalized;
    }

    private boolean isMimoProtocol(String protocol) {
        if (protocol == null || protocol.isBlank()) {
            return false;
        }
        String normalized = protocol.trim().toUpperCase(Locale.ROOT);
        return normalized.contains("MIMO")
                || normalized.contains("XIAOMI");
    }

    private boolean isMimoBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return false;
        }
        String normalized = baseUrl.toLowerCase(Locale.ROOT);
        return normalized.contains("xiaomimimo.com")
                || normalized.contains("api.mimo-v2.com");
    }

    private boolean isDeepSeekProtocol(String protocol) {
        if (protocol == null || protocol.isBlank()) {
            return false;
        }
        return protocol.trim().toUpperCase(Locale.ROOT).contains("DEEPSEEK");
    }

    private boolean isDeepSeekBaseUrl(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("deepseek.com");
    }

    private boolean isXaiProtocol(String protocol) {
        if (protocol == null || protocol.isBlank()) {
            return false;
        }
        String normalized = protocol.trim().toUpperCase(Locale.ROOT);
        return normalized.contains("XAI") || normalized.contains("GROK");
    }

    private boolean isXaiBaseUrl(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("api.x.ai");
    }

    private boolean isQwenProtocol(String protocol) {
        return containsProtocol(protocol, "QWEN");
    }

    private boolean isQwenBaseUrl(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("dashscope.aliyuncs.com");
    }

    private boolean isMoonshotProtocol(String protocol) {
        return containsProtocol(protocol, "MOONSHOT") || containsProtocol(protocol, "KIMI");
    }

    private boolean isMoonshotBaseUrl(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("moonshot.cn");
    }

    private boolean isVolcengineProtocol(String protocol) {
        return containsProtocol(protocol, "VOLCENGINE") || containsProtocol(protocol, "DOUBAO");
    }

    private boolean isVolcengineBaseUrl(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("volces.com");
    }

    private boolean isMiniMaxProtocol(String protocol) {
        return containsProtocol(protocol, "MINIMAX");
    }

    private boolean isMiniMaxBaseUrl(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("minimax.chat");
    }

    private boolean isMistralProtocol(String protocol) {
        return containsProtocol(protocol, "MISTRAL");
    }

    private boolean isMistralBaseUrl(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("mistral.ai");
    }

    private boolean isPerplexityProtocol(String protocol) {
        return containsProtocol(protocol, "PERPLEXITY") || containsProtocol(protocol, "SONAR");
    }

    private boolean isPerplexityBaseUrl(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("perplexity.ai");
    }

    private boolean isCohereProtocol(String protocol) {
        if (protocol == null || protocol.isBlank()) {
            return false;
        }
        return protocol.trim().toUpperCase(Locale.ROOT).contains("COHERE");
    }

    private boolean isCohereBaseUrl(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("api.cohere.ai");
    }

    private boolean isJinaProtocol(String protocol) {
        if (protocol == null || protocol.isBlank()) {
            return false;
        }
        return protocol.trim().toUpperCase(Locale.ROOT).contains("JINA");
    }

    private boolean isJinaBaseUrl(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("api.jina.ai");
    }

    private boolean containsProtocol(String protocol, String token) {
        return protocol != null && protocol.trim().toUpperCase(Locale.ROOT).contains(token);
    }

    private String baseUrlFromPreview(Map<String, Object> requestPreview) {
        if (requestPreview == null) {
            return null;
        }
        Object baseUrl = requestPreview.get("baseUrl");
        return baseUrl == null ? null : String.valueOf(baseUrl);
    }

    private Map<String, Object> recordReplayRequestPreview(
            Map<String, Object> requestPreview,
            ProviderType providerType,
            String protocol,
            String fallbackBaseUrl) {
        Map<String, Object> preview = new LinkedHashMap<>(sanitizeMap(requestPreview));
        String baseUrl = firstNonBlank(baseUrlFromPreview(preview), fallbackBaseUrl);
        preview.put("providerType", recordReplayProviderType(providerType, protocol, baseUrl));
        preview.put("protocol", recordReplayProtocol(protocol, baseUrl));
        return Map.copyOf(preview);
    }

    private String certificationStatus(Map<String, Integer> summary, boolean dryRun) {
        if (dryRun) {
            return "DRY_RUN";
        }
        if (count(summary, "FAIL") > 0) {
            return "FAILED";
        }
        if (count(summary, "NO_PERMISSION") > 0) {
            return "NO_PERMISSION";
        }
        if (count(summary, "PASS") > 0 && count(summary, "BUDGET_BLOCKED") > 0) {
            return "PARTIAL_CERTIFIED";
        }
        if (count(summary, "PASS") > 0) {
            return "CERTIFIED";
        }
        if (count(summary, "BUDGET_BLOCKED") > 0) {
            return "BUDGET_BLOCKED";
        }
        if (count(summary, "UNSUPPORTED") > 0) {
            return "UNSUPPORTED";
        }
        return "SKIPPED";
    }

    private int count(Map<String, Integer> summary, String key) {
        return summary.getOrDefault(key, 0);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Object sanitized = sanitize(source);
        return sanitized instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
    }

    private Object sanitize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((key, child) -> {
                if (key != null) {
                    String normalizedKey = String.valueOf(key);
                    sanitized.put(normalizedKey, shouldRedactKey(normalizedKey) ? "***" : sanitize(child));
                }
            });
            return sanitized;
        }
        if (value instanceof List<?> list) {
            List<Object> sanitized = new ArrayList<>();
            for (Object child : list) {
                sanitized.add(sanitize(child));
            }
            return sanitized;
        }
        if (value instanceof String text) {
            return sanitizeText(text);
        }
        return value;
    }

    private String sanitizeText(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = value.replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer ***");
        sanitized = sanitized.replaceAll("sk-[A-Za-z0-9._~+/=-]+", "sk-***");
        sanitized = sanitized.replaceAll("AIza[A-Za-z0-9_-]{10,}", "AIza***");
        sanitized = sanitized.replaceAll("(?i)(api-key|x-api-key|x-goog-api-key)\\s*[:=]\\s*[^\\s,;}]+", "$1=***");
        sanitized = sanitized.replaceAll("\\borg[-_][A-Za-z0-9._~+-]+", "org-***");
        sanitized = sanitized.replaceAll("\\bproj[-_][A-Za-z0-9._~+-]+", "proj-***");
        return sanitized.length() <= 512 ? sanitized : sanitized.substring(0, 512);
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return normalized.contains("authorization")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("api_key")
                || normalized.contains("apikey")
                || normalized.contains("api-key")
                || normalized.contains("x-api-key")
                || normalized.contains("x-goog-api-key")
                || normalized.contains("organization")
                || normalized.contains("project")
                || normalized.contains("cookie")
                || "key".equals(normalized);
    }

    private boolean shouldRedactKey(String key) {
        if ("secretMaterial".equals(key)) {
            return false;
        }
        return isSensitiveKey(key);
    }

    private String baseUrlHost(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(baseUrl);
            return sanitizeText(uri.getHost());
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
