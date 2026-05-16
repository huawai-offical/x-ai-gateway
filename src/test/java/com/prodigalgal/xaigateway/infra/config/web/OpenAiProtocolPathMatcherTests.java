package com.prodigalgal.xaigateway.infra.config.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiProtocolPathMatcherTests {

    @Test
    void shouldMatchOpenAiProtocolPathFamilies() {
        List.of(
                "/v1/chat/completions",
                "/v1/chat/completions/chatcmpl_1/messages",
                "/v1/responses",
                "/v1/responses/resp_1",
                "/v1/conversations",
                "/v1/webhooks",
                "/v1/completions",
                "/v1/embeddings",
                "/v1/audio/transcriptions",
                "/v1/audio/speech",
                "/v1/images/generations",
                "/v1/videos",
                "/v1/moderations",
                "/v1/files",
                "/v1/files/file_1",
                "/v1/uploads",
                "/v1/batches",
                "/v1/models",
                "/v1/fine_tuning/jobs",
                "/v1/vector_stores",
                "/v1/containers",
                "/v1/evals",
                "/v1/skills",
                "/v1/realtime/client_secrets",
                "/v1/assistants",
                "/v1/threads/thread_1/runs"
        ).forEach(path -> assertTrue(OpenAiProtocolPathMatcher.isOpenAiProtocolPath(path), path));
    }

    @Test
    void shouldRejectNonOpenAiProtocolPaths() {
        List.of(
                "",
                "/v1",
                "/v1/messages",
                "/anthropic/v1/messages",
                "/google/v1beta/models/gemini:generateContent",
                "/public/docs/openapi.json",
                "/api/v1/media/provider-matrix",
                "/v1/web_search"
        ).forEach(path -> assertFalse(OpenAiProtocolPathMatcher.isOpenAiProtocolPath(path), path));
    }
}
