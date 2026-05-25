package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.FunctionalProviderSmokeItemResponse;
import com.prodigalgal.xaigateway.admin.api.FunctionalProviderSmokeResponse;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FunctionalProviderSmokeCertificationServiceTests {

    @Test
    void shouldBuildPartialCertificationWithProviderProtocolModelAndRedaction() {
        FunctionalProviderSmokeCertificationService service = new FunctionalProviderSmokeCertificationService();
        FunctionalProviderSmokeResponse smoke = smokeResponse(false, Map.of(
                "PASS", 1,
                "FAIL", 0,
                "SKIPPED", 0,
                "UNSUPPORTED", 0,
                "NO_PERMISSION", 0,
                "BUDGET_BLOCKED", 1
        ));

        var certification = service.certify(smoke, Instant.parse("2026-05-19T01:00:00Z"));
        Map<String, Object> metadata = service.metadata(certification);

        assertEquals("PARTIAL_CERTIFIED", certification.certificationStatus());
        assertEquals(2, certification.fixtureSnapshots().size());
        assertEquals(FunctionalProviderSmokeCertificationService.RECORD_REPLAY_SCHEMA_VERSION,
                certification.recordReplayFixture().schemaVersion());
        assertEquals("record_replay", certification.recordReplayFixture().replayMode());
        assertEquals("XIAOMI_MIMO", certification.recordReplayFixture().providerType());
        assertEquals("XIAOMI_MIMO_OPENAI_COMPATIBLE", certification.recordReplayFixture().protocol());
        assertEquals("api.mimo-v2.com", certification.recordReplayFixture().baseUrlHost());
        assertEquals("XIAOMI_MIMO", certification.fixtureSnapshots().getFirst().providerType());
        assertEquals("XIAOMI_MIMO_OPENAI_COMPATIBLE", certification.fixtureSnapshots().getFirst().protocol());
        assertEquals("mimo-v2-pro", certification.fixtureSnapshots().getFirst().model());
        assertEquals("replay_only", certification.recordReplayFixture().replayPolicy().get("billableOperations"));
        assertEquals(true, certification.recordReplayFixture().replayPolicy().get("liveExecutionRequiresAllowLive"));
        String rendered = metadata.toString();
        assertFalse(rendered.contains("mimo-live-secret"));
        assertFalse(rendered.contains("api-key=mimo"));
        assertFalse(rendered.contains("AIzaSy"));
        assertFalse(rendered.contains("Bearer sk-"));
        assertEquals("PARTIAL_CERTIFIED", metadata.get("certificationStatus"));
    }

    @Test
    void shouldBuildRecordReplayFixtureAcceptedByVerifier() {
        FunctionalProviderSmokeCertificationService service = new FunctionalProviderSmokeCertificationService();
        FunctionalProviderSmokeResponse smoke = smokeResponse(false, Map.of(
                "PASS", 1,
                "FAIL", 0,
                "SKIPPED", 0,
                "UNSUPPORTED", 0,
                "NO_PERMISSION", 0,
                "BUDGET_BLOCKED", 1
        ));

        var certification = service.certify(smoke, Instant.parse("2026-05-19T01:00:00Z"));
        var metadata = service.metadata(certification).get("recordReplayFixture");
        var result = new FunctionalProviderSmokeRecordReplayFixtureVerifier()
                .validate(new ObjectMapper().valueToTree(metadata));

        assertEquals(true, result.valid(), () -> String.join("\n", result.errors()));
    }

    @Test
    void shouldPreserveCohereNativeProviderAndProtocolInCertificationFixture() {
        FunctionalProviderSmokeCertificationService service = new FunctionalProviderSmokeCertificationService();
        FunctionalProviderSmokeResponse smoke = new FunctionalProviderSmokeResponse(
                21L,
                "DRY_RUN_READY",
                "SKIPPED",
                "DRY_RUN",
                "https://api.cohere.ai",
                ProviderType.OPENAI_COMPATIBLE,
                "COHERE_NATIVE",
                true,
                false,
                true,
                null,
                "fingerprint",
                Instant.parse("2026-05-24T00:00:00Z"),
                "message",
                Map.of(
                        "PASS", 0,
                        "FAIL", 0,
                        "SKIPPED", 1,
                        "UNSUPPORTED", 0,
                        "NO_PERMISSION", 0,
                        "BUDGET_BLOCKED", 0
                ),
                List.of(new FunctionalProviderSmokeItemResponse(
                        ProviderType.OPENAI_COMPATIBLE,
                        "COHERE_NATIVE",
                        "EMBEDDINGS",
                        "DRY_RUN_READY",
                        "SKIPPED",
                        "DRY_RUN",
                        "POST",
                        "/v2/embed",
                        "embed-v4.0",
                        true,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Map.of("probeKind", "cohere_native_embed"),
                        Map.of(
                                "baseUrl", "https://api.cohere.ai",
                                "headers", Map.of("authorization", "Bearer cohere-secret")
                        )
                ))
        );

        var certification = service.certify(smoke, Instant.parse("2026-05-24T00:00:00Z"));
        var result = new FunctionalProviderSmokeRecordReplayFixtureVerifier()
                .validate(new ObjectMapper().valueToTree(service.metadata(certification).get("recordReplayFixture")));

        assertEquals("COHERE", certification.recordReplayFixture().providerType());
        assertEquals("COHERE_NATIVE", certification.recordReplayFixture().protocol());
        assertEquals("COHERE", certification.fixtureSnapshots().getFirst().providerType());
        assertEquals("COHERE_NATIVE", certification.fixtureSnapshots().getFirst().protocol());
        assertEquals(true, result.valid(), () -> String.join("\n", result.errors()));
        assertFalse(service.metadata(certification).toString().contains("cohere-secret"));
    }

    @Test
    void shouldNormalizeDeepSeekRecordReplayProviderAndProtocol() {
        FunctionalProviderSmokeCertificationService service = new FunctionalProviderSmokeCertificationService();
        FunctionalProviderSmokeResponse smoke = providerSpecificOpenAiSmoke(
                "https://api.deepseek.com",
                "OPENAI_COMPATIBLE",
                "deepseek-chat",
                "req_deepseek_chat"
        );

        var certification = service.certify(smoke, Instant.parse("2026-05-19T01:00:00Z"));
        var fixture = certification.recordReplayFixture();
        var result = new FunctionalProviderSmokeRecordReplayFixtureVerifier()
                .validate(new ObjectMapper().valueToTree(service.metadata(certification).get("recordReplayFixture")));

        assertEquals("DEEPSEEK", fixture.providerType());
        assertEquals("DEEPSEEK_OPENAI_COMPATIBLE", fixture.protocol());
        assertEquals("DEEPSEEK", fixture.fixtures().getFirst().providerType());
        assertEquals("DEEPSEEK_OPENAI_COMPATIBLE", fixture.fixtures().getFirst().protocol());
        assertEquals(true, result.valid(), () -> String.join("\n", result.errors()));
    }

    @Test
    void shouldNormalizeXaiRecordReplayProviderAndProtocol() {
        FunctionalProviderSmokeCertificationService service = new FunctionalProviderSmokeCertificationService();
        FunctionalProviderSmokeResponse smoke = providerSpecificOpenAiSmoke(
                "https://api.x.ai/v1",
                "OPENAI_COMPATIBLE",
                "grok-4.3",
                "req_xai_chat"
        );

        var certification = service.certify(smoke, Instant.parse("2026-05-19T01:00:00Z"));
        var fixture = certification.recordReplayFixture();
        var result = new FunctionalProviderSmokeRecordReplayFixtureVerifier()
                .validate(new ObjectMapper().valueToTree(service.metadata(certification).get("recordReplayFixture")));

        assertEquals("XAI", fixture.providerType());
        assertEquals("XAI_OPENAI_COMPATIBLE", fixture.protocol());
        assertEquals("XAI", fixture.fixtures().getFirst().providerType());
        assertEquals("XAI_OPENAI_COMPATIBLE", fixture.fixtures().getFirst().protocol());
        assertEquals(true, result.valid(), () -> String.join("\n", result.errors()));
    }

    @Test
    void shouldNormalizeQwenRecordReplayProviderProtocolAndContractPath() {
        FunctionalProviderSmokeCertificationService service = new FunctionalProviderSmokeCertificationService();
        FunctionalProviderSmokeResponse smoke = providerSpecificOpenAiSmoke(
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "OPENAI_COMPATIBLE",
                "qwen-plus",
                "req_qwen_chat"
        );

        var certification = service.certify(smoke, Instant.parse("2026-05-24T02:00:00Z"));
        var fixture = certification.recordReplayFixture();
        var result = new FunctionalProviderSmokeRecordReplayFixtureVerifier()
                .validate(new ObjectMapper().valueToTree(service.metadata(certification).get("recordReplayFixture")));

        assertEquals("QWEN", fixture.providerType());
        assertEquals("QWEN_OPENAI_COMPATIBLE", fixture.protocol());
        assertEquals("QWEN", fixture.fixtures().getFirst().providerType());
        assertEquals("QWEN_OPENAI_COMPATIBLE", fixture.fixtures().getFirst().protocol());
        assertEquals("/compatible-mode/v1/chat/completions", fixture.fixtures().getFirst().path());
        assertEquals(true, result.valid(), () -> String.join("\n", result.errors()));
    }

    @Test
    void shouldMarkDryRunCertificationWithoutPromotingToCertified() {
        FunctionalProviderSmokeCertificationService service = new FunctionalProviderSmokeCertificationService();
        FunctionalProviderSmokeResponse smoke = new FunctionalProviderSmokeResponse(
                9L,
                "DRY_RUN_READY",
                "SKIPPED",
                "DRY_RUN",
                "https://generativelanguage.googleapis.com",
                ProviderType.GEMINI_DIRECT,
                "GEMINI_NATIVE",
                true,
                false,
                true,
                null,
                "fingerprint",
                Instant.parse("2026-05-19T01:00:00Z"),
                "message",
                Map.of(
                        "PASS", 0,
                        "FAIL", 0,
                        "SKIPPED", 1,
                        "UNSUPPORTED", 0,
                        "NO_PERMISSION", 0,
                        "BUDGET_BLOCKED", 0
                ),
                List.of(new FunctionalProviderSmokeItemResponse(
                        ProviderType.GEMINI_DIRECT,
                        "GEMINI_NATIVE",
                        "GENERATE_CONTENT",
                        "DRY_RUN_READY",
                        "SKIPPED",
                        "DRY_RUN",
                        "POST",
                        "/v1beta/models/gemini-2.5-flash:generateContent",
                        "gemini-2.5-flash",
                        true,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Map.of("probeKind", "gemini_generate_content"),
                        Map.of("headers", Map.of("x-goog-api-key", "***"))
                ))
        );

        var certification = service.certify(smoke, Instant.parse("2026-05-19T01:00:00Z"));

        assertEquals("DRY_RUN", certification.certificationStatus());
        assertEquals("DRY_RUN", certification.recordReplayFixture().certificationStatus());
        assertEquals(true, certification.recordReplayFixture().dryRun());
        assertEquals("generativelanguage.googleapis.com", certification.recordReplayFixture().baseUrlHost());
    }

    @Test
    void shouldKeepRecordReplaySampleFixtureParseableAndRedacted() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        var root = objectMapper.readTree(Files.readString(Path.of(
                "src/test/resources/conformance/functional-provider-smoke-record-replay-fixture.sample.json")));

        assertEquals(FunctionalProviderSmokeCertificationService.RECORD_REPLAY_SCHEMA_VERSION,
                root.path("schemaVersion").asText());
        assertEquals("record_replay", root.path("replayMode").asText());
        assertEquals("disabled_by_default", root.path("replayPolicy").path("network").asText());
        assertEquals("XIAOMI_MIMO", root.path("providerType").asText());
        assertEquals("XIAOMI_MIMO_OPENAI_COMPATIBLE", root.path("fixtures").path(0).path("protocol").asText());
        assertEquals("mimo-v2-pro", root.path("fixtures").path(0).path("model").asText());
        String rendered = root.toString();
        assertFalse(rendered.contains("mimo-live-secret"));
        assertFalse(rendered.contains("api-key=mimo"));
        assertFalse(rendered.contains("Bearer sk-"));
        assertFalse(rendered.contains("AIzaSy"));
    }

    private FunctionalProviderSmokeResponse smokeResponse(boolean dryRun, Map<String, Integer> summary) {
        return new FunctionalProviderSmokeResponse(
                17L,
                dryRun ? "DRY_RUN_READY" : "LIVE_SMOKE_COMPLETED",
                dryRun ? "SKIPPED" : "PASS",
                dryRun ? "DRY_RUN" : null,
                "https://api.mimo-v2.com",
                ProviderType.OPENAI_COMPATIBLE,
                "OPENAI_COMPATIBLE",
                dryRun,
                !dryRun,
                true,
                null,
                "fingerprint",
                Instant.parse("2026-05-19T01:00:00Z"),
                "message",
                summary,
                List.of(
                        new FunctionalProviderSmokeItemResponse(
                                ProviderType.OPENAI_COMPATIBLE,
                                "OPENAI_COMPATIBLE",
                                "CHAT_COMPLETIONS",
                                "LIVE_SMOKE_OK",
                                "PASS",
                                null,
                                "POST",
                                "/v1/chat/completions",
                                "mimo-v2-pro",
                                true,
                                false,
                                200,
                                "req_mimo_chat",
                                18L,
                                null,
                                null,
                                Map.of("object", "chat.completion", "model", "mimo-v2-pro"),
                                Map.of("headers", Map.of("authorization", "Bearer mimo-live-secret"))
                        ),
                        new FunctionalProviderSmokeItemResponse(
                                ProviderType.OPENAI_COMPATIBLE,
                                "OPENAI_COMPATIBLE",
                                "CHAT_TOOLS",
                                "BUDGET_GUARD_BLOCKED",
                                "BUDGET_BLOCKED",
                                "BILLABLE_PROBE_BLOCKED",
                                "POST",
                                "/v1/chat/completions",
                                "mimo-v2-pro",
                                true,
                                false,
                                null,
                                null,
                                null,
                                null,
                                "blocked Bearer sk-live-secret " + "AIzaSy" + "BPM5panpM3zawPoYSUZ1JGTmzCPzr-R-s",
                                Map.of("probeKind", "openai_compatible_chat_tools"),
                                Map.of("headers", Map.of("authorization", "Bearer ***"))
                        )
                )
        );
    }

    private FunctionalProviderSmokeResponse providerSpecificOpenAiSmoke(
            String baseUrl,
            String protocol,
            String model,
            String requestId) {
        return new FunctionalProviderSmokeResponse(
                18L,
                "LIVE_SMOKE_COMPLETED",
                "PASS",
                null,
                baseUrl,
                ProviderType.OPENAI_COMPATIBLE,
                protocol,
                false,
                true,
                true,
                null,
                "fingerprint",
                Instant.parse("2026-05-19T01:00:00Z"),
                "message",
                Map.of(
                        "PASS", 1,
                        "FAIL", 0,
                        "SKIPPED", 0,
                        "UNSUPPORTED", 0,
                        "NO_PERMISSION", 0,
                        "BUDGET_BLOCKED", 0
                ),
                List.of(new FunctionalProviderSmokeItemResponse(
                        ProviderType.OPENAI_COMPATIBLE,
                        protocol,
                        "CHAT_COMPLETIONS",
                        "LIVE_SMOKE_OK",
                        "PASS",
                        null,
                        "POST",
                        providerSpecificPath(baseUrl),
                        model,
                        true,
                        false,
                        200,
                        requestId,
                        18L,
                        null,
                        null,
                        Map.of("object", "chat.completion", "model", model),
                        Map.of(
                                "providerType", ProviderType.OPENAI_COMPATIBLE.name(),
                                "protocol", protocol,
                                "baseUrl", baseUrl,
                                "headers", Map.of("authorization", "Bearer ***")
                        )
                ))
        );
    }

    private String providerSpecificPath(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("dashscope.aliyuncs.com")) {
            return "/compatible-mode/v1/chat/completions";
        }
        if (normalized.contains("volces.com")) {
            return "/api/v3/chat/completions";
        }
        if (normalized.contains("perplexity.ai")) {
            return "/chat/completions";
        }
        return "/v1/chat/completions";
    }
}
