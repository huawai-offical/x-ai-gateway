package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.OpenAiDirectResourceSmokeItemResponse;
import com.prodigalgal.xaigateway.admin.api.OpenAiDirectResourceSmokeResponse;
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

class OpenAiDirectSmokeCertificationServiceTests {

    @Test
    void shouldBuildPartialCertificationAndRedactedFixtures() {
        OpenAiDirectSmokeCertificationService service = new OpenAiDirectSmokeCertificationService();
        OpenAiDirectResourceSmokeResponse smoke = smokeResponse(false, Map.of(
                "PASS", 1,
                "FAIL", 0,
                "SKIPPED", 0,
                "UNSUPPORTED", 0,
                "NO_PERMISSION", 0,
                "BUDGET_BLOCKED", 1
        ));

        var certification = service.certify(smoke, Instant.parse("2026-05-16T01:00:00Z"));
        Map<String, Object> metadata = service.metadata(certification);

        assertEquals("PARTIAL_CERTIFIED", certification.certificationStatus());
        assertEquals(2, certification.fixtureSnapshots().size());
        assertEquals(OpenAiDirectSmokeCertificationService.RECORD_REPLAY_SCHEMA_VERSION,
                certification.recordReplayFixture().schemaVersion());
        assertEquals("record_replay", certification.recordReplayFixture().replayMode());
        assertEquals(2, certification.recordReplayFixture().fixtures().size());
        assertEquals("replay_only", certification.recordReplayFixture().replayPolicy().get("billableOperations"));
        String rendered = metadata.toString();
        assertFalse(rendered.contains("sk-live-secret"));
        assertFalse(rendered.contains("Bearer sk-"));
        assertFalse(rendered.contains("org-real"));
        assertFalse(rendered.contains("proj-real"));
        assertEquals("PARTIAL_CERTIFIED", metadata.get("certificationStatus"));
        assertFalse(String.valueOf(metadata.get("recordReplayFixture")).contains("sk-live-secret"));
    }

    @Test
    void shouldMarkDryRunCertificationWithoutPromotingToCertified() {
        OpenAiDirectSmokeCertificationService service = new OpenAiDirectSmokeCertificationService();
        OpenAiDirectResourceSmokeResponse smoke = smokeResponse(true, Map.of(
                "PASS", 0,
                "FAIL", 0,
                "SKIPPED", 6,
                "UNSUPPORTED", 0,
                "NO_PERMISSION", 0,
                "BUDGET_BLOCKED", 0
        ));

        var certification = service.certify(smoke, Instant.parse("2026-05-16T01:00:00Z"));

        assertEquals("DRY_RUN", certification.certificationStatus());
        assertEquals("DRY_RUN", certification.recordReplayFixture().certificationStatus());
        assertEquals(true, certification.recordReplayFixture().dryRun());
    }

    @Test
    void shouldKeepRecordReplaySampleFixtureParseableAndRedacted() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        var root = objectMapper.readTree(Files.readString(Path.of(
                "src/test/resources/conformance/openai-direct-smoke-record-replay-fixture.sample.json")));

        assertEquals(OpenAiDirectSmokeCertificationService.RECORD_REPLAY_SCHEMA_VERSION,
                root.path("schemaVersion").asText());
        assertEquals("record_replay", root.path("replayMode").asText());
        assertEquals("disabled_by_default", root.path("replayPolicy").path("network").asText());
        assertEquals("FILES", root.path("fixtures").path(0).path("resourceFamily").asText());
        String rendered = root.toString();
        assertFalse(rendered.contains("sk-live-secret"));
        assertFalse(rendered.contains("Bearer sk-"));
        assertFalse(rendered.contains("org-real"));
        assertFalse(rendered.contains("proj-real"));
    }

    private OpenAiDirectResourceSmokeResponse smokeResponse(boolean dryRun, Map<String, Integer> summary) {
        return new OpenAiDirectResourceSmokeResponse(
                7L,
                dryRun ? "DRY_RUN_READY" : "LIVE_SMOKE_COMPLETED",
                dryRun ? "SKIPPED" : "PASS",
                dryRun ? "DRY_RUN" : null,
                "https://api.openai.com",
                ProviderType.OPENAI_DIRECT,
                dryRun,
                true,
                null,
                "fingerprint",
                Instant.parse("2026-05-16T01:00:00Z"),
                "message",
                summary,
                List.of(
                        new OpenAiDirectResourceSmokeItemResponse(
                                "FILES",
                                "LIVE_SMOKE_OK",
                                "PASS",
                                null,
                                "GET",
                                "/v1/files?limit=1",
                                false,
                                false,
                                200,
                                "req_files",
                                12L,
                                null,
                                null,
                                Map.of("firstId", "file_1"),
                                Map.of("headers", Map.of("authorization", "Bearer sk-live-secret", "OpenAI-Organization", "org-real"))
                        ),
                        new OpenAiDirectResourceSmokeItemResponse(
                                "CHAT_COMPLETIONS",
                                "BUDGET_GUARD_BLOCKED",
                                "BUDGET_BLOCKED",
                                "BILLABLE_PROBE_BLOCKED",
                                "POST",
                                "/v1/chat/completions",
                                true,
                                false,
                                null,
                                null,
                                null,
                                null,
                                "blocked Bearer sk-live-secret for org-real proj-real",
                                Map.of("probeKind", "billable_generation"),
                                Map.of("headers", Map.of("authorization", "Bearer ***", "OpenAI-Project", "proj-real"))
                        )
                )
        );
    }
}
