package com.prodigalgal.xaigateway.admin.application;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

class FunctionalProviderSmokeRecordReplayFixtureVerifier {

    private static final Set<String> PROVIDER_TYPES = Set.of(
            "GEMINI_DIRECT",
            "XIAOMI_MIMO",
            "DEEPSEEK",
            "XAI",
            "QWEN",
            "MOONSHOT",
            "VOLCENGINE",
            "MINIMAX",
            "MISTRAL",
            "PERPLEXITY",
            "COHERE",
            "JINA"
    );

    private static final Set<String> FORBIDDEN_OFFICIAL_FIXTURE_PROVIDERS = Set.of(
            "OPENAI_COMPATIBLE",
            "OPENAI_COMPATIBLE_GENERIC",
            "DIFY",
            "OPENROUTER",
            "TOGETHER",
            "FIREWORKS",
            "SILICONFLOW"
    );

    private static final Set<String> PROTOCOLS = Set.of(
            "GEMINI_NATIVE",
            "XIAOMI_MIMO_OPENAI_COMPATIBLE",
            "XIAOMI_MIMO_ANTHROPIC_COMPATIBLE",
            "DEEPSEEK_OPENAI_COMPATIBLE",
            "XAI_OPENAI_COMPATIBLE",
            "QWEN_OPENAI_COMPATIBLE",
            "MOONSHOT_OPENAI_COMPATIBLE",
            "VOLCENGINE_OPENAI_COMPATIBLE",
            "MINIMAX_OPENAI_COMPATIBLE",
            "MISTRAL_OPENAI_COMPATIBLE",
            "PERPLEXITY_OPENAI_COMPATIBLE",
            "COHERE_NATIVE",
            "JINA_NATIVE"
    );

    private static final Set<String> FORBIDDEN_OFFICIAL_FIXTURE_PROTOCOLS = Set.of(
            "OPENAI_COMPATIBLE",
            "OPENAI_COMPATIBLE_GENERIC",
            "DIFY",
            "OPENROUTER",
            "TOGETHER",
            "FIREWORKS",
            "SILICONFLOW"
    );

    private static final Map<String, Set<String>> PROVIDER_PROTOCOLS = Map.ofEntries(
            Map.entry("GEMINI_DIRECT", Set.of("GEMINI_NATIVE")),
            Map.entry("XIAOMI_MIMO", Set.of(
                    "XIAOMI_MIMO_OPENAI_COMPATIBLE",
                    "XIAOMI_MIMO_ANTHROPIC_COMPATIBLE"
            )),
            Map.entry("DEEPSEEK", Set.of("DEEPSEEK_OPENAI_COMPATIBLE")),
            Map.entry("XAI", Set.of("XAI_OPENAI_COMPATIBLE")),
            Map.entry("QWEN", Set.of("QWEN_OPENAI_COMPATIBLE")),
            Map.entry("MOONSHOT", Set.of("MOONSHOT_OPENAI_COMPATIBLE")),
            Map.entry("VOLCENGINE", Set.of("VOLCENGINE_OPENAI_COMPATIBLE")),
            Map.entry("MINIMAX", Set.of("MINIMAX_OPENAI_COMPATIBLE")),
            Map.entry("MISTRAL", Set.of("MISTRAL_OPENAI_COMPATIBLE")),
            Map.entry("PERPLEXITY", Set.of("PERPLEXITY_OPENAI_COMPATIBLE")),
            Map.entry("COHERE", Set.of("COHERE_NATIVE")),
            Map.entry("JINA", Set.of("JINA_NATIVE"))
    );

    private static final Set<String> CLASSIFICATIONS = Set.of(
            "PASS",
            "FAIL",
            "SKIPPED",
            "UNSUPPORTED",
            "NO_PERMISSION",
            "BUDGET_BLOCKED"
    );

    private static final Set<String> CERTIFICATION_STATUSES = Set.of(
            "DRY_RUN",
            "CERTIFIED",
            "PARTIAL_CERTIFIED",
            "NO_PERMISSION",
            "BUDGET_BLOCKED",
            "UNSUPPORTED",
            "FAILED",
            "SKIPPED"
    );

    private static final Set<String> HTTP_METHODS = Set.of("POST");

    private static final List<SensitivePattern> SENSITIVE_PATTERNS = List.of(
            new SensitivePattern("bearer-token", Pattern.compile("(?i)Bearer\\s+(?!\\*\\*\\*)\\S+")),
            new SensitivePattern("openai-key", Pattern.compile("sk-[A-Za-z0-9._~+/=-]+")),
            new SensitivePattern("google-ai-studio-key", Pattern.compile("AIza[0-9A-Za-z_-]{10,}")),
            new SensitivePattern("api-key-assignment", Pattern.compile("(?i)(api-key|x-api-key|x-goog-api-key)\\s*[:=]\\s*(?!\\*\\*\\*)[^\\s,;}]+")),
            new SensitivePattern("openai-org", Pattern.compile("\\borg[-_](?!\\*\\*\\*)[A-Za-z0-9._~+-]+")),
            new SensitivePattern("openai-project", Pattern.compile("\\bproj[-_](?!\\*\\*\\*)[A-Za-z0-9._~+-]+")),
            new SensitivePattern("github-token", Pattern.compile("\\bgh[opsu]_[A-Za-z0-9_]{20,}")),
            new SensitivePattern("slack-token", Pattern.compile("\\bxox[baprs]-[A-Za-z0-9-]{10,}"))
    );

    VerificationResult validate(JsonNode root) {
        List<String> errors = new ArrayList<>();
        if (root == null || !root.isObject()) {
            errors.add("$ 必须是 functional provider record/replay fixture 对象。");
            return VerificationResult.failed(errors);
        }

        validateTopLevel(root, errors);
        validateReplayPolicy(root.path("replayPolicy"), errors);
        Map<String, Integer> fixtureCounts = validateFixtures(
                root.path("fixtures"),
                root.path("providerType").asText(""),
                root.path("protocol").asText(""),
                errors
        );
        validateSummary(root.path("summary"), fixtureCounts, errors);
        scanSensitiveValues(root, "$", errors);

        return errors.isEmpty() ? VerificationResult.passed() : VerificationResult.failed(errors);
    }

    private void validateTopLevel(JsonNode root, List<String> errors) {
        expectText(root, "schemaVersion", FunctionalProviderSmokeCertificationService.RECORD_REPLAY_SCHEMA_VERSION,
                "$.schemaVersion", errors);
        expectText(root, "replayMode", "record_replay", "$.replayMode", errors);
        String providerType = requireText(root, "providerType", "$.providerType", errors);
        if (!providerType.isBlank() && !PROVIDER_TYPES.contains(providerType)) {
            errors.add("$.providerType 不在允许集合内：" + providerType);
        }
        String protocol = requireText(root, "protocol", "$.protocol", errors);
        if (!protocol.isBlank() && !PROTOCOLS.contains(protocol)) {
            errors.add("$.protocol 不在允许集合内：" + protocol);
        }
        validateProviderProtocol(providerType, protocol, "$", errors);
        validateOfficialProviderProtocolBan(providerType, protocol, "$", errors);
        requireText(root, "baseUrl", "$.baseUrl", errors);
        requireText(root, "baseUrlHost", "$.baseUrlHost", errors);
        String certificationStatus = requireText(root, "certificationStatus", "$.certificationStatus", errors);
        if (!certificationStatus.isBlank() && !CERTIFICATION_STATUSES.contains(certificationStatus)) {
            errors.add("$.certificationStatus 不在允许集合内：" + certificationStatus);
        }
        requireBoolean(root, "dryRun", "$.dryRun", errors);
        String recordedAt = requireText(root, "recordedAt", "$.recordedAt", errors);
        if (!recordedAt.isBlank()) {
            try {
                Instant.parse(recordedAt);
            } catch (DateTimeParseException exception) {
                errors.add("$.recordedAt 必须是 ISO-8601 Instant。");
            }
        }
    }

    private void validateReplayPolicy(JsonNode policy, List<String> errors) {
        if (!policy.isObject()) {
            errors.add("$.replayPolicy 必须是对象。");
            return;
        }
        expectText(policy, "network", "disabled_by_default", "$.replayPolicy.network", errors);
        expectText(policy, "billableOperations", "replay_only", "$.replayPolicy.billableOperations", errors);
        expectText(policy, "writeOperations", "replay_only", "$.replayPolicy.writeOperations", errors);
        expectText(policy, "secretMaterial", "redacted", "$.replayPolicy.secretMaterial", errors);
        expectText(policy, "fixtureSource", "functional_provider_smoke_certification",
                "$.replayPolicy.fixtureSource", errors);
        requireBoolean(policy, "dryRunEvidenceAccepted", "$.replayPolicy.dryRunEvidenceAccepted", errors);
        requireBoolean(policy, "liveExecutionRequiresAllowLive", "$.replayPolicy.liveExecutionRequiresAllowLive", errors);
        requireBoolean(policy, "billableExecutionRequiresAllowBillableProbes",
                "$.replayPolicy.billableExecutionRequiresAllowBillableProbes", errors);
    }

    private Map<String, Integer> validateFixtures(
            JsonNode fixtures,
            String topLevelProviderType,
            String topLevelProtocol,
            List<String> errors) {
        Map<String, Integer> counts = emptyCounts();
        if (!fixtures.isArray()) {
            errors.add("$.fixtures 必须是数组。");
            return counts;
        }

        int index = 0;
        for (JsonNode fixture : fixtures) {
            validateFixture(fixture, index, topLevelProviderType, topLevelProtocol, counts, errors);
            index++;
        }
        if (index == 0) {
            errors.add("$.fixtures 不能为空。");
        }
        return counts;
    }

    private void validateFixture(
            JsonNode fixture,
            int index,
            String topLevelProviderType,
            String topLevelProtocol,
            Map<String, Integer> counts,
            List<String> errors) {
        String pointer = "$.fixtures[" + index + "]";
        if (!fixture.isObject()) {
            errors.add(pointer + " 必须是对象。");
            return;
        }

        String providerType = requireText(fixture, "providerType", pointer + ".providerType", errors);
        if (!providerType.isBlank() && !PROVIDER_TYPES.contains(providerType)) {
            errors.add(pointer + ".providerType 不在允许集合内：" + providerType);
        }
        if (!topLevelProviderType.isBlank() && !providerType.isBlank() && !topLevelProviderType.equals(providerType)) {
            errors.add(pointer + ".providerType 必须与顶层 providerType 一致。");
        }
        String protocol = requireText(fixture, "protocol", pointer + ".protocol", errors);
        if (!protocol.isBlank() && !PROTOCOLS.contains(protocol)) {
            errors.add(pointer + ".protocol 不在允许集合内：" + protocol);
        }
        validateProviderProtocol(providerType, protocol, pointer, errors);
        validateOfficialProviderProtocolBan(providerType, protocol, pointer, errors);
        if (!topLevelProtocol.isBlank() && !protocol.isBlank() && !topLevelProtocol.equals(protocol)) {
            errors.add(pointer + ".protocol 必须与顶层 protocol 一致。");
        }
        String resourceFamily = requireText(fixture, "resourceFamily", pointer + ".resourceFamily", errors);
        requireText(fixture, "status", pointer + ".status", errors);
        String classification = requireText(fixture, "classification", pointer + ".classification", errors);
        if (!classification.isBlank() && CLASSIFICATIONS.contains(classification)) {
            counts.put(classification, counts.get(classification) + 1);
        } else if (!classification.isBlank()) {
            errors.add(pointer + ".classification 不在允许集合内：" + classification);
        }

        String method = requireText(fixture, "method", pointer + ".method", errors).toUpperCase(Locale.ROOT);
        if (!method.isBlank() && !HTTP_METHODS.contains(method)) {
            errors.add(pointer + ".method 不在允许集合内：" + method);
        }
        String path = requireText(fixture, "path", pointer + ".path", errors);
        validatePath(protocol, path, pointer + ".path", errors);
        requireText(fixture, "model", pointer + ".model", errors);
        requireBoolean(fixture, "billable", pointer + ".billable", errors);
        requireBoolean(fixture, "writeOperation", pointer + ".writeOperation", errors);
        requireObject(fixture, "evidence", pointer + ".evidence", errors);
        requireObject(fixture, "requestPreview", pointer + ".requestPreview", errors);
        validateRequestPreviewScope(fixture.path("requestPreview"), providerType, protocol,
                pointer + ".requestPreview", errors);
        validateEmbedRerankNativeFixture(fixture, protocol, resourceFamily, classification, path, pointer, errors);
        validateClassificationEvidence(fixture, classification, pointer, errors);
        validateOptionalStatus(fixture, pointer, errors);
    }

    private void validatePath(String protocol, String path, String pointer, List<String> errors) {
        if (path.isBlank()) {
            return;
        }
        if ("GEMINI_NATIVE".equals(protocol)) {
            if (!path.startsWith("/v1beta/models/") || !(path.contains(":generateContent") || path.contains(":streamGenerateContent"))) {
                errors.add(pointer + " 必须是 Gemini GenerateContent 路径。");
            }
            return;
        }
        if ("XIAOMI_MIMO_OPENAI_COMPATIBLE".equals(protocol)
                || "DEEPSEEK_OPENAI_COMPATIBLE".equals(protocol)
                || "XAI_OPENAI_COMPATIBLE".equals(protocol)
                || "MOONSHOT_OPENAI_COMPATIBLE".equals(protocol)
                || "MINIMAX_OPENAI_COMPATIBLE".equals(protocol)
                || "MISTRAL_OPENAI_COMPATIBLE".equals(protocol)) {
            if (!"/v1/chat/completions".equals(path)) {
                errors.add(pointer + " 必须是 provider-specific OpenAI-compatible Chat Completions 路径。");
            }
            return;
        }
        if ("QWEN_OPENAI_COMPATIBLE".equals(protocol)) {
            if (!"/compatible-mode/v1/chat/completions".equals(path)) {
                errors.add(pointer + " 必须是 Qwen provider-specific Chat Completions 路径。");
            }
            return;
        }
        if ("VOLCENGINE_OPENAI_COMPATIBLE".equals(protocol)) {
            if (!"/api/v3/chat/completions".equals(path)) {
                errors.add(pointer + " 必须是 Volcengine provider-specific Chat Completions 路径。");
            }
            return;
        }
        if ("PERPLEXITY_OPENAI_COMPATIBLE".equals(protocol)) {
            if (!"/chat/completions".equals(path)) {
                errors.add(pointer + " 必须是 Perplexity provider-specific Chat Completions 路径。");
            }
            return;
        }
        if ("COHERE_NATIVE".equals(protocol)) {
            if (!"/v2/embed".equals(path) && !"/v2/rerank".equals(path)) {
                errors.add(pointer + " 必须是 Cohere native embed/rerank 路径。");
            }
            return;
        }
        if ("JINA_NATIVE".equals(protocol)) {
            if (!"/v1/embeddings".equals(path) && !"/v1/rerank".equals(path)) {
                errors.add(pointer + " 必须是 Jina native embeddings/rerank 路径。");
            }
            return;
        }
        if ("XIAOMI_MIMO_ANTHROPIC_COMPATIBLE".equals(protocol) && !"/v1/messages".equals(path)) {
            errors.add(pointer + " 必须是 MiMo Anthropic-compatible Messages 路径。");
        }
    }

    private void validateEmbedRerankNativeFixture(
            JsonNode fixture,
            String protocol,
            String resourceFamily,
            String classification,
            String path,
            String pointer,
            List<String> errors) {
        if (!"COHERE_NATIVE".equals(protocol) && !"JINA_NATIVE".equals(protocol)) {
            return;
        }
        boolean supportedFamily = "EMBEDDINGS".equals(resourceFamily) || "RERANK".equals(resourceFamily);
        if (!supportedFamily) {
            if (!"UNSUPPORTED".equals(classification)) {
                errors.add(pointer + ".resourceFamily 不是 Cohere/Jina native embed/rerank 能力时只能记录 UNSUPPORTED。");
            }
            return;
        }
        String expectedPath = expectedEmbedRerankNativePath(protocol, resourceFamily);
        if (!expectedPath.isBlank() && !expectedPath.equals(path)) {
            errors.add(pointer + ".path 必须与 " + protocol + " 的 " + resourceFamily + " native endpoint 一致。");
        }
        if ("PASS".equals(classification)) {
            validateEmbedRerankNativePassEvidence(fixture.path("evidence"), protocol, resourceFamily, pointer, errors);
        }
    }

    private String expectedEmbedRerankNativePath(String protocol, String resourceFamily) {
        if ("COHERE_NATIVE".equals(protocol) && "EMBEDDINGS".equals(resourceFamily)) {
            return "/v2/embed";
        }
        if ("COHERE_NATIVE".equals(protocol) && "RERANK".equals(resourceFamily)) {
            return "/v2/rerank";
        }
        if ("JINA_NATIVE".equals(protocol) && "EMBEDDINGS".equals(resourceFamily)) {
            return "/v1/embeddings";
        }
        if ("JINA_NATIVE".equals(protocol) && "RERANK".equals(resourceFamily)) {
            return "/v1/rerank";
        }
        return "";
    }

    private void validateEmbedRerankNativePassEvidence(
            JsonNode evidence,
            String protocol,
            String resourceFamily,
            String pointer,
            List<String> errors) {
        if ("COHERE_NATIVE".equals(protocol) && "EMBEDDINGS".equals(resourceFamily)) {
            if (!positiveNumber(evidence.path("embeddingFloatVectorsSeen"))
                    || !arrayContains(evidence.path("embeddingFields"), "float")
                    || !arrayContains(evidence.path("billedUnitFields"), "input_tokens")) {
                errors.add(pointer + ".evidence 必须证明 Cohere embed 返回 embeddings.float 与 meta.billed_units.input_tokens。");
            }
            return;
        }
        if ("COHERE_NATIVE".equals(protocol) && "RERANK".equals(resourceFamily)) {
            if (!positiveNumber(evidence.path("resultsSeen"))
                    || !arrayContains(evidence.path("firstResultFields"), "relevance_score")
                    || !arrayContains(evidence.path("billedUnitFields"), "search_units")) {
                errors.add(pointer + ".evidence 必须证明 Cohere rerank 返回 results[].relevance_score 与 meta.billed_units.search_units。");
            }
            return;
        }
        if ("JINA_NATIVE".equals(protocol) && "EMBEDDINGS".equals(resourceFamily)
                && !positiveNumber(evidence.path("dataSeen"))
                && !positiveNumber(evidence.path("embeddingsSeen"))) {
            errors.add(pointer + ".evidence 必须证明 Jina embeddings 返回 embedding 数据。");
            return;
        }
        if ("JINA_NATIVE".equals(protocol) && "RERANK".equals(resourceFamily)
                && (!positiveNumber(evidence.path("resultsSeen"))
                || !arrayContains(evidence.path("firstResultFields"), "relevance_score"))) {
            errors.add(pointer + ".evidence 必须证明 Jina rerank 返回 results[].relevance_score。");
        }
    }

    private boolean positiveNumber(JsonNode value) {
        return value.isNumber() && value.asLong() > 0;
    }

    private boolean arrayContains(JsonNode value, String expected) {
        if (!value.isArray()) {
            return false;
        }
        for (JsonNode item : value) {
            if (expected.equals(item.asText())) {
                return true;
            }
        }
        return false;
    }

    private void validateProviderProtocol(String providerType, String protocol, String pointer, List<String> errors) {
        if (providerType.isBlank() || protocol.isBlank()) {
            return;
        }
        Set<String> allowedProtocols = PROVIDER_PROTOCOLS.get(providerType);
        if (allowedProtocols != null && !allowedProtocols.contains(protocol)) {
            errors.add(pointer + ".protocol 必须是 " + providerType + " 的 provider-specific protocol。");
        }
    }

    private void validateOfficialProviderProtocolBan(
            String providerType,
            String protocol,
            String pointer,
            List<String> errors) {
        if (!providerType.isBlank() && FORBIDDEN_OFFICIAL_FIXTURE_PROVIDERS.contains(providerType)) {
            errors.add(pointer + ".providerType 不能进入 official functional provider smoke fixture：" + providerType);
        }
        if (!protocol.isBlank() && FORBIDDEN_OFFICIAL_FIXTURE_PROTOCOLS.contains(protocol)) {
            errors.add(pointer + ".protocol 不能进入 official functional provider smoke fixture：" + protocol);
        }
    }

    private void validateRequestPreviewScope(
            JsonNode preview,
            String fixtureProviderType,
            String fixtureProtocol,
            String pointer,
            List<String> errors) {
        if (!preview.isObject()) {
            return;
        }
        String previewProviderType = requireText(preview, "providerType", pointer + ".providerType", errors);
        if (!previewProviderType.isBlank() && !PROVIDER_TYPES.contains(previewProviderType)) {
            errors.add(pointer + ".providerType 不在允许集合内：" + previewProviderType);
        }
        if (!fixtureProviderType.isBlank()
                && !previewProviderType.isBlank()
                && !fixtureProviderType.equals(previewProviderType)) {
            errors.add(pointer + ".providerType 必须与 fixture providerType 一致。");
        }
        String previewProtocol = requireText(preview, "protocol", pointer + ".protocol", errors);
        if (!previewProtocol.isBlank() && !PROTOCOLS.contains(previewProtocol)) {
            errors.add(pointer + ".protocol 不在允许集合内：" + previewProtocol);
        }
        if (!fixtureProtocol.isBlank() && !previewProtocol.isBlank() && !fixtureProtocol.equals(previewProtocol)) {
            errors.add(pointer + ".protocol 必须与 fixture protocol 一致。");
        }
        validateProviderProtocol(previewProviderType, previewProtocol, pointer, errors);
        validateOfficialProviderProtocolBan(previewProviderType, previewProtocol, pointer, errors);
    }

    private void validateClassificationEvidence(
            JsonNode fixture,
            String classification,
            String pointer,
            List<String> errors) {
        if ("PASS".equals(classification)) {
            JsonNode httpStatus = fixture.path("httpStatus");
            if (!httpStatus.isNumber() || httpStatus.asInt() < 200 || httpStatus.asInt() >= 300) {
                errors.add(pointer + ".httpStatus 必须为 PASS 样本记录 2xx 状态。");
            }
            return;
        }
        if ("FAIL".equals(classification)) {
            if (!fixture.path("failureType").isTextual() || fixture.path("failureType").asText().isBlank()) {
                errors.add(pointer + ".failureType 必须为 FAIL 样本记录失败类型。");
            }
            if (!fixture.path("failureMessage").isTextual() || fixture.path("failureMessage").asText().isBlank()) {
                errors.add(pointer + ".failureMessage 必须为 FAIL 样本记录失败信息。");
            }
            return;
        }
        if ("UNSUPPORTED".equals(classification)) {
            if (!fixture.path("skippedReason").isTextual() || fixture.path("skippedReason").asText().isBlank()) {
                errors.add(pointer + ".skippedReason 必须为 UNSUPPORTED 样本记录阻断原因。");
            }
            JsonNode evidence = fixture.path("evidence");
            if (!evidence.isObject() || evidence.path("reason").asText("").isBlank()) {
                errors.add(pointer + ".evidence.reason 必须为 UNSUPPORTED 样本记录 unsupported reason。");
            }
        }
    }

    private void validateOptionalStatus(JsonNode fixture, String pointer, List<String> errors) {
        JsonNode httpStatus = fixture.path("httpStatus");
        if (!httpStatus.isMissingNode() && (!httpStatus.isNumber()
                || httpStatus.asInt() < 100
                || httpStatus.asInt() > 599)) {
            errors.add(pointer + ".httpStatus 必须是 100-599 的数字。");
        }
        JsonNode durationMs = fixture.path("durationMs");
        if (!durationMs.isMissingNode() && (!durationMs.isNumber() || durationMs.asLong() < 0)) {
            errors.add(pointer + ".durationMs 必须是非负数字。");
        }
    }

    private void validateSummary(JsonNode summary, Map<String, Integer> fixtureCounts, List<String> errors) {
        if (!summary.isObject()) {
            errors.add("$.summary 必须是对象。");
            return;
        }
        for (String classification : CLASSIFICATIONS) {
            JsonNode value = summary.path(classification);
            if (value.isMissingNode()) {
                errors.add("$.summary 缺少 " + classification + "。");
                continue;
            }
            if (!value.isNumber() || value.asInt() < 0) {
                errors.add("$.summary." + classification + " 必须是非负数字。");
                continue;
            }
            int expected = fixtureCounts.getOrDefault(classification, 0);
            if (value.asInt() != expected) {
                errors.add("$.summary." + classification + " 与 fixture 计数不一致，summary="
                        + value.asInt() + " fixture=" + expected + "。");
            }
        }
    }

    private Map<String, Integer> emptyCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String classification : CLASSIFICATIONS) {
            counts.put(classification, 0);
        }
        return counts;
    }

    private String requireText(JsonNode node, String field, String pointer, List<String> errors) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            errors.add(pointer + " 必须是非空字符串。");
            return "";
        }
        return value.asText();
    }

    private void expectText(JsonNode node, String field, String expected, String pointer, List<String> errors) {
        String actual = requireText(node, field, pointer, errors);
        if (!actual.isBlank() && !expected.equals(actual)) {
            errors.add(pointer + " 必须为 " + expected + "，当前为 " + actual + "。");
        }
    }

    private void requireBoolean(JsonNode node, String field, String pointer, List<String> errors) {
        if (!node.path(field).isBoolean()) {
            errors.add(pointer + " 必须是 boolean。");
        }
    }

    private void requireObject(JsonNode node, String field, String pointer, List<String> errors) {
        if (!node.path(field).isObject()) {
            errors.add(pointer + " 必须是对象。");
        }
    }

    private void scanSensitiveValues(JsonNode node, String pointer, List<String> errors) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            String value = node.asText();
            for (SensitivePattern pattern : SENSITIVE_PATTERNS) {
                if (pattern.pattern().matcher(value).find()) {
                    errors.add(pointer + " 包含未脱敏敏感信息：" + pattern.name() + "。");
                }
            }
            return;
        }
        if (node.isArray()) {
            int index = 0;
            for (JsonNode child : node) {
                scanSensitiveValues(child, pointer + "[" + index + "]", errors);
                index++;
            }
            return;
        }
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                String key = entry.getKey();
                if (isSensitiveKey(key)
                        && entry.getValue().isTextual()
                        && !isRedactedSensitiveValue(entry.getValue().asText())) {
                    errors.add(pointer + "." + key + " 包含未脱敏敏感字段值。");
                }
                scanSensitiveValues(entry.getValue(), pointer + "." + key, errors);
            });
        }
    }

    private boolean isRedactedSensitiveValue(String value) {
        return "***".equals(value)
                || "redacted".equals(value)
                || "Bearer ***".equals(value);
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

    record VerificationResult(boolean valid, List<String> errors) {

        static VerificationResult passed() {
            return new VerificationResult(true, List.of());
        }

        static VerificationResult failed(List<String> errors) {
            return new VerificationResult(false, List.copyOf(errors));
        }
    }

    private record SensitivePattern(String name, Pattern pattern) {
    }
}
