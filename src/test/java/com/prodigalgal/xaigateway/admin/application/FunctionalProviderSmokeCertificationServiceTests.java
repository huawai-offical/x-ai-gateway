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
        assertEquals(ProviderType.OPENAI_COMPATIBLE, certification.recordReplayFixture().providerType());
        assertEquals("OPENAI_COMPATIBLE", certification.recordReplayFixture().protocol());
        assertEquals("api.mimo-v2.com", certification.recordReplayFixture().baseUrlHost());
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
        assertEquals("OPENAI_COMPATIBLE", root.path("providerType").asText());
        assertEquals("OPENAI_COMPATIBLE", root.path("fixtures").path(0).path("protocol").asText());
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
                                Map.of("headers", Map.of("api-key", "mimo-live-secret"))
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
                                "blocked api-key=mimo-live-secret Bearer sk-live-secret " + "AIzaSy" + "BPM5panpM3zawPoYSUZ1JGTmzCPzr-R-s",
                                Map.of("probeKind", "openai_compatible_chat_tools"),
                                Map.of("headers", Map.of("api-key", "***"))
                        )
                )
        );
    }
}
