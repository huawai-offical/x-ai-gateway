package com.prodigalgal.xaigateway.infra.config.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiProtocolPathMatcherTests {


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
                "/v1/videos",
                "/v1/containers",
                "/v1/evals",
                "/v1/skills",
                "/v1/assistants",
                "/v1/threads/thread_1/runs",
                "/v1/web_search"
        ).forEach(path -> assertFalse(OpenAiProtocolPathMatcher.isOpenAiProtocolPath(path), path));
    }
}
