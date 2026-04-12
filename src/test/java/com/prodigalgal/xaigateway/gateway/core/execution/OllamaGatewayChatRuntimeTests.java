package com.prodigalgal.xaigateway.gateway.core.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OllamaGatewayChatRuntimeTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRejectToolCallsForOllama() {
        OllamaGatewayChatRuntime runtime = new OllamaGatewayChatRuntime(WebClient.builder(), objectMapper);
        ChatExecutionRequest request = new ChatExecutionRequest(
                "sk-gw-test",
                "openai",
                "/v1/chat/completions",
                "llama3",
                List.of(new ChatExecutionRequest.MessageInput("user", "hello", null, null, List.of())),
                List.of(new GatewayToolDefinition("sum", null, null, null)),
                null,
                null,
                null
        );

        assertThrows(IllegalArgumentException.class, () -> runtime.execute(context(request)));
    }

    @Test
    void shouldRejectReasoningForOllama() {
        OllamaGatewayChatRuntime runtime = new OllamaGatewayChatRuntime(WebClient.builder(), objectMapper);
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("reasoning_effort", "high");
        ChatExecutionRequest request = new ChatExecutionRequest(
                "sk-gw-test",
                "responses",
                "/v1/responses",
                "llama3",
                List.of(new ChatExecutionRequest.MessageInput("user", "hello", null, null, List.of())),
                List.of(),
                null,
                null,
                null,
                metadata
        );

        assertThrows(IllegalArgumentException.class, () -> runtime.execute(context(request)));
    }

    @Test
    void shouldEmitSingleTerminalChunkWithUsage() {
        ExchangeFunction exchangeFunction = request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("""
                        {"message":{"content":"hel"}}
                        {"message":{"content":"lo"},"done":true,"done_reason":"stop","prompt_eval_count":3,"eval_count":2}
                        """)
                .build());
        OllamaGatewayChatRuntime runtime = new OllamaGatewayChatRuntime(WebClient.builder().exchangeFunction(exchangeFunction), objectMapper);
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

        assertEquals(2, chunks.size());
        assertEquals("hel", chunks.get(0).textDelta());
        assertEquals("lo", chunks.get(1).textDelta());
        assertEquals(true, chunks.get(1).terminal());
        assertEquals("stop", chunks.get(1).finishReason());
        assertEquals(5, chunks.get(1).usage().totalTokens());
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
                List.of("openai"),
                true,
                false,
                false,
                false,
                false,
                false,
                ReasoningTransport.NONE,
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
