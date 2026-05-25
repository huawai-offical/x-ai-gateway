package com.prodigalgal.xaigateway.admin.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionalProviderSmokeRecordReplayFixtureVerifierTests {

    private static final Path SAMPLE = Path.of(
            "src/test/resources/conformance/functional-provider-smoke-record-replay-fixture.sample.json");
    private static final String COHERE_SAMPLE =
            "src/test/resources/conformance/functional-provider-smoke-record-replay-fixture.cohere.sample.json";
    private static final String JINA_SAMPLE =
            "src/test/resources/conformance/functional-provider-smoke-record-replay-fixture.jina.sample.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FunctionalProviderSmokeRecordReplayFixtureVerifier verifier =
            new FunctionalProviderSmokeRecordReplayFixtureVerifier();

    @Test
    void shouldAcceptRepositorySampleFixture() throws Exception {
        var root = objectMapper.readTree(Files.readString(SAMPLE));
        var result = verifier.validate(root);

        assertTrue(result.valid(), () -> String.join("\n", result.errors()));
        assertEquals("XIAOMI_MIMO", root.path("providerType").asText());
        assertEquals("XIAOMI_MIMO_OPENAI_COMPATIBLE", root.path("protocol").asText());
        assertEquals("XIAOMI_MIMO", root.path("fixtures").path(0).path("providerType").asText());
        assertEquals("XIAOMI_MIMO_OPENAI_COMPATIBLE", root.path("fixtures").path(0).path("protocol").asText());
        assertEquals("XIAOMI_MIMO", root.path("fixtures").path(0).path("requestPreview").path("providerType").asText());
        assertEquals("XIAOMI_MIMO_OPENAI_COMPATIBLE",
                root.path("fixtures").path(0).path("requestPreview").path("protocol").asText());
        assertEquals(1, root.path("summary").path("PASS").asInt());
        assertEquals(1, root.path("summary").path("FAIL").asInt());
        assertEquals(1, root.path("summary").path("UNSUPPORTED").asInt());
        assertEquals(1, root.path("summary").path("BUDGET_BLOCKED").asInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            COHERE_SAMPLE,
            JINA_SAMPLE
    })
    void shouldAcceptNativeEmbedRerankProviderSampleFixtures(String samplePath) throws Exception {
        var root = objectMapper.readTree(Files.readString(Path.of(samplePath)));
        var result = verifier.validate(root);

        assertTrue(result.valid(), () -> String.join("\n", result.errors()));
        assertNativeEmbedRerankSampleBoundary(root);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            COHERE_SAMPLE,
            JINA_SAMPLE
    })
    void shouldRejectNativeProviderSampleWhenProtocolRegressesToOpenAiCompatible(String samplePath) throws Exception {
        String unsafe = Files.readString(Path.of(samplePath))
                .replace("\"protocol\": \"COHERE_NATIVE\"", "\"protocol\": \"OPENAI_COMPATIBLE\"")
                .replace("\"protocol\": \"JINA_NATIVE\"", "\"protocol\": \"OPENAI_COMPATIBLE\"");

        var result = verifier.validate(objectMapper.readTree(unsafe));

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("OPENAI_COMPATIBLE")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            COHERE_SAMPLE,
            JINA_SAMPLE
    })
    void shouldRejectNativeProviderSampleWhenNonEmbedRerankFamilyIsMarkedPass(String samplePath) throws Exception {
        String unsafe = Files.readString(Path.of(samplePath))
                .replace("\"classification\": \"PASS\"", "\"classification\": \"SKIPPED\"")
                .replace("\"classification\": \"UNSUPPORTED\"", "\"classification\": \"PASS\"")
                .replace("\"httpStatus\": 200,", "")
                .replace("\"method\": \"POST\",\r\n      \"path\": \"/v2/embed\",",
                        "\"method\": \"POST\",\r\n      \"path\": \"/v2/embed\",\r\n      \"httpStatus\": 200,")
                .replace("\"method\": \"POST\",\r\n      \"path\": \"/v1/embeddings\",",
                        "\"method\": \"POST\",\r\n      \"path\": \"/v1/embeddings\",\r\n      \"httpStatus\": 200,")
                .replace("\"PASS\": 1", "\"PASS\": 1")
                .replace("\"SKIPPED\": 0", "\"SKIPPED\": 1")
                .replace("\"UNSUPPORTED\": 1", "\"UNSUPPORTED\": 0");

        var result = verifier.validate(objectMapper.readTree(unsafe));

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("只能记录 UNSUPPORTED")));
    }

    @Test
    void shouldRejectUnsafeReplayPolicy() throws Exception {
        String unsafe = Files.readString(SAMPLE)
                .replace("\"network\": \"disabled_by_default\"", "\"network\": \"live_allowed\"")
                .replace("\"billableExecutionRequiresAllowBillableProbes\": true",
                        "\"billableExecutionRequiresAllowBillableProbes\": false");

        var result = verifier.validate(objectMapper.readTree(unsafe));

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("$.replayPolicy.network")));
    }

    @Test
    void shouldRejectUnredactedSecretMaterial() throws Exception {
        String unsafe = Files.readString(SAMPLE)
                .replace("Bearer ***", "Bearer mimo-live-secret")
                .replace("blocked Bearer ***", "blocked Bearer mimo-live-secret")
                .replace("req_mimo_chat", "Bearer sk-live-secret")
                .replace("mimo-v2-pro", "AIzaSy" + "BPM5panpM3zawPoYSUZ1JGTmzCPzr-R-s");

        var result = verifier.validate(objectMapper.readTree(unsafe));

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("未脱敏敏感字段值")));
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("bearer-token")));
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("google-ai-studio-key")));
    }

    @Test
    void shouldRejectMissingProviderProtocolAndModelFields() throws Exception {
        String missing = Files.readString(SAMPLE)
                .replaceFirst("\\s*\"protocol\"\\s*:\\s*\"XIAOMI_MIMO_OPENAI_COMPATIBLE\"\\s*,", "")
                .replaceFirst("\\s*\"model\"\\s*:\\s*\"mimo-v2-pro\"\\s*,", "");

        var result = verifier.validate(objectMapper.readTree(missing));

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("$.protocol")));
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("$.fixtures[0].model")));
    }

    @Test
    void shouldRejectPathOutsideProtocolScope() throws Exception {
        String unsafe = Files.readString(SAMPLE)
                .replace("\"path\": \"/v1/chat/completions\"", "\"path\": \"/v1/responses\"");

        var result = verifier.validate(objectMapper.readTree(unsafe));

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("Chat Completions 路径")));
    }

    @Test
    void shouldAcceptCohereNativeEmbedRerankFixtureAndRejectChatPath() throws Exception {
        var root = objectMapper.createObjectNode();
        root.put("schemaVersion", FunctionalProviderSmokeCertificationService.RECORD_REPLAY_SCHEMA_VERSION);
        root.put("replayMode", "record_replay");
        root.put("providerType", "COHERE");
        root.put("protocol", "COHERE_NATIVE");
        root.put("baseUrl", "https://api.cohere.ai");
        root.put("baseUrlHost", "api.cohere.ai");
        root.put("certificationStatus", "UNSUPPORTED");
        root.put("dryRun", true);
        root.put("recordedAt", "2026-05-24T00:00:00Z");
        var summary = root.putObject("summary");
        summary.put("PASS", 0);
        summary.put("FAIL", 0);
        summary.put("SKIPPED", 0);
        summary.put("UNSUPPORTED", 1);
        summary.put("NO_PERMISSION", 0);
        summary.put("BUDGET_BLOCKED", 0);
        var policy = root.putObject("replayPolicy");
        policy.put("network", "disabled_by_default");
        policy.put("billableOperations", "replay_only");
        policy.put("writeOperations", "replay_only");
        policy.put("secretMaterial", "redacted");
        policy.put("dryRunEvidenceAccepted", true);
        policy.put("fixtureSource", "functional_provider_smoke_certification");
        policy.put("liveExecutionRequiresAllowLive", true);
        policy.put("billableExecutionRequiresAllowBillableProbes", true);
        var fixture = root.putArray("fixtures").addObject();
        fixture.put("providerType", "COHERE");
        fixture.put("protocol", "COHERE_NATIVE");
        fixture.put("resourceFamily", "CHAT_COMPLETIONS");
        fixture.put("status", "OUT_OF_SCOPE");
        fixture.put("classification", "UNSUPPORTED");
        fixture.put("skippedReason", "OUT_OF_FUNCTIONAL_API_SCOPE");
        fixture.put("method", "POST");
        fixture.put("path", "/v2/embed");
        fixture.put("model", "embed-v4.0");
        fixture.put("billable", false);
        fixture.put("writeOperation", false);
        fixture.putObject("evidence").put("reason", "UNSUPPORTED_FAMILY");
        var preview = fixture.putObject("requestPreview");
        preview.put("providerType", "COHERE");
        preview.put("protocol", "COHERE_NATIVE");
        preview.put("method", "POST");
        preview.put("baseUrl", "https://api.cohere.ai");
        preview.put("path", "/v2/embed");
        preview.put("model", "embed-v4.0");
        preview.putObject("headers").put("authorization", "Bearer ***");

        var result = verifier.validate(root);
        assertTrue(result.valid(), () -> String.join("\n", result.errors()));

        ((tools.jackson.databind.node.ObjectNode) fixture).put("path", "/v1/chat/completions");
        var rejected = verifier.validate(root);
        assertFalse(rejected.valid());
        assertTrue(rejected.errors().stream().anyMatch(item -> item.contains("Cohere native embed/rerank 路径")));
    }

    @Test
    void shouldRejectNonCoreProviderFixture() throws Exception {
        String dify = Files.readString(SAMPLE)
                .replace("\"providerType\": \"XIAOMI_MIMO\"", "\"providerType\": \"DIFY\"");

        var result = verifier.validate(objectMapper.readTree(dify));

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("$.providerType 不在允许集合内：DIFY")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "OPENAI_COMPATIBLE",
            "DIFY",
            "OPENROUTER",
            "TOGETHER",
            "FIREWORKS",
            "SILICONFLOW"
    })
    void shouldRejectForbiddenOfficialProviderAndProtocolFixtureValues(String forbidden) throws Exception {
        String unsafe = Files.readString(SAMPLE)
                .replace("\"providerType\": \"XIAOMI_MIMO\"", "\"providerType\": \"" + forbidden + "\"")
                .replace("\"protocol\": \"XIAOMI_MIMO_OPENAI_COMPATIBLE\"", "\"protocol\": \"" + forbidden + "\"");

        var result = verifier.validate(objectMapper.readTree(unsafe));

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(item ->
                item.contains("不能进入 official functional provider smoke fixture")
                        || item.contains("不在允许集合内：" + forbidden)));
    }

    @Test
    void shouldRejectSummaryThatDoesNotMatchFixtureCounts() throws Exception {
        String mismatch = Files.readString(SAMPLE)
                .replace("\"PASS\": 1", "\"PASS\": 2");

        var result = verifier.validate(objectMapper.readTree(mismatch));

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("$.summary.PASS 与 fixture 计数不一致")));
    }

    private void assertNativeEmbedRerankSampleBoundary(JsonNode root) {
        String providerType = root.path("providerType").asText();
        String protocol = root.path("protocol").asText();
        assertTrue(Set.of("COHERE", "JINA").contains(providerType));
        assertEquals("COHERE".equals(providerType) ? "COHERE_NATIVE" : "JINA_NATIVE", protocol);
        assertFalse(protocol.contains("OPENAI_COMPATIBLE"));
        assertEquals(1, root.path("summary").path("PASS").asInt());
        assertEquals(1, root.path("summary").path("FAIL").asInt());
        assertEquals(1, root.path("summary").path("UNSUPPORTED").asInt());

        boolean unsupportedNonEmbedRerankSeen = false;
        for (JsonNode fixture : root.path("fixtures")) {
            assertEquals(providerType, fixture.path("providerType").asText());
            assertEquals(protocol, fixture.path("protocol").asText());
            assertEquals(providerType, fixture.path("requestPreview").path("providerType").asText());
            assertEquals(protocol, fixture.path("requestPreview").path("protocol").asText());
            assertEquals("Bearer ***", fixture.path("requestPreview").path("headers").path("authorization").asText());
            assertFalse(fixture.path("protocol").asText().contains("OPENAI_COMPATIBLE"));
            assertFalse(fixture.path("requestPreview").path("protocol").asText().contains("OPENAI_COMPATIBLE"));

            String resourceFamily = fixture.path("resourceFamily").asText();
            String classification = fixture.path("classification").asText();
            if ("PASS".equals(classification)) {
                assertTrue(isEmbedRerankFamily(resourceFamily),
                        () -> resourceFamily + " 不能作为 Cohere/Jina native smoke 成功样本。");
            }
            if (!isEmbedRerankFamily(resourceFamily)) {
                assertEquals("UNSUPPORTED", classification);
                unsupportedNonEmbedRerankSeen = true;
            }
        }
        assertTrue(unsupportedNonEmbedRerankSeen);
    }

    private boolean isEmbedRerankFamily(String resourceFamily) {
        return "EMBEDDINGS".equals(resourceFamily) || "RERANK".equals(resourceFamily);
    }
}
