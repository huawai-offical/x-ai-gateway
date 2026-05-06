package com.prodigalgal.xaigateway.gateway.core.execution;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalContentPart;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessageRole;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalReasoningConfig;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolDefinition;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResponse;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileContent;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileResponse;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaGatewayChatRuntimeTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExecuteToolReasoningAndGatewayImageForOllama() {
        GatewayFileService gatewayFileService = Mockito.mock(GatewayFileService.class);
        Mockito.when(gatewayFileService.getFileContent("file-1", 1L))
                .thenReturn(new GatewayFileContent(
                        GatewayFileResponse.from("file-1", "photo.png", null, 5, Instant.now(), "processed"),
                        "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        "image/png"
                ));

        ExchangeFunction exchangeFunction = request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("""
                        {
                          "message": {
                            "content": "done",
                            "thinking": "step-by-step",
                            "tool_calls": [
                              {
                                "id": "call_1",
                                "type": "function",
                                "function": {
                                  "name": "lookup_weather",
                                  "arguments": {"city":"Shanghai"}
                                }
                              }
                            ]
                          },
                          "prompt_eval_count": 3,
                          "eval_count": 2
                        }
                        """)
                .build());
        OllamaGatewayChatRuntime runtime = new OllamaGatewayChatRuntime(
                WebClient.builder().exchangeFunction(exchangeFunction),
                objectMapper,
                gatewayFileService
        );
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("reasoning_effort", "high");
        CanonicalRequest request = new CanonicalRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.OPENAI,
                "/v1/chat/completions",
                "llama3",
                List.of(new CanonicalMessage(
                        CanonicalMessageRole.USER,
                        List.of(
                                CanonicalContentPart.text("describe"),
                                CanonicalContentPart.image("image/png", "gateway://file-1", "photo.png")
                        )
                )),
                List.of(new CanonicalToolDefinition("lookup_weather", "Lookup weather", objectMapper.createObjectNode().put("type", "object"), null)),
                null,
                null,
                null,
                new CanonicalReasoningConfig(metadata, "high"),
                null
        );

        CanonicalResponse result = runtime.execute(context(request));

        assertEquals("done", result.outputText());
        assertEquals("step-by-step", result.reasoning());
        assertEquals(1, result.toolCalls().size());
        assertEquals("lookup_weather", result.toolCalls().get(0).name());
        assertEquals("{\"city\":\"Shanghai\"}", result.toolCalls().get(0).arguments());
        assertEquals(5, result.usage().totalTokens());
    }

    @Test
    void shouldRejectRemoteImageInputForOllama() {
        OllamaGatewayChatRuntime runtime = new OllamaGatewayChatRuntime(WebClient.builder(), objectMapper, Mockito.mock(GatewayFileService.class));
        CanonicalRequest request = new CanonicalRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.OPENAI,
                "/v1/chat/completions",
                "llama3",
                List.of(new CanonicalMessage(
                        CanonicalMessageRole.USER,
                        List.of(
                                CanonicalContentPart.text("hello"),
                                CanonicalContentPart.image("image/png", "https://example.com/demo.png", "demo.png")
                        )
                )),
                List.of(),
                null,
                null,
                null,
                null,
                null
        );

        assertThrows(IllegalArgumentException.class, () -> runtime.execute(context(request)));
    }

    @Test
    void shouldInjectTextDocumentInputForOllama() {
        GatewayFileService gatewayFileService = Mockito.mock(GatewayFileService.class);
        Mockito.when(gatewayFileService.getFileContent("doc-1", 1L))
                .thenReturn(new GatewayFileContent(
                        GatewayFileResponse.from("doc-1", "notes.md", null, 28, Instant.now(), "processed"),
                        "第一行文档\nsecond line".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        "text/markdown"
                ));
        AtomicReference<String> capturedBody = new AtomicReference<>();
        ExchangeFunction exchangeFunction = request -> {
            capturedBody.set(extractBody(request));
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("""
                            {
                              "message": {"content": "done"},
                              "prompt_eval_count": 4,
                              "eval_count": 1
                            }
                            """)
                    .build());
        };
        OllamaGatewayChatRuntime runtime = new OllamaGatewayChatRuntime(
                WebClient.builder().exchangeFunction(exchangeFunction),
                objectMapper,
                gatewayFileService
        );
        CanonicalRequest request = new CanonicalRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.RESPONSES,
                "/v1/responses",
                "llama3",
                List.of(new CanonicalMessage(
                        CanonicalMessageRole.USER,
                        List.of(
                                CanonicalContentPart.text("请总结文档"),
                                CanonicalContentPart.file("text/markdown", "gateway://doc-1", "notes.md")
                        )
                )),
                List.of(),
                null,
                null,
                null,
                null,
                null
        );

        CanonicalResponse result = runtime.execute(context(request));

        assertEquals("done", result.outputText());
        assertNotNull(capturedBody.get());
        assertTrue(capturedBody.get().contains("请总结文档"));
        assertTrue(capturedBody.get().contains("Ollama 文本文件: notes.md; mimeType=text/markdown"));
        assertTrue(capturedBody.get().contains("第一行文档"));
        Mockito.verify(gatewayFileService).getFileContent("doc-1", 1L);
    }

    @Test
    void shouldRejectBinaryDocumentInputForOllama() {
        GatewayFileService gatewayFileService = Mockito.mock(GatewayFileService.class);
        Mockito.when(gatewayFileService.getFileContent("file-1", 1L))
                .thenReturn(new GatewayFileContent(
                        GatewayFileResponse.from("file-1", "demo.pdf", null, 4, Instant.now(), "processed"),
                        "%PDF".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        "application/pdf"
                ));
        OllamaGatewayChatRuntime runtime = new OllamaGatewayChatRuntime(WebClient.builder(), objectMapper, gatewayFileService);
        CanonicalRequest request = new CanonicalRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.RESPONSES,
                "/v1/responses",
                "llama3",
                List.of(new CanonicalMessage(
                        CanonicalMessageRole.USER,
                        List.of(
                                CanonicalContentPart.text("hello"),
                                CanonicalContentPart.file("application/pdf", "gateway://file-1", "demo.pdf")
                        )
                )),
                List.of(),
                null,
                null,
                null,
                null,
                null
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> runtime.execute(context(request)));
        assertTrue(exception.getMessage().contains("OLLAMA_UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    void shouldEmitReasoningToolCallAndTerminalUsageChunks() {
        ExchangeFunction exchangeFunction = request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("""
                        {"message":{"thinking":"step 1"}}
                        {"message":{"tool_calls":[{"id":"call_1","type":"function","function":{"name":"lookup_weather","arguments":{"city":"Shanghai"}}}]}}
                        {"message":{"content":"hel"}}
                        {"message":{"content":"lo","thinking":"step 2"},"done":true,"done_reason":"stop","prompt_eval_count":3,"eval_count":2}
                        """)
                .build());
        OllamaGatewayChatRuntime runtime = new OllamaGatewayChatRuntime(
                WebClient.builder().exchangeFunction(exchangeFunction),
                objectMapper,
                Mockito.mock(GatewayFileService.class)
        );
        CanonicalRequest request = new CanonicalRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.OPENAI,
                "/v1/chat/completions",
                "llama3",
                List.of(new CanonicalMessage(CanonicalMessageRole.USER, List.of(CanonicalContentPart.text("hello")))),
                List.of(),
                null,
                null,
                null,
                null,
                null
        );

        List<CanonicalStreamEvent> chunks = runtime.executeStream(context(request)).collectList().block();

        assertNotNull(chunks);
        assertEquals(5, chunks.size());
        assertEquals("step 1", chunks.get(0).reasoningDelta());
        assertEquals("lookup_weather", chunks.get(1).toolCalls().get(0).name());
        assertEquals("hel", chunks.get(2).textDelta());
        assertEquals("lo", chunks.get(3).textDelta());
        assertEquals("step 2", chunks.get(3).reasoningDelta());
        assertEquals(true, chunks.get(4).terminal());
        assertEquals("STOP", chunks.get(4).finishReason().name());
        assertEquals(5, chunks.get(4).usage().totalTokens());
    }

    private GatewayChatRuntimeContext context(CanonicalRequest request) {
        CatalogCandidateView candidate = new CatalogCandidateView(
                101L,
                "ollama",
                ProviderType.OLLAMA_DIRECT,
                1L,
                ProviderFamily.OLLAMA,
                UpstreamSiteKind.OLLAMA_DIRECT,
                AuthStrategy.UNSUPPORTED,
                PathStrategy.OLLAMA_API_CHAT,
                ErrorSchemaStrategy.OLLAMA_ERROR,
                "http://localhost:11434",
                "llama3",
                "llama3",
                List.of("openai", "responses", "anthropic_native", "google_native"),
                true,
                true,
                false,
                true,
                true,
                false,
                ReasoningTransport.OLLAMA_THINKING,
                com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel.NATIVE
        );
        RouteCandidateView routeCandidateView = new RouteCandidateView(candidate, 11L, 10, 100);
        RouteSelectionResult selectionResult = new RouteSelectionResult(
                1L,
                "sk-gw-test",
                "llama3",
                "llama3",
                "llama3",
                "openai",
                "prefix",
                "fingerprint",
                "llama3",
                RouteSelectionSource.WEIGHTED_HASH,
                routeCandidateView,
                List.of(routeCandidateView)
        );
        UpstreamCredentialEntity credential = new UpstreamCredentialEntity();
        credential.setBaseUrl("http://localhost:11434");
        credential.setProviderType(ProviderType.OLLAMA_DIRECT);
        return new GatewayChatRuntimeContext(
                selectionResult,
                credential,
                new com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial(
                        null,
                        null,
                        com.prodigalgal.xaigateway.gateway.core.credential.CredentialAuthKind.API_KEY,
                        null,
                        null,
                        java.util.Map.of(),
                        null,
                        "test"
                ),
                request,
                null
        );
    }

    private String extractBody(ClientRequest request) {
        MockClientHttpRequest mockRequest = new MockClientHttpRequest(request.method(), URI.create("https://example.com"));
        request.headers().forEach((key, values) -> mockRequest.getHeaders().put(key, new ArrayList<>(values)));
        request.body().insert(mockRequest, new BodyInserter.Context() {
            @Override
            public List<HttpMessageWriter<?>> messageWriters() {
                return ExchangeStrategies.withDefaults().messageWriters();
            }

            @Override
            public Optional<ServerHttpRequest> serverRequest() {
                return Optional.empty();
            }

            @Override
            public java.util.Map<String, Object> hints() {
                return Collections.emptyMap();
            }
        }).block();
        return mockRequest.getBodyAsString().block();
    }
}
