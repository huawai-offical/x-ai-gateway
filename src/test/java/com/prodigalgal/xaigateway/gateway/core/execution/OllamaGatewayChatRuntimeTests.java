package com.prodigalgal.xaigateway.gateway.core.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
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
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        ChatExecutionRequest request = new ChatExecutionRequest(
                "sk-gw-test",
                "openai",
                "/v1/chat/completions",
                "llama3",
                List.of(new ChatExecutionRequest.MessageInput(
                        "user",
                        "describe",
                        null,
                        null,
                        List.of(new ChatExecutionRequest.MediaInput("image", "image/png", "gateway://file-1", "photo.png"))
                )),
                List.of(new GatewayToolDefinition("lookup_weather", "Lookup weather", objectMapper.createObjectNode().put("type", "object"), null)),
                null,
                null,
                null,
                metadata
        );

        GatewayChatRuntimeResult result = runtime.execute(context(request));

        assertEquals("done", result.text());
        assertEquals("step-by-step", result.reasoning());
        assertEquals(1, result.toolCalls().size());
        assertEquals("lookup_weather", result.toolCalls().get(0).name());
        assertEquals("{\"city\":\"Shanghai\"}", result.toolCalls().get(0).arguments());
        assertEquals(5, result.usage().totalTokens());
    }

    @Test
    void shouldRejectRemoteImageInputForOllama() {
        OllamaGatewayChatRuntime runtime = new OllamaGatewayChatRuntime(WebClient.builder(), objectMapper, Mockito.mock(GatewayFileService.class));
        ChatExecutionRequest request = new ChatExecutionRequest(
                "sk-gw-test",
                "openai",
                "/v1/chat/completions",
                "llama3",
                List.of(new ChatExecutionRequest.MessageInput(
                        "user",
                        "hello",
                        null,
                        null,
                        List.of(new ChatExecutionRequest.MediaInput("image", "image/png", "https://example.com/demo.png", "demo.png"))
                )),
                List.of(),
                null,
                null,
                null
        );

        assertThrows(IllegalArgumentException.class, () -> runtime.execute(context(request)));
    }

    @Test
    void shouldRejectDocumentInputForOllama() {
        OllamaGatewayChatRuntime runtime = new OllamaGatewayChatRuntime(WebClient.builder(), objectMapper, Mockito.mock(GatewayFileService.class));
        ChatExecutionRequest request = new ChatExecutionRequest(
                "sk-gw-test",
                "responses",
                "/v1/responses",
                "llama3",
                List.of(new ChatExecutionRequest.MessageInput(
                        "user",
                        "hello",
                        null,
                        null,
                        List.of(new ChatExecutionRequest.MediaInput("file", "application/pdf", "gateway://file-1", "demo.pdf"))
                )),
                List.of(),
                null,
                null,
                null
        );

        assertThrows(IllegalArgumentException.class, () -> runtime.execute(context(request)));
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
        ChatExecutionRequest request = new ChatExecutionRequest(
                "sk-gw-test",
                "openai",
                "/v1/chat/completions",
                "llama3",
                List.of(new ChatExecutionRequest.MessageInput("user", "hello", null, null, List.of())),
                List.of(),
                null,
                null,
                null
        );

        List<ChatExecutionStreamChunk> chunks = runtime.executeStream(context(request)).collectList().block();

        assertNotNull(chunks);
        assertEquals(5, chunks.size());
        assertEquals("step 1", chunks.get(0).reasoningDelta());
        assertEquals("lookup_weather", chunks.get(1).toolCalls().get(0).name());
        assertEquals("hel", chunks.get(2).textDelta());
        assertEquals("lo", chunks.get(3).textDelta());
        assertEquals("step 2", chunks.get(3).reasoningDelta());
        assertEquals(true, chunks.get(4).terminal());
        assertEquals("stop", chunks.get(4).finishReason());
        assertEquals(5, chunks.get(4).usage().totalTokens());
    }

    private GatewayChatRuntimeContext context(ChatExecutionRequest request) {
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
        return new GatewayChatRuntimeContext(selectionResult, credential, null, request);
    }
}
