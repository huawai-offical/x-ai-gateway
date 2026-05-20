package com.prodigalgal.xaigateway.admin.application;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionalProviderSmokeRecordReplayFixtureVerifierTests {

    private static final Path SAMPLE = Path.of(
            "src/test/resources/conformance/functional-provider-smoke-record-replay-fixture.sample.json");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FunctionalProviderSmokeRecordReplayFixtureVerifier verifier =
            new FunctionalProviderSmokeRecordReplayFixtureVerifier();

    @Test
    void shouldAcceptRepositorySampleFixture() throws Exception {
        var result = verifier.validate(objectMapper.readTree(Files.readString(SAMPLE)));

        assertTrue(result.valid(), () -> String.join("\n", result.errors()));
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
                .replace("api-key\": \"***\"", "api-key\": \"mimo-live-secret\"")
                .replace("blocked api-key=***", "blocked api-key=mimo-live-secret")
                .replace("req_mimo_chat", "Bearer sk-live-secret")
                .replace("mimo-v2-pro", "AIzaSyBPM5panpM3zawPoYSUZ1JGTmzCPzr-R-s");

        var result = verifier.validate(objectMapper.readTree(unsafe));

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("未脱敏敏感字段值")));
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("api-key-assignment")));
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("bearer-token")));
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("google-ai-studio-key")));
    }

    @Test
    void shouldRejectMissingProviderProtocolAndModelFields() throws Exception {
        String missing = Files.readString(SAMPLE)
                .replaceFirst("\\s*\"protocol\"\\s*:\\s*\"OPENAI_COMPATIBLE\"\\s*,", "")
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
    void shouldRejectSummaryThatDoesNotMatchFixtureCounts() throws Exception {
        String mismatch = Files.readString(SAMPLE)
                .replace("\"PASS\": 1", "\"PASS\": 2");

        var result = verifier.validate(objectMapper.readTree(mismatch));

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("$.summary.PASS 与 fixture 计数不一致")));
    }
}
