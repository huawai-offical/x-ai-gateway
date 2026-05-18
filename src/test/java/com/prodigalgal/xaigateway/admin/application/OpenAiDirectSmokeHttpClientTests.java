package com.prodigalgal.xaigateway.admin.application;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiDirectSmokeHttpClientTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void shouldBuildRedactedModelsPreview() {
        OpenAiDirectSmokeHttpClient client = new OpenAiDirectSmokeHttpClient(objectMapper);

        Map<String, Object> preview = client.requestPreview("https://api.openai.com/v1", "org-real", "proj-real");

        assertEquals("GET", preview.get("method"));
        assertEquals("https://api.openai.com", preview.get("baseUrl"));
        assertEquals("/v1/models", preview.get("path"));
        Map<String, Object> headers = (Map<String, Object>) preview.get("headers");
        assertEquals("Bearer ***", headers.get("authorization"));
        assertEquals("***", headers.get("OpenAI-Organization"));
        assertEquals("***", headers.get("OpenAI-Project"));
        assertFalse(preview.toString().contains("org-real"));
        assertFalse(preview.toString().contains("proj-real"));
    }

    @Test
    void shouldExecuteModelsPermissionProbeWithOptionalHeaders() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> organization = new AtomicReference<>();
        AtomicReference<String> project = new AtomicReference<>();
        server.createContext("/v1/models", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("authorization"));
            organization.set(exchange.getRequestHeaders().getFirst("OpenAI-Organization"));
            project.set(exchange.getRequestHeaders().getFirst("OpenAI-Project"));
            exchange.getResponseHeaders().add("x-request-id", "req-models");
            sendJson(exchange, 200, """
                    {"object":"list","data":[{"id":"gpt-5.4","object":"model"},{"id":"gpt-4.1","object":"model"}]}
                    """);
        });
        server.start();
        try {
            OpenAiDirectSmokeHttpClient client = new OpenAiDirectSmokeHttpClient(objectMapper);

            OpenAiDirectSmokeHttpClient.OpenAiDirectSmokeResult result = client.execute(
                    "sk-test-secret",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    3,
                    "org-test",
                    "proj-test"
            );

            assertEquals(true, result.success());
            assertEquals(200, result.httpStatus());
            assertEquals("req-models", result.upstreamRequestId());
            assertEquals(2, result.modelsCount());
            assertEquals("gpt-5.4", result.sampleModels().getFirst());
            assertEquals("Bearer sk-test-secret", authorization.get());
            assertEquals("org-test", organization.get());
            assertEquals("proj-test", project.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldClassifyPermissionFailureAndRedactSecretFromMessage() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/models", exchange -> sendJson(exchange, 401, """
                {"error":{"type":"invalid_api_key","message":"bad key sk-test-secret via Bearer sk-test-secret"}}
                """));
        server.start();
        try {
            OpenAiDirectSmokeHttpClient client = new OpenAiDirectSmokeHttpClient(objectMapper);

            OpenAiDirectSmokeHttpClient.OpenAiDirectSmokeResult result = client.execute(
                    "sk-test-secret",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/models",
                    3,
                    null,
                    null
            );

            assertEquals(false, result.success());
            assertEquals(401, result.httpStatus());
            assertEquals("invalid_api_key", result.failureType());
            assertTrue(result.failureMessage().contains("***"));
            assertFalse(result.failureMessage().contains("sk-test-secret"));
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
