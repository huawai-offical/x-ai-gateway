package com.prodigalgal.xaigateway.admin.application;

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

class OpenAiDirectResourceSmokeHttpClientTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldBuildDefaultDryRunItemsWithoutSecretLeak() {
        OpenAiDirectResourceSmokeHttpClient client = new OpenAiDirectResourceSmokeHttpClient(objectMapper);

        List<String> families = client.normalizeFamilies(null);
        var item = client.dryRunItem(families.getFirst(), "https://api.openai.com/v1", "org-real", "proj-real");

        assertEquals(5, families.size());
        assertEquals("CHAT_COMPLETIONS", item.resourceFamily());
        assertEquals("SKIPPED", item.classification());
        assertEquals("DRY_RUN", item.skippedReason());
        assertEquals("POST", item.method());
        assertEquals("/v1/chat/completions", item.path());
        assertTrue(item.billable());
        assertFalse(item.requestPreview().toString().contains("org-real"));
        assertFalse(item.requestPreview().toString().contains("proj-real"));
        assertTrue(item.requestPreview().toString().contains("Bearer ***"));
    }

    @Test
    void shouldExecuteReadOnlyListProbeAndReturnEvidence() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/v1/files", exchange -> {
            requestPath.set(exchange.getRequestURI().toString());
            authorization.set(exchange.getRequestHeaders().getFirst("authorization"));
            exchange.getResponseHeaders().add("x-request-id", "req-files");
            sendJson(exchange, 200, """
                    {"object":"list","data":[{"id":"file_1","object":"file"}]}
                    """);
        });
        server.start();
        try {
            OpenAiDirectResourceSmokeHttpClient client = new OpenAiDirectResourceSmokeHttpClient(objectMapper);

            var item = client.executeReadOnlyProbe(
                    "FILES",
                    "sk-live-secret",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    3,
                    null,
                    null
            );

            assertEquals("PASS", item.classification());
            assertEquals("LIVE_SMOKE_OK", item.status());
            assertEquals(200, item.httpStatus());
            assertEquals("req-files", item.upstreamRequestId());
            assertEquals("/v1/files?limit=1", requestPath.get());
            assertEquals("Bearer sk-live-secret", authorization.get());
            assertEquals(1, item.evidence().get("itemsSeen"));
            assertEquals("file_1", item.evidence().get("firstId"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldClassifyNotFoundAsUnsupportedAndRedactSecret() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/vector_stores", exchange -> sendJson(exchange, 404, """
                {"error":{"type":"not_found_error","message":"missing Bearer sk-live-secret"}}
                """));
        server.start();
        try {
            OpenAiDirectResourceSmokeHttpClient client = new OpenAiDirectResourceSmokeHttpClient(objectMapper);

            var item = client.executeReadOnlyProbe(
                    "VECTOR_STORES",
                    "sk-live-secret",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    3,
                    null,
                    null
            );

            assertEquals("UNSUPPORTED", item.classification());
            assertEquals("not_found_error", item.skippedReason());
            assertEquals(404, item.httpStatus());
            assertFalse(item.failureMessage().contains("sk-live-secret"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldBlockBillableAndWriteFamiliesWithoutHttpCall() {
        OpenAiDirectResourceSmokeHttpClient client = new OpenAiDirectResourceSmokeHttpClient(objectMapper);

        var chat = client.executeReadOnlyProbe("CHAT_COMPLETIONS", "sk-secret", "https://api.openai.com", 3, null, null);
        var realtime = client.executeReadOnlyProbe("REALTIME_CLIENT_SECRET", "sk-secret", "https://api.openai.com", 3, null, null);

        assertEquals("BUDGET_BLOCKED", chat.classification());
        assertEquals("BILLABLE_PROBE_BLOCKED", chat.skippedReason());
        assertEquals("BUDGET_BLOCKED", realtime.classification());
        assertEquals("WRITE_PROBE_BLOCKED", realtime.skippedReason());
    }

    @Test
    void shouldExecuteExplicitBillableAndWriteProbesWithMinimalPayloads() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> chatBody = new AtomicReference<>();
        AtomicReference<String> responsesBody = new AtomicReference<>();
        AtomicReference<String> realtimeBody = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            chatBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("x-request-id", "req-chat");
            sendJson(exchange, 200, """
                    {"id":"chatcmpl_1","object":"chat.completion","model":"gpt-4o-mini","usage":{"completion_tokens":1}}
                    """);
        });
        server.createContext("/v1/responses", exchange -> {
            responsesBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("x-request-id", "req-resp");
            sendJson(exchange, 200, """
                    {"id":"resp_1","object":"response","model":"gpt-4o-mini","usage":{"output_tokens":1}}
                    """);
        });
        server.createContext("/v1/realtime/client_secrets", exchange -> {
            realtimeBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("x-request-id", "req-rt");
            sendJson(exchange, 200, """
                    {"client_secret":{"value":"ek_secret","expires_at":1893456000},"session":{"type":"realtime","model":"gpt-realtime-mini"}}
                    """);
        });
        server.start();
        try {
            OpenAiDirectResourceSmokeHttpClient client = new OpenAiDirectResourceSmokeHttpClient(objectMapper);
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

            var chat = client.executeProbe("CHAT_COMPLETIONS", "sk-live-secret", baseUrl, 3, "org-real", "proj-real", true, false);
            var responses = client.executeProbe("RESPONSES", "sk-live-secret", baseUrl, 3, null, null, true, false);
            var realtime = client.executeProbe("REALTIME_CLIENT_SECRET", "sk-live-secret", baseUrl, 3, null, null, false, true);

            assertEquals("PASS", chat.classification());
            assertEquals("req-chat", chat.upstreamRequestId());
            assertEquals(1, objectMapper.readTree(chatBody.get()).path("max_completion_tokens").asInt());
            assertEquals(false, objectMapper.readTree(chatBody.get()).path("store").asBoolean());
            assertFalse(chat.requestPreview().toString().contains("org-real"));
            assertFalse(chat.requestPreview().toString().contains("proj-real"));

            assertEquals("PASS", responses.classification());
            assertEquals(1, objectMapper.readTree(responsesBody.get()).path("max_output_tokens").asInt());
            assertEquals(false, objectMapper.readTree(responsesBody.get()).path("store").asBoolean());

            assertEquals("PASS", realtime.classification());
            assertEquals(true, realtime.evidence().get("clientSecretReturned"));
            assertEquals(60, objectMapper.readTree(realtimeBody.get()).path("expires_after").path("seconds").asInt());
            assertEquals("text", objectMapper.readTree(realtimeBody.get()).path("session").path("output_modalities").get(0).asText());
            assertEquals(1, objectMapper.readTree(realtimeBody.get()).path("session").path("max_output_tokens").asInt());
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
