package com.prodigalgal.xaigateway.admin.application;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexResponsesSmokeHttpClientTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void shouldBuildCodexAppPreviewWithGpt54LowReasoningByDefault() {
        CodexResponsesSmokeHttpClient client = new CodexResponsesSmokeHttpClient(objectMapper);

        Map<String, Object> preview = client.requestPreview(null, "hello", null, null);

        assertEquals("https://chatgpt.com/backend-api/codex", preview.get("baseUrl"));
        assertEquals("/backend-api/codex/responses", preview.get("path"));
        assertEquals(true, preview.get("codexAppApi"));
        Map<String, Object> body = (Map<String, Object>) preview.get("body");
        assertEquals("gpt-5.4", body.get("model"));
        assertEquals("low", ((Map<String, Object>) body.get("reasoning")).get("effort"));
        assertEquals(false, body.get("store"));
        assertEquals(true, body.get("stream"));
    }

    @Test
    void shouldBlockResponsesPostWhenUsageLimitIsReached() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicInteger responsesCalls = new AtomicInteger();
        server.createContext("/backend-api/wham/usage", exchange -> {
            exchange.getResponseHeaders().add("request-id", "req-usage-budget");
            sendJson(exchange, 200, """
                    {"plan_type":"plus","rate_limit":{"allowed":false,"limit_reached":true,"primary_window":{"used_percent":100,"limit_window_seconds":18000}}}
                    """);
        });
        server.createContext("/backend-api/codex/responses", exchange -> {
            responsesCalls.incrementAndGet();
            sendJson(exchange, 200, "{\"id\":\"resp_should_not_be_called\"}");
        });
        server.start();
        try {
            CodexResponsesSmokeHttpClient client = new CodexResponsesSmokeHttpClient(objectMapper);

            CodexResponsesSmokeHttpClient.CodexResponsesSmokeResult result = client.execute(
                    "access-token",
                    "gpt-5.4@low",
                    "hello",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/backend-api/codex",
                    3,
                    "acct_test"
            );

            assertEquals(false, result.success());
            assertEquals(200, result.httpStatus());
            assertEquals("BUDGET_BLOCKED", result.failureType());
            assertEquals("req-usage-budget", result.upstreamRequestId());
            assertNotNull(result.usageProbe());
            assertEquals(false, result.usageProbe().allowed());
            assertEquals(true, result.usageProbe().limitReached());
            assertEquals(0, responsesCalls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldBlockResponsesPostWhenUsageProbeHasNoPermission() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicInteger responsesCalls = new AtomicInteger();
        server.createContext("/backend-api/wham/usage", exchange -> {
            exchange.getResponseHeaders().add("request-id", "req-usage-auth");
            sendJson(exchange, 403, """
                    {"error":{"type":"permission_denied","message":"missing scope"}}
                    """);
        });
        server.createContext("/backend-api/codex/responses", exchange -> {
            responsesCalls.incrementAndGet();
            sendJson(exchange, 200, "{\"id\":\"resp_should_not_be_called\"}");
        });
        server.start();
        try {
            CodexResponsesSmokeHttpClient client = new CodexResponsesSmokeHttpClient(objectMapper);

            CodexResponsesSmokeHttpClient.CodexResponsesSmokeResult result = client.execute(
                    "access-token",
                    "gpt-5.4@low",
                    "hello",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/backend-api/codex",
                    3,
                    "acct_test"
            );

            assertEquals(false, result.success());
            assertEquals(403, result.httpStatus());
            assertEquals("NO_PERMISSION", result.failureType());
            assertEquals("req-usage-auth", result.upstreamRequestId());
            assertNotNull(result.usageProbe());
            assertEquals("permission_denied", result.usageProbe().failureType());
            assertEquals(0, responsesCalls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldClassifyCodexModelUnsupportedWithoutMarkingKeepaliveFailure() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> responsesBody = new AtomicReference<>();
        server.createContext("/backend-api/wham/usage", exchange -> sendJson(exchange, 200, """
                {"plan_type":"plus","rate_limit":{"allowed":true,"limit_reached":false,"primary_window":{"used_percent":1,"limit_window_seconds":18000},"secondary_window":{"used_percent":2,"limit_window_seconds":604800}}}
                """));
        server.createContext("/backend-api/codex/responses", exchange -> {
            responsesBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("request-id", "req-model");
            sendJson(exchange, 400, """
                    {"detail":"The 'gpt-5-codex' model is not supported when using Codex with a ChatGPT account."}
                    """);
        });
        server.start();
        try {
            CodexResponsesSmokeHttpClient client = new CodexResponsesSmokeHttpClient(objectMapper);

            CodexResponsesSmokeHttpClient.CodexResponsesSmokeResult result = client.execute(
                    "access-token",
                    "gpt-5-codex@low",
                    "hello",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/backend-api/codex",
                    3,
                    "acct_test"
            );

            assertEquals(false, result.success());
            assertEquals(400, result.httpStatus());
            assertEquals("MODEL_NOT_SUPPORTED", result.failureType());
            assertEquals("req-model", result.upstreamRequestId());
            assertNotNull(result.usageProbe());
            assertEquals(true, result.usageProbe().success());
            assertTrue(responsesBody.get().contains("\"model\":\"gpt-5-codex\""));
            assertTrue(responsesBody.get().contains("\"effort\":\"low\""));
            assertTrue(responsesBody.get().contains("\"store\":false"));
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
