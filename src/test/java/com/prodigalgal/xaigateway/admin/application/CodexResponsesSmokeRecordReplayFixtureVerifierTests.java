package com.prodigalgal.xaigateway.admin.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexResponsesSmokeRecordReplayFixtureVerifierTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldAcceptSampleFixtureWithoutNetworkAccess() throws IOException {
        var root = objectMapper.readTree(Files.readString(Path.of(
                "src/test/resources/conformance/codex-responses-smoke-record-replay-fixture.sample.json"
        )));

        var result = new CodexResponsesSmokeRecordReplayFixtureVerifier().validate(root);

        assertTrue(result.valid(), String.join("\n", result.errors()));
    }

    @Test
    void shouldRejectLeakedBearerToken() throws IOException {
        var root = objectMapper.readTree("""
                {
                  "schemaVersion": "2026-05-19.codex-responses-smoke-record-replay.v1",
                  "replayMode": "record_replay",
                  "providerType": "CODEX_OAUTH",
                  "protocol": "codex-responses",
                  "baseUrl": "https://chatgpt.com/backend-api/codex",
                  "certificationStatus": "DRY_RUN",
                  "dryRun": true,
                  "recordedAt": "2026-05-19T00:00:00Z",
                  "summary": {
                    "PASS": 0,
                    "FAIL": 0,
                    "SKIPPED": 1,
                    "UNSUPPORTED": 0,
                    "NO_PERMISSION": 0,
                    "BUDGET_BLOCKED": 0
                  },
                  "replayPolicy": {
                    "network": "disabled_by_default",
                    "billableOperations": "replay_only",
                    "writeOperations": "replay_only",
                    "secretMaterial": "redacted",
                    "fixtureSource": "codex_responses_smoke",
                    "dryRunEvidenceAccepted": true,
                    "liveExecutionRequiresDryRunFalse": true,
                    "liveExecutionRequiresRouteEligible": true,
                    "liveExecutionRequiresBudgetAvailable": true
                  },
                  "fixtures": [
                    {
                      "resourceFamily": "codex_responses",
                      "status": "DRY_RUN_READY",
                      "classification": "SKIPPED",
                      "skippedReason": "DRY_RUN",
                      "method": "POST",
                      "path": "/backend-api/codex/responses",
                      "model": "gpt-5.4@low",
                      "billable": false,
                      "writeOperation": false,
                      "evidence": {},
                      "requestPreview": {
                        "headers": {
                          "authorization": "Bearer live-token-should-fail"
                        }
                      }
                    }
                  ]
                }
                """);

        var result = new CodexResponsesSmokeRecordReplayFixtureVerifier().validate(root);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("bearer-token")));
    }
}
