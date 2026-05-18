package com.prodigalgal.xaigateway.admin.application;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiDirectSmokeRecordReplayFixtureVerifierTests {

    private static final Path SAMPLE = Path.of(
            "src/test/resources/conformance/openai-direct-smoke-record-replay-fixture.sample.json");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAiDirectSmokeRecordReplayFixtureVerifier verifier =
            new OpenAiDirectSmokeRecordReplayFixtureVerifier();

    @Test
    void shouldAcceptRepositorySampleFixture() throws Exception {
        var result = verifier.validate(objectMapper.readTree(Files.readString(SAMPLE)));

        assertTrue(result.valid(), () -> String.join("\n", result.errors()));
    }

    @Test
    void shouldRejectUnsafeReplayPolicy() throws Exception {
        String unsafe = Files.readString(SAMPLE)
                .replace("\"network\": \"disabled_by_default\"", "\"network\": \"live_allowed\"");

        var result = verifier.validate(objectMapper.readTree(unsafe));

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("$.replayPolicy.network")));
    }

    @Test
    void shouldRejectUnredactedSecretMaterial() throws Exception {
        String unsafe = Files.readString(SAMPLE)
                .replace("Bearer ***", "Bearer sk-live-secret")
                .replace("file_1", "AIzaSyBPM5panpM3zawPoYSUZ1JGTmzCPzr-R-s")
                .replace("org-***", "org-real");

        var result = verifier.validate(objectMapper.readTree(unsafe));

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("bearer-token")));
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("google-ai-studio-key")));
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("openai-org")));
    }

    @Test
    void shouldRejectMissingFixtureRequiredFields() throws Exception {
        String missing = Files.readString(SAMPLE)
                .replaceFirst("\\s*\"resourceFamily\"\\s*:\\s*\"FILES\"\\s*,", "");

        var result = verifier.validate(objectMapper.readTree(missing));

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("$.fixtures[0].resourceFamily")));
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
