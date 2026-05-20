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
            "OPENAI_COMPATIBLE",
            "ANTHROPIC_DIRECT"
    );

    private static final Set<String> PROTOCOLS = Set.of(
            "GEMINI_NATIVE",
            "OPENAI_COMPATIBLE",
            "ANTHROPIC_COMPATIBLE"
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
        Map<String, Integer> fixtureCounts = validateFixtures(root.path("fixtures"), root.path("protocol").asText(""), errors);
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

    private Map<String, Integer> validateFixtures(JsonNode fixtures, String topLevelProtocol, List<String> errors) {
        Map<String, Integer> counts = emptyCounts();
        if (!fixtures.isArray()) {
            errors.add("$.fixtures 必须是数组。");
            return counts;
        }

        int index = 0;
        for (JsonNode fixture : fixtures) {
            validateFixture(fixture, index, topLevelProtocol, counts, errors);
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
        String protocol = requireText(fixture, "protocol", pointer + ".protocol", errors);
        if (!protocol.isBlank() && !PROTOCOLS.contains(protocol)) {
            errors.add(pointer + ".protocol 不在允许集合内：" + protocol);
        }
        if (!topLevelProtocol.isBlank() && !protocol.isBlank() && !topLevelProtocol.equals(protocol)) {
            errors.add(pointer + ".protocol 必须与顶层 protocol 一致。");
        }
        requireText(fixture, "resourceFamily", pointer + ".resourceFamily", errors);
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
        if ("OPENAI_COMPATIBLE".equals(protocol)) {
            if (!"/v1/chat/completions".equals(path)) {
                errors.add(pointer + " 必须是 OpenAI-compatible Chat Completions 路径。");
            }
            return;
        }
        if ("ANTHROPIC_COMPATIBLE".equals(protocol) && !"/v1/messages".equals(path)) {
            errors.add(pointer + " 必须是 Anthropic-compatible Messages 路径。");
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
                        && !"***".equals(entry.getValue().asText())
                        && !"redacted".equals(entry.getValue().asText())) {
                    errors.add(pointer + "." + key + " 包含未脱敏敏感字段值。");
                }
                scanSensitiveValues(entry.getValue(), pointer + "." + key, errors);
            });
        }
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
