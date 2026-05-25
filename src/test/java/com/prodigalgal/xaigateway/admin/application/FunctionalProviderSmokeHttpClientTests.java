package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
    void shouldBuildMimoOpenAiCompatibleDryRunWithContractBearerAuth() {
        FunctionalProviderSmokeHttpClient client = new FunctionalProviderSmokeHttpClient(objectMapper);
        String protocol = client.resolveProtocol(ProviderType.OPENAI_COMPATIBLE, null, null);
        List<String> families = client.normalizeFamilies(ProviderType.OPENAI_COMPATIBLE, protocol, null);

        var item = client.dryRunItem(
                ProviderType.OPENAI_COMPATIBLE,
                protocol,
                families.getFirst(),
                null,
                null
        );

        assertEquals(3, families.size());
        assertEquals("XIAOMI_MIMO_OPENAI_COMPATIBLE", protocol);
        assertEquals("CHAT_COMPLETIONS", item.resourceFamily());
        assertEquals("SKIPPED", item.classification());
        assertEquals("DRY_RUN", item.skippedReason());
        assertEquals("/v1/chat/completions", item.path());
        assertEquals("https://token-plan-sgp.xiaomimimo.com", item.requestPreview().get("baseUrl"));
        assertEquals("mimo-v2-pro", item.model());
        assertTrue(item.requestPreview().toString().contains("Bearer ***"));
        assertFalse(item.requestPreview().toString().contains("api-key=***"));
    }

    @Test
    void shouldBuildMimoAnthropicCompatibleDryRunFromCurrentDefaultEndpoint() {
        FunctionalProviderSmokeHttpClient client = new FunctionalProviderSmokeHttpClient(objectMapper);
        String protocol = client.resolveProtocol(ProviderType.ANTHROPIC_DIRECT, "mimo_anthropic", null);
        List<String> families = client.normalizeFamilies(ProviderType.ANTHROPIC_DIRECT, protocol, null);

        var item = client.dryRunItem(
                ProviderType.ANTHROPIC_DIRECT,
                protocol,
                families.getFirst(),
                null,
                null
        );

        assertEquals("XIAOMI_MIMO_ANTHROPIC_COMPATIBLE", protocol);
        assertEquals("MESSAGES", item.resourceFamily());
        assertEquals("/v1/messages", item.path());
        assertEquals("https://token-plan-sgp.xiaomimimo.com/anthropic", item.requestPreview().get("baseUrl"));
        assertTrue(item.requestPreview().toString().contains("anthropic-version=2023-06-01"));
        assertTrue(item.requestPreview().toString().contains("api-key=***"));
    }

    @Test
    void shouldKeepNonMimoAnthropicAliasGenericInsteadOfMimoSpecific() {
        FunctionalProviderSmokeHttpClient client = new FunctionalProviderSmokeHttpClient(objectMapper);
        String protocol = client.resolveProtocol(
                ProviderType.ANTHROPIC_DIRECT,
                "anthropic_compatible",
                "https://api.deepseek.com/anthropic"
        );

        var item = client.dryRunItem(
                ProviderType.ANTHROPIC_DIRECT,
                protocol,
                "MESSAGES",
                "https://api.deepseek.com/anthropic",
                null
        );

        assertEquals("ANTHROPIC_COMPATIBLE", protocol);
        assertEquals("ANTHROPIC_COMPATIBLE", item.requestPreview().get("protocol"));
        assertEquals("https://api.deepseek.com/anthropic", item.requestPreview().get("baseUrl"));
        assertTrue(item.requestPreview().toString().contains("api-key=***"));
        assertFalse(item.requestPreview().toString().contains("XIAOMI_MIMO_ANTHROPIC_COMPATIBLE"));
    }

    @Test
    void shouldBuildDeepSeekProviderSpecificOpenAiSmokeWithBearerAuth() {
        FunctionalProviderSmokeHttpClient client = new FunctionalProviderSmokeHttpClient(objectMapper);
        String protocol = client.resolveProtocol(
                ProviderType.OPENAI_COMPATIBLE,
                "openai_compatible",
                "https://api.deepseek.com"
        );
        List<String> families = client.normalizeFamilies(ProviderType.OPENAI_COMPATIBLE, protocol, null);

        var item = client.dryRunItem(
                ProviderType.OPENAI_COMPATIBLE,
                protocol,
                families.getFirst(),
                "https://api.deepseek.com",
                null
        );

        assertEquals("DEEPSEEK_OPENAI_COMPATIBLE", protocol);
        assertEquals("CHAT_COMPLETIONS", item.resourceFamily());
        assertEquals("/v1/chat/completions", item.path());
        assertEquals("deepseek-chat", item.model());
        assertEquals("https://api.deepseek.com", item.requestPreview().get("baseUrl"));
        assertTrue(item.requestPreview().toString().contains("authorization=Bearer ***"));
        assertFalse(item.requestPreview().toString().contains("api-key=***"));
    }

    @Test
    void shouldBuildXaiProviderSpecificOpenAiSmokeWithBearerAuth() {
        FunctionalProviderSmokeHttpClient client = new FunctionalProviderSmokeHttpClient(objectMapper);
        String protocol = client.resolveProtocol(
                ProviderType.OPENAI_COMPATIBLE,
                "grok_openai",
                "https://api.x.ai/v1"
        );

        var item = client.dryRunItem(
                ProviderType.OPENAI_COMPATIBLE,
                protocol,
                "CHAT_COMPLETIONS",
                "https://api.x.ai/v1",
                null
        );

        assertEquals("XAI_OPENAI_COMPATIBLE", protocol);
        assertEquals("/v1/chat/completions", item.path());
        assertEquals("grok-4.3", item.model());
        assertEquals("https://api.x.ai", item.requestPreview().get("baseUrl"));
        assertTrue(item.requestPreview().toString().contains("authorization=Bearer ***"));
        assertFalse(item.requestPreview().toString().contains("api-key=***"));
    }

    @Test
    void shouldKeepProviderSpecificOpenAiSmokePathsAlignedWithCatalogContracts() {
        FunctionalProviderSmokeHttpClient client = new FunctionalProviderSmokeHttpClient(objectMapper);
        ProviderCatalogSnapshot snapshot = new ProviderCatalogLoader(objectMapper).load();

        for (ProviderPresetDefinition preset : snapshot.presets()) {
            Map<String, Object> contract = preset.nativeAdapterContract();
            if (!"provider_specific_openai_compatible".equals(contract.get("adapterKind"))) {
                continue;
            }
            if (!List.of("chat_completions", "chat_completions_path_adapter", "web_grounded_chat_completions")
                    .contains(contract.get("nativeSurface"))) {
                continue;
            }
            String protocol = client.resolveProtocol(
                    ProviderType.OPENAI_COMPATIBLE,
                    preset.code() + "_openai_compatible",
                    preset.defaultBaseUrl()
            );
            List<String> families = client.normalizeFamilies(ProviderType.OPENAI_COMPATIBLE, protocol, null);
            var item = client.dryRunItem(
                    ProviderType.OPENAI_COMPATIBLE,
                    protocol,
                    families.getFirst(),
                    preset.defaultBaseUrl(),
                    null
            );

            String expectedEndpoint = String.valueOf(((List<?>) contract.get("requiredEndpoints")).getFirst());
            assertEquals(expectedEndpoint, item.path(), preset.code() + " smoke path");
            assertEquals("SKIPPED", item.classification(), preset.code() + " dry-run classification");
            assertTrue(item.requestPreview().toString().contains("authorization=Bearer ***"), preset.code() + " bearer auth");
            assertFalse(item.requestPreview().toString().contains("api-key=***"), preset.code() + " must not use legacy api-key auth");
        }
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
    void shouldBuildCohereNativeDryRunForEmbedAndRejectChatFamily() {
        FunctionalProviderSmokeHttpClient client = new FunctionalProviderSmokeHttpClient(objectMapper);
        String protocol = client.resolveProtocol(ProviderType.OPENAI_COMPATIBLE, null, "https://api.cohere.ai");
        List<String> families = client.normalizeFamilies(ProviderType.OPENAI_COMPATIBLE, protocol, null);

        var embed = client.dryRunItem(
                ProviderType.OPENAI_COMPATIBLE,
                protocol,
                families.getFirst(),
                "https://api.cohere.ai",
                null
        );
        var chat = client.dryRunItem(
                ProviderType.OPENAI_COMPATIBLE,
                protocol,
                "CHAT_COMPLETIONS",
                "https://api.cohere.ai",
                null
        );

        assertEquals("COHERE_NATIVE", protocol);
        assertEquals(List.of("EMBEDDINGS", "RERANK"), families);
        assertEquals("/v2/embed", embed.path());
        assertEquals("SKIPPED", embed.classification());
        assertTrue(embed.requestPreview().toString().contains("Bearer ***"));
        assertFalse(embed.requestPreview().toString().contains("/v1/chat/completions"));
        assertEquals("UNSUPPORTED", chat.classification());
        assertEquals("OUT_OF_FUNCTIONAL_API_SCOPE", chat.skippedReason());
    }

    @Test
    void shouldBuildJinaNativeRerankDryRunWithoutGenericChatPath() {
        FunctionalProviderSmokeHttpClient client = new FunctionalProviderSmokeHttpClient(objectMapper);
        String protocol = client.resolveProtocol(ProviderType.OPENAI_COMPATIBLE, "jina_native", null);

        var item = client.dryRunItem(
                ProviderType.OPENAI_COMPATIBLE,
                protocol,
                "RERANK",
                null,
                null
        );

        assertEquals("JINA_NATIVE", protocol);
        assertEquals("/v1/rerank", item.path());
        assertEquals("jina-reranker-v2-base-multilingual", item.model());
        assertEquals("SKIPPED", item.classification());
        assertTrue(item.requestPreview().toString().contains("Bearer ***"));
        assertFalse(item.requestPreview().toString().contains("/v1/chat/completions"));
    }

    @Test
    void shouldExecuteJinaNativeEmbeddingsWithBearerAuth() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/v1/embeddings", exchange -> {
            requestPath.set(exchange.getRequestURI().toString());
            authorization.set(exchange.getRequestHeaders().getFirst("authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("x-request-id", "req-jina-embed");
            sendJson(exchange, 200, """
                    {"object":"list","model":"jina-embeddings-v3","data":[{"object":"embedding","index":0,"embedding":[0.1]}]}
                    """);
        });
        server.start();
        try {
            FunctionalProviderSmokeHttpClient client = new FunctionalProviderSmokeHttpClient(objectMapper);
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";

            var item = client.executeProbe(
                    ProviderType.OPENAI_COMPATIBLE,
                    "JINA_NATIVE",
                    "EMBEDDINGS",
                    "jina-secret",
                    baseUrl,
                    "jina-embeddings-v3",
                    3,
                    true
            );

            assertEquals("PASS", item.classification());
            assertEquals("/v1/embeddings", requestPath.get());
            assertEquals("Bearer jina-secret", authorization.get());
            assertEquals("jina-embeddings-v3", objectMapper.readTree(requestBody.get()).path("model").asText());
            assertEquals(1, item.evidence().get("dataSeen"));
            assertFalse(item.toString().contains("jina-secret"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldExtractCohereNativeEmbedAndRerankEvidenceWithoutOpenAiShapeAssumption() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> embedBody = new AtomicReference<>();
        AtomicReference<String> rerankBody = new AtomicReference<>();
        server.createContext("/v2/embed", exchange -> {
            embedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("x-request-id", "req-cohere-embed");
            sendJson(exchange, 200, """
                    {"id":"embd_1","embeddings":{"float":[[0.1,0.2]]},"meta":{"billed_units":{"input_tokens":1}}}
                    """);
        });
        server.createContext("/v2/rerank", exchange -> {
            rerankBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("x-request-id", "req-cohere-rerank");
            sendJson(exchange, 200, """
                    {"id":"rank_1","results":[{"index":0,"relevance_score":0.98}],"meta":{"billed_units":{"search_units":1}}}
                    """);
        });
        server.start();
        try {
            FunctionalProviderSmokeHttpClient client = new FunctionalProviderSmokeHttpClient(objectMapper);
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

            var embed = client.executeProbe(
                    ProviderType.OPENAI_COMPATIBLE,
                    "COHERE_NATIVE",
                    "EMBEDDINGS",
                    "cohere-secret",
                    baseUrl,
                    "embed-v4.0",
                    3,
                    true
            );
            var rerank = client.executeProbe(
                    ProviderType.OPENAI_COMPATIBLE,
                    "COHERE_NATIVE",
                    "RERANK",
                    "cohere-secret",
                    baseUrl,
                    "rerank-v3.5",
                    3,
                    true
            );

            assertEquals("PASS", embed.classification());
            assertEquals("PASS", rerank.classification());
            assertEquals("req-cohere-embed", embed.upstreamRequestId());
            assertEquals("req-cohere-rerank", rerank.upstreamRequestId());
            assertEquals(1, embed.evidence().get("embeddingFloatVectorsSeen"));
            assertTrue(embed.evidence().get("embeddingFields").toString().contains("float"));
            assertTrue(embed.evidence().get("billedUnitFields").toString().contains("input_tokens"));
            assertEquals(1, rerank.evidence().get("resultsSeen"));
            assertTrue(rerank.evidence().get("firstResultFields").toString().contains("relevance_score"));
            assertTrue(rerank.evidence().get("billedUnitFields").toString().contains("search_units"));
            assertEquals("embed-v4.0", objectMapper.readTree(embedBody.get()).path("model").asText());
            assertEquals("rerank-v3.5", objectMapper.readTree(rerankBody.get()).path("model").asText());
            assertFalse(embed.toString().contains("cohere-secret"));
            assertFalse(rerank.toString().contains("cohere-secret"));
        } finally {
            server.stop(0);
        }
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
        assertTrue(item.requestPreview().toString().contains("Bearer ***"));
        assertFalse(item.toString().contains("mimo-secret"));
    }

    @Test
    void shouldExecuteMimoOpenAiCompatibleChatWithBearerHeader() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            requestPath.set(exchange.getRequestURI().toString());
            apiKey.set(exchange.getRequestHeaders().getFirst("api-key"));
            authorization.set(exchange.getRequestHeaders().getFirst("authorization"));
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
            assertEquals(null, apiKey.get());
            assertEquals("Bearer mimo-secret", authorization.get());
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
