package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionalProviderSmokeHttpClientTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldBuildMimoOpenAiCompatibleDryRunWithoutOpenAiDirectHeaders() {
        FunctionalProviderSmokeHttpClient client = new FunctionalProviderSmokeHttpClient(objectMapper);
        String protocol = client.resolveProtocol(ProviderType.OPENAI_COMPATIBLE, null, null);
        List<String> families = client.normalizeFamilies(ProviderType.OPENAI_COMPATIBLE, protocol, null);

        var item = client.dryRunItem(
                ProviderType.OPENAI_COMPATIBLE,
                protocol,
                families.getFirst(),
                "https://api.mimo-v2.com/v1",
                null
        );

        assertEquals(3, families.size());
        assertEquals("OPENAI_COMPATIBLE", protocol);
        assertEquals("CHAT_COMPLETIONS", item.resourceFamily());
        assertEquals("SKIPPED", item.classification());
        assertEquals("DRY_RUN", item.skippedReason());
        assertEquals("/v1/chat/completions", item.path());
        assertEquals("https://api.mimo-v2.com", item.requestPreview().get("baseUrl"));
        assertEquals("mimo-v2-pro", item.model());
        assertTrue(item.requestPreview().toString().contains("api-key=***"));
        assertFalse(item.requestPreview().toString().contains("Bearer ***"));
    }

    @Test
    void shouldMarkOutOfScopeFamiliesAsUnsupported() {
        FunctionalProviderSmokeHttpClient client = new FunctionalProviderSmokeHttpClient(objectMapper);
        String protocol = client.resolveProtocol(ProviderType.OPENAI_COMPATIBLE, null, null);
        List<String> families = client.normalizeFamilies(ProviderType.OPENAI_COMPATIBLE, protocol, List.of("responses"));

        var item = client.dryRunItem(
                ProviderType.OPENAI_COMPATIBLE,
                protocol,
                families.getFirst(),
                "https://api.mimo-v2.com/v1",
                null
        );

        assertEquals("RESPONSES", item.resourceFamily());
        assertEquals("UNSUPPORTED", item.classification());
        assertEquals("OUT_OF_FUNCTIONAL_API_SCOPE", item.skippedReason());
        assertTrue(item.evidence().toString().contains("CHAT_COMPLETIONS"));
    }

    @Test
    void shouldBlockBillableLiveProbeUnlessExplicitlyAllowed() {
        FunctionalProviderSmokeHttpClient client = new FunctionalProviderSmokeHttpClient(objectMapper);
        String protocol = client.resolveProtocol(ProviderType.OPENAI_COMPATIBLE, null, null);

        var item = client.executeProbe(
                ProviderType.OPENAI_COMPATIBLE,
                protocol,
                "CHAT_COMPLETIONS",
                "mimo-secret",
                "https://api.mimo-v2.com/v1",
                null,
                3,
                false
        );

        assertEquals("BUDGET_BLOCKED", item.classification());
        assertEquals("BILLABLE_PROBE_BLOCKED", item.skippedReason());
        assertTrue(item.requestPreview().toString().contains("api-key=***"));
        assertFalse(item.toString().contains("mimo-secret"));
    }

    @Test
    void shouldExecuteMimoOpenAiCompatibleChatWithApiKeyHeader() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            requestPath.set(exchange.getRequestURI().toString());
            apiKey.set(exchange.getRequestHeaders().getFirst("api-key"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("x-request-id", "req-mimo-openai");
            sendJson(exchange, 200, """
                    {"id":"chatcmpl_1","object":"chat.completion","model":"mimo-v2-pro","usage":{"completion_tokens":1},"choices":[{"index":0}]}
                    """);
        });
        server.start();
        try {
            FunctionalProviderSmokeHttpClient client = new FunctionalProviderSmokeHttpClient(objectMapper);
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            String protocol = client.resolveProtocol(ProviderType.OPENAI_COMPATIBLE, "mimo_openai", baseUrl);

            var item = client.executeProbe(
                    ProviderType.OPENAI_COMPATIBLE,
                    protocol,
                    "CHAT_COMPLETIONS",
                    "mimo-secret",
                    baseUrl,
                    null,
                    3,
                    true
            );

            assertEquals("PASS", item.classification());
            assertEquals("LIVE_SMOKE_OK", item.status());
            assertEquals("/v1/chat/completions", requestPath.get());
            assertEquals("mimo-secret", apiKey.get());
            assertEquals("mimo-v2-pro", objectMapper.readTree(requestBody.get()).path("model").asText());
            assertEquals(1, objectMapper.readTree(requestBody.get()).path("max_tokens").asInt());
            assertEquals("req-mimo-openai", item.upstreamRequestId());
            assertEquals(1, item.evidence().get("choicesSeen"));
            assertFalse(item.toString().contains("mimo-secret"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldExecuteMimoAnthropicCompatibleMessagesWithAnthropicHeaders() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> apiKey = new AtomicReference<>();
        AtomicReference<String> anthropicVersion = new AtomicReference<>();
        AtomicReference<String> requestPath = new AtomicReference<>();
        server.createContext("/anthropic/v1/messages", exchange -> {
            requestPath.set(exchange.getRequestURI().toString());
            apiKey.set(exchange.getRequestHeaders().getFirst("api-key"));
            anthropicVersion.set(exchange.getRequestHeaders().getFirst("anthropic-version"));
            exchange.getResponseHeaders().add("request-id", "req-mimo-anthropic");
            sendJson(exchange, 200, """
                    {"id":"msg_1","type":"message","model":"mimo-v2-pro","content":[{"type":"text","text":"pong"}],"usage":{"output_tokens":1}}
                    """);
        });
        server.start();
        try {
            FunctionalProviderSmokeHttpClient client = new FunctionalProviderSmokeHttpClient(objectMapper);
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/anthropic";
            String protocol = client.resolveProtocol(ProviderType.ANTHROPIC_DIRECT, "mimo_anthropic", baseUrl);

            var item = client.executeProbe(
                    ProviderType.ANTHROPIC_DIRECT,
                    protocol,
                    "MESSAGES",
                    "mimo-secret",
                    baseUrl,
                    null,
                    3,
                    true
            );

            assertEquals("PASS", item.classification());
            assertEquals("/anthropic/v1/messages", requestPath.get());
            assertEquals("mimo-secret", apiKey.get());
            assertEquals("2023-06-01", anthropicVersion.get());
            assertEquals("req-mimo-anthropic", item.upstreamRequestId());
            assertEquals(1, item.evidence().get("contentBlocksSeen"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldExecuteGeminiNativeGenerateContentWithGoogleApiKeyHeader() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> googleApiKey = new AtomicReference<>();
        AtomicReference<String> requestPath = new AtomicReference<>();
        server.createContext("/v1beta/models/gemini-2.5-flash:generateContent", exchange -> {
            requestPath.set(exchange.getRequestURI().toString());
            googleApiKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            exchange.getResponseHeaders().add("x-cloud-trace-context", "trace-gemini");
            sendJson(exchange, 200, """
                    {"candidates":[{"content":{"parts":[{"text":"pong"}]}}],"usageMetadata":{"totalTokenCount":1}}
                    """);
        });
        server.start();
        try {
            FunctionalProviderSmokeHttpClient client = new FunctionalProviderSmokeHttpClient(objectMapper);
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            String protocol = client.resolveProtocol(ProviderType.GEMINI_DIRECT, null, baseUrl);

            var item = client.executeProbe(
                    ProviderType.GEMINI_DIRECT,
                    protocol,
                    "GENERATE_CONTENT",
                    "gemini-secret",
                    baseUrl,
                    null,
                    3,
                    true
            );

            assertEquals("PASS", item.classification());
            assertEquals("/v1beta/models/gemini-2.5-flash:generateContent", requestPath.get());
            assertEquals("gemini-secret", googleApiKey.get());
            assertEquals("trace-gemini", item.upstreamRequestId());
            assertEquals(1, item.evidence().get("candidatesSeen"));
            assertFalse(item.toString().contains("gemini-secret"));
        } finally {
            server.stop(0);
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("content-type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
