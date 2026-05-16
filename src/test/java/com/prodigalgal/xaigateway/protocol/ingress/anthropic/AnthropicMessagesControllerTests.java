package com.prodigalgal.xaigateway.protocol.ingress.anthropic;

import com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionService;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamilyResolver;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionResult;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionStreamResult;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResponse;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEventType;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolCall;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalUsage;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

@WebFluxTest(controllers = AnthropicMessagesController.class)
@Import({PermitAllSecurityTestConfig.class, AnthropicMessagesRequestMapper.class, GatewayClientFamilyResolver.class})
class AnthropicMessagesControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private DistributedKeyAuthenticationService distributedKeyAuthenticationService;

    @MockitoBean
    private GatewayChatExecutionService gatewayChatExecutionService;

    @Test
    void shouldExecuteMinimalAnthropicMessage() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>any()))
                .thenReturn(gatewayResponse("req-anthropic-1", "anthropic back", new GatewayUsage(900, 900, 200, 0, 300, 120, 300, 120, null, 1520, null), List.of(), GatewayFinishReason.END_TURN));

        webTestClient.post()
                .uri("/v1/messages")
                .header("x-api-key", "sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "claude-sonnet-4",
                          "system": "you are helpful",
                          "messages": [
                            {"role":"user","content":"hello"}
                          ],
                          "max_tokens": 256
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.type").isEqualTo("message")
                .jsonPath("$.content[0].text").isEqualTo("anthropic back")
                .jsonPath("$.usage.cache_read_input_tokens").isEqualTo(300)
                .jsonPath("$.usage.cache_creation_input_tokens").isEqualTo(120);
    }

    @Test
    void shouldReturnToolUseBlocks() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>any()))
                .thenReturn(gatewayResponse(
                        "req-anthropic-tool-1",
                        "",
                        GatewayUsage.empty(),
                        List.of(new GatewayToolCall(
                                "toolu_1",
                                "tool_use",
                                "lookup_weather",
                                "{\"city\":\"Shanghai\"}"
                        )),
                        GatewayFinishReason.TOOL_CALLS
                ));

        webTestClient.post()
                .uri("/v1/messages")
                .header("x-api-key", "sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "claude-sonnet-4",
                          "messages": [
                            {"role":"user","content":"帮我查天气"}
                          ],
                          "tools": [
                            {
                              "name":"lookup_weather",
                              "description":"Lookup weather",
                              "input_schema":{"type":"object"}
                            }
                          ],
                          "max_tokens": 256
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.stop_reason").isEqualTo("tool_use")
                .jsonPath("$.content[0].type").isEqualTo("tool_use")
                .jsonPath("$.content[0].name").isEqualTo("lookup_weather")
                .jsonPath("$.content[0].input.city").isEqualTo("Shanghai");
    }

    @Test
    void shouldAcceptOfficialAnthropicSnakeCaseControls() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>argThat(request ->
                        request != null
                                && Integer.valueOf(512).equals(request.maxTokens())
                                && request.toolChoice() != null
                                && "tool".equals(request.toolChoice().path("type").asText())
                                && "lookup_weather".equals(request.toolChoice().path("name").asText())
                                && request.reasoning() != null
                                && "enabled".equals(request.reasoning().rawSettings().path("type").asText())
                                && request.reasoning().rawSettings().path("budget_tokens").asInt() == 1024
                                && request.tools().size() == 1
                                && "object".equals(request.tools().get(0).inputSchema().path("type").asText())
                )))
                .thenReturn(gatewayResponse("req-anthropic-official-fields-1", "official controls accepted", GatewayUsage.empty(), List.of(), GatewayFinishReason.END_TURN));

        webTestClient.post()
                .uri("/v1/messages")
                .header("x-api-key", "sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "claude-sonnet-4",
                          "messages": [
                            {"role":"user","content":"帮我查天气"}
                          ],
                          "tools": [
                            {
                              "name":"lookup_weather",
                              "description":"Lookup weather",
                              "input_schema":{"type":"object"}
                            }
                          ],
                          "tool_choice":{"type":"tool","name":"lookup_weather"},
                          "thinking":{"type":"enabled","budget_tokens":1024},
                          "service_tier":"auto",
                          "max_tokens": 512
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].text").isEqualTo("official controls accepted");
    }

    @Test
    void shouldAcceptManagedAnthropicExtensionFields() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>argThat(request -> {
            var extensions = request == null ? null : request.providerExtensions();
            return extensions != null
                    && "auto".equals(extensions.path("service_tier").asText())
                    && "session-1".equals(extensions.path("container").asText())
                    && "user-1".equals(extensions.path("metadata").path("user_id").asText())
                    && extensions.path("context_management").path("clear_function_results").asBoolean(false)
                    && "https://mcp.example.com/sse".equals(extensions.path("mcp_servers").get(0).path("url").asText())
                    && "context-2025-10-01".equals(extensions.path("x_ai_gateway_anthropic_beta").asText());
        })))
                .thenReturn(gatewayResponse("req-anthropic-managed-fields-1", "managed controls accepted", GatewayUsage.empty(), List.of(), GatewayFinishReason.END_TURN));

        webTestClient.post()
                .uri("/v1/messages")
                .header("x-api-key", "sk-gw-test.secret")
                .header("anthropic-beta", "context-2025-10-01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "claude-sonnet-4",
                          "messages": [
                            {"role":"user","content":"列出工具"}
                          ],
                          "service_tier":"auto",
                          "container":"session-1",
                          "metadata":{"user_id":"user-1"},
                          "context_management":{"clear_function_results":true},
                          "mcp_servers":[{"type":"url","url":"https://mcp.example.com/sse","name":"docs"}],
                          "x_ai_gateway_mcp_allowlist":["mcp.example.com"],
                          "max_tokens": 512
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].text").isEqualTo("managed controls accepted");
    }

    @Test
    void shouldRejectAnthropicMcpServersWithoutGovernance() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));

        webTestClient.post()
                .uri("/v1/messages")
                .header("x-api-key", "sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "claude-sonnet-4",
                          "messages": [
                            {"role":"user","content":"列出工具"}
                          ],
                          "mcp_servers":[{"type":"url","url":"https://mcp.example.com/sse","name":"docs"}],
                          "max_tokens": 512
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_ARGUMENT")
                .jsonPath("$.message").isEqualTo("mcp_servers 需要 x_ai_gateway_mcp_allowlist 或 x_ai_gateway_allow_mcp_servers=true。");
    }

    @Test
    void shouldAcceptAnthropicImageBlocks() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>any()))
                .thenReturn(gatewayResponse("req-anthropic-image-1", "image processed", GatewayUsage.empty(), List.of(), GatewayFinishReason.END_TURN));

        webTestClient.post()
                .uri("/v1/messages")
                .header("x-api-key", "sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "claude-sonnet-4",
                          "messages": [
                            {
                              "role":"user",
                              "content":[
                                {"type":"text","text":"描述图片"},
                                {"type":"image","source":{"type":"url","url":"https://example.com/cat.png","media_type":"image/png"}}
                              ]
                            }
                          ],
                          "max_tokens": 256
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].text").isEqualTo("image processed");
    }

    @Test
    void shouldAcceptAnthropicDocumentBlocks() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>any()))
                .thenReturn(gatewayResponse("req-anthropic-doc-1", "document processed", GatewayUsage.empty(), List.of(), GatewayFinishReason.END_TURN));

        webTestClient.post()
                .uri("/v1/messages")
                .header("x-api-key", "sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "claude-sonnet-4",
                          "messages": [
                            {
                              "role":"user",
                              "content":[
                                {"type":"text","text":"总结这个文档"},
                                {"type":"document","title":"doc","source":{"type":"url","url":"https://example.com/doc.pdf","media_type":"application/pdf"}}
                              ]
                            }
                          ],
                          "max_tokens": 256
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].text").isEqualTo("document processed");
    }

    @Test
    void shouldAcceptAnthropicGatewayFileIdDocumentBlocks() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>any()))
                .thenReturn(gatewayResponse("req-anthropic-fileid-1", "gateway document processed", GatewayUsage.empty(), List.of(), GatewayFinishReason.END_TURN));

        webTestClient.post()
                .uri("/v1/messages")
                .header("x-api-key", "sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "claude-sonnet-4",
                          "messages": [
                            {
                              "role":"user",
                              "content":[
                                {"type":"document","title":"doc","source":{"type":"file_id","file_id":"file-123","media_type":"application/pdf"}}
                              ]
                            }
                          ],
                          "max_tokens": 256
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].text").isEqualTo("gateway document processed");
    }

    @Test
    void shouldAcceptAnthropicDocumentOnlyMessage() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>any()))
                .thenReturn(gatewayResponse("req-anthropic-doc-only-1", "document only processed", GatewayUsage.empty(), List.of(), GatewayFinishReason.END_TURN));

        webTestClient.post()
                .uri("/v1/messages")
                .header("x-api-key", "sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "claude-sonnet-4",
                          "messages": [
                            {
                              "role":"user",
                              "content":[
                                {"type":"document","title":"doc","source":{"type":"url","url":"https://example.com/doc.pdf","media_type":"application/pdf"}}
                              ]
                            }
                          ],
                          "max_tokens": 256
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].text").isEqualTo("document only processed");
    }

    @Test
    void shouldExecuteMinimalAnthropicStream() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayStream(Mockito.<CanonicalRequest>any()))
                .thenReturn(new CanonicalExecutionStreamResult(
                        "req-anthropic-stream-1",
                        selectionResult(),
                        plan(),
                        Flux.just(
                                textEvent("hello"),
                                completedEvent(GatewayFinishReason.END_TURN)
                        )
                ));

        var result = webTestClient.post()
                .uri("/v1/messages")
                .header("x-api-key", "sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "claude-sonnet-4",
                          "messages": [
                            {"role":"user","content":"hello"}
                          ],
                          "max_tokens": 256,
                          "stream": true
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class);

        var body = result.getResponseBody().collectList().block();
        assert body != null;
        String joined = String.join("", body);
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("message_start"));
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("content_block_delta"));
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("message_stop"));
    }

    @Test
    void shouldRejectAnthropicMessageWithoutUserPayload() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));

        webTestClient.post()
                .uri("/v1/messages")
                .header("x-api-key", "sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "claude-sonnet-4",
                          "messages": [
                            {"role":"assistant","content":"hello"}
                          ],
                          "max_tokens": 256
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_ARGUMENT")
                .jsonPath("$.message").isEqualTo("至少需要一条 user 消息。");
    }

    private RouteSelectionResult selectionResult() {
        CatalogCandidateView candidate = new CatalogCandidateView(
                201L,
                "anthropic-primary",
                ProviderType.ANTHROPIC_DIRECT,
                "https://api.anthropic.com",
                "claude-sonnet-4",
                "claude-sonnet-4",
                List.of("anthropic_native"),
                true,
                false,
                true,
                true,
                true,
                true,
                ReasoningTransport.ANTHROPIC
        );
        RouteCandidateView routeCandidateView = new RouteCandidateView(candidate, 21L, 10, 100);
        return new RouteSelectionResult(
                1L,
                "sk-gw-test",
                "claude-sonnet-4",
                "claude-sonnet-4",
                "claude-sonnet-4",
                "anthropic_native",
                "prefix-hash",
                "fingerprint",
                "claude-sonnet-4",
                RouteSelectionSource.PREFIX_AFFINITY,
                routeCandidateView,
                List.of(routeCandidateView)
        );
    }

    private CanonicalExecutionResult gatewayResponse(
            String requestId,
            String text,
            GatewayUsage usage,
            List<GatewayToolCall> toolCalls,
            GatewayFinishReason finishReason) {
        return new CanonicalExecutionResult(
                requestId,
                selectionResult(),
                plan(),
                new CanonicalResponse(
                        requestId,
                        selectionResult().publicModel(),
                        text,
                        null,
                        toCanonicalToolCalls(toolCalls),
                        toCanonicalUsage(usage),
                        finishReason
                )
        );
    }

    private CanonicalStreamEvent textEvent(String delta) {
        return new CanonicalStreamEvent(
                CanonicalStreamEventType.TEXT_DELTA,
                delta,
                null,
                List.of(),
                CanonicalUsage.empty(),
                false,
                null,
                null,
                null
        );
    }

    private CanonicalStreamEvent completedEvent(GatewayFinishReason finishReason) {
        return new CanonicalStreamEvent(
                CanonicalStreamEventType.COMPLETED,
                null,
                null,
                List.of(),
                CanonicalUsage.empty(),
                true,
                finishReason,
                "hello",
                null
        );
    }

    private CanonicalExecutionPlan plan() {
        return new CanonicalExecutionPlan(
                true,
                CanonicalIngressProtocol.ANTHROPIC_NATIVE,
                "/v1/messages",
                "claude-sonnet-4",
                "claude-sonnet-4",
                "claude-sonnet-4",
                TranslationResourceType.CHAT,
                TranslationOperation.CHAT_COMPLETION,
                ExecutionKind.NATIVE,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                List.of(InteropFeature.CHAT_TEXT),
                java.util.Map.of("chat_text", InteropCapabilityLevel.NATIVE),
                List.of(),
                List.of()
        );
    }

    private List<CanonicalToolCall> toCanonicalToolCalls(List<GatewayToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }
        return toolCalls.stream()
                .map(toolCall -> new CanonicalToolCall(toolCall.id(), toolCall.type(), toolCall.name(), toolCall.arguments()))
                .toList();
    }

    private CanonicalUsage toCanonicalUsage(GatewayUsage usage) {
        if (usage == null || usage.isEmpty()) {
            return CanonicalUsage.empty();
        }
        return new CanonicalUsage(
                true,
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens(),
                usage.cacheHitTokens(),
                usage.cacheWriteTokens(),
                usage.reasoningTokens()
        );
    }
}
