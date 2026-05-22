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



    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("content-type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
