package com.prodigalgal.xaigateway.protocol.ingress.anthropic;

import com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionService;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionResponse;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionStreamChunk;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionStreamResponse;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

@WebFluxTest(controllers = AnthropicMessagesController.class)
@Import(PermitAllSecurityTestConfig.class)
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
        Mockito.when(gatewayChatExecutionService.execute(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(new ChatExecutionResponse(
                        "req-anthropic-1",
                        selectionResult(),
                        "anthropic back",
                        new GatewayUsage(900, 900, 200, 0, 300, 120, 300, 120, null, 1520, null),
                        List.of()
                ));

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
                          "maxTokens": 256
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
        Mockito.when(gatewayChatExecutionService.execute(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(new ChatExecutionResponse(
                        "req-anthropic-tool-1",
                        selectionResult(),
                        "",
                        GatewayUsage.empty(),
                        List.of(new GatewayToolCall(
                                "toolu_1",
                                "tool_use",
                                "lookup_weather",
                                "{\"city\":\"Shanghai\"}"
                        ))
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
                              "inputSchema":{"type":"object"}
                            }
                          ],
                          "maxTokens": 256
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
    void shouldAcceptAnthropicImageBlocks() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.execute(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(new ChatExecutionResponse(
                        "req-anthropic-image-1",
                        selectionResult(),
                        "image processed",
                        GatewayUsage.empty(),
                        List.of()
                ));

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
                          "maxTokens": 256
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
        Mockito.when(gatewayChatExecutionService.execute(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(new ChatExecutionResponse(
                        "req-anthropic-doc-1",
                        selectionResult(),
                        "document processed",
                        GatewayUsage.empty(),
                        List.of()
                ));

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
                          "maxTokens": 256
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
        Mockito.when(gatewayChatExecutionService.execute(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(new ChatExecutionResponse(
                        "req-anthropic-fileid-1",
                        selectionResult(),
                        "gateway document processed",
                        GatewayUsage.empty(),
                        List.of()
                ));

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
                          "maxTokens": 256
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
        Mockito.when(gatewayChatExecutionService.execute(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(new ChatExecutionResponse(
                        "req-anthropic-doc-only-1",
                        selectionResult(),
                        "document only processed",
                        GatewayUsage.empty(),
                        List.of()
                ));

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
                          "maxTokens": 256
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
        Mockito.when(gatewayChatExecutionService.executeStream(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(new ChatExecutionStreamResponse(
                        "req-anthropic-stream-1",
                        selectionResult(),
                        Flux.just(
                                new ChatExecutionStreamChunk("hello", null, GatewayUsage.empty(), false),
                                new ChatExecutionStreamChunk(null, "end_turn", GatewayUsage.empty(), true)
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
                          "maxTokens": 256,
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
}
