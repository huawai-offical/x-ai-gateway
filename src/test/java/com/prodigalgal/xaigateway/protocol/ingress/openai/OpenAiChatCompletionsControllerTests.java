package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionService;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayResponse;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamEventType;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamResponse;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageCompleteness;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageSource;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageView;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

@WebFluxTest(controllers = OpenAiChatCompletionsController.class)
@Import({PermitAllSecurityTestConfig.class, OpenAiChatCompletionRequestMapper.class})
class OpenAiChatCompletionsControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private DistributedKeyAuthenticationService distributedKeyAuthenticationService;

    @MockitoBean
    private GatewayChatExecutionService gatewayChatExecutionService;

    @Test
    void shouldExecuteMinimalOpenAiCompletion() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(gatewayResponse(
                        "req-openai-1",
                        "hello back",
                        new GatewayUsage(1000, 700, 200, 20, 300, 0, 300, 0, null, 1200, null),
                        List.of(),
                        GatewayFinishReason.STOP
                ));

        webTestClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "gpt-4o",
                          "messages": [
                            {"role":"system","content":"you are helpful"},
                            {"role":"user","content":"hello"}
                          ],
                          "temperature": 0.2,
                          "max_tokens": 256
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("chat.completion")
                .jsonPath("$.choices[0].message.content").isEqualTo("hello back")
                .jsonPath("$.usage.prompt_tokens_details.cached_tokens").isEqualTo(300)
                .jsonPath("$.usage.completion_tokens_details.reasoning_tokens").isEqualTo(20);
    }

    @Test
    void shouldExecuteMinimalOpenAiStreamCompletion() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayStream(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(new GatewayStreamResponse(
                        "req-openai-stream-1",
                        selectionResult(),
                        Flux.just(
                                textEvent("hello"),
                                completedEvent(GatewayFinishReason.STOP, "hello", GatewayUsage.empty())
                        )
                ));

        var result = webTestClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "gpt-4o",
                          "messages": [
                            {"role":"user","content":"hello"}
                          ],
                          "stream": true
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class);

        var body = result.getResponseBody().collectList().block();
        assert body != null;
        String joined = String.join("", body);
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("chat.completion.chunk"));
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("[DONE]"));
    }

    @Test
    void shouldStreamToolCallDeltas() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayStream(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(new GatewayStreamResponse(
                        "req-openai-stream-tool-1",
                        selectionResult(),
                        Flux.just(
                                toolCallEvent(List.of(new GatewayToolCall(
                                        "call_1",
                                        "function",
                                        "lookup_weather",
                                        "{\"city\":\"Shanghai\"}"
                                ))),
                                completedEvent(GatewayFinishReason.TOOL_CALLS, "", GatewayUsage.empty())
                        )
                ));

        var result = webTestClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "gpt-4o",
                          "messages": [
                            {"role":"user","content":"帮我查询上海天气"}
                          ],
                          "tools": [
                            {
                              "type":"function",
                              "function":{
                                "name":"lookup_weather",
                                "description":"Lookup weather",
                                "parameters":{"type":"object"}
                              }
                            }
                          ],
                          "stream": true
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class);

        var body = result.getResponseBody().collectList().block();
        assert body != null;
        String joined = String.join("", body);
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("\"tool_calls\""));
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("\"lookup_weather\""));
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("{\\\"city\\\":\\\"Shanghai\\\"}"));
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("\"finish_reason\":\"tool_calls\""));
    }

    @Test
    void shouldReturnToolCallsWhenModelRequestsFunctionExecution() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(gatewayResponse(
                        "req-openai-tool-1",
                        "",
                        GatewayUsage.empty(),
                        List.of(new GatewayToolCall(
                                "call_1",
                                "function",
                                "lookup_weather",
                                "{\"city\":\"Shanghai\"}"
                        )),
                        GatewayFinishReason.TOOL_CALLS
                ));

        webTestClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "gpt-4o",
                          "messages": [
                            {"role":"user","content":"帮我查询上海天气"}
                          ],
                          "tools": [
                            {
                              "type":"function",
                              "function":{
                                "name":"lookup_weather",
                                "description":"Lookup weather",
                                "parameters":{"type":"object"}
                              }
                            }
                          ],
                          "tool_choice":"auto"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.choices[0].finish_reason").isEqualTo("tool_calls")
                .jsonPath("$.choices[0].message.tool_calls[0].function.name").isEqualTo("lookup_weather")
                .jsonPath("$.choices[0].message.tool_calls[0].function.arguments").isEqualTo("{\"city\":\"Shanghai\"}");
    }

    @Test
    void shouldAcceptOpenAiContentArrayWithImageUrl() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(gatewayResponse("req-openai-image-1", "image processed", GatewayUsage.empty(), List.of(), GatewayFinishReason.STOP));

        webTestClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "gpt-4o",
                          "messages": [
                            {
                              "role":"user",
                              "content":[
                                {"type":"text","text":"请描述这张图片"},
                                {"type":"image_url","image_url":{"url":"https://example.com/cat.png"}}
                              ]
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.choices[0].message.content").isEqualTo("image processed");
    }

    @Test
    void shouldAcceptOpenAiInputFileBlocks() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(gatewayResponse("req-openai-file-1", "file processed", GatewayUsage.empty(), List.of(), GatewayFinishReason.STOP));

        webTestClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "gpt-4o",
                          "messages": [
                            {
                              "role":"user",
                              "content":[
                                {"type":"text","text":"总结这个文档"},
                                {"type":"input_file","input_file":{"url":"https://example.com/file.pdf","mime_type":"application/pdf","filename":"file.pdf"}}
                              ]
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.choices[0].message.content").isEqualTo("file processed");
    }

    @Test
    void shouldAcceptOpenAiGatewayFileIdBlocks() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(gatewayResponse("req-openai-fileid-1", "gateway file processed", GatewayUsage.empty(), List.of(), GatewayFinishReason.STOP));

        webTestClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "gpt-4o",
                          "messages": [
                            {
                              "role":"user",
                              "content":[
                                {"type":"input_file","input_file":{"file_id":"file-123","mime_type":"application/pdf","filename":"doc.pdf"}}
                              ]
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.choices[0].message.content").isEqualTo("gateway file processed");
    }

    @Test
    void shouldAcceptOpenAiImageOnlyMessage() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(gatewayResponse("req-openai-image-only-1", "image only processed", GatewayUsage.empty(), List.of(), GatewayFinishReason.STOP));

        webTestClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "gpt-4o",
                          "messages": [
                            {
                              "role":"user",
                              "content":[
                                {"type":"image_url","image_url":{"url":"https://example.com/cat.png"}}
                              ]
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.choices[0].message.content").isEqualTo("image only processed");
    }

    @Test
    void shouldRejectOpenAiCompletionWithoutUserMessage() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));

        webTestClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "gpt-4o",
                          "messages": [
                            {"role":"system","content":"you are helpful"}
                          ]
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
                101L,
                "openai-primary",
                ProviderType.OPENAI_DIRECT,
                "https://api.openai.com",
                "gpt-4o",
                "gpt-4o",
                List.of("openai"),
                true,
                false,
                true,
                true,
                true,
                true,
                ReasoningTransport.OPENAI_CHAT
        );
        RouteCandidateView routeCandidateView = new RouteCandidateView(candidate, 11L, 10, 100);
        return new RouteSelectionResult(
                1L,
                "sk-gw-test",
                "gpt-4o",
                "gpt-4o",
                "gpt-4o",
                "openai",
                "prefix-hash",
                "fingerprint",
                "gpt-4o",
                RouteSelectionSource.PREFIX_AFFINITY,
                routeCandidateView,
                List.of(routeCandidateView)
        );
    }

    private GatewayResponse gatewayResponse(
            String requestId,
            String text,
            GatewayUsage usage,
            List<GatewayToolCall> toolCalls,
            GatewayFinishReason finishReason) {
        return new GatewayResponse(
                requestId,
                selectionResult(),
                text,
                usageView(usage),
                toolCalls,
                null,
                finishReason,
                null
        );
    }

    private GatewayUsageView usageView(GatewayUsage usage) {
        return new GatewayUsageView(
                usage.rawPromptTokens(),
                usage.promptTokens(),
                usage.completionTokens(),
                usage.reasoningTokens(),
                usage.cacheHitTokens(),
                usage.cacheWriteTokens(),
                usage.upstreamCacheHitTokens(),
                usage.upstreamCacheWriteTokens(),
                usage.savedInputTokens(),
                usage.cachedContentRef(),
                usage.totalTokens(),
                GatewayUsageCompleteness.FINAL,
                GatewayUsageSource.DIRECT_RESPONSE,
                usage.nativeUsagePayload()
        );
    }

    private GatewayStreamEvent textEvent(String delta) {
        return new GatewayStreamEvent(
                GatewayStreamEventType.TEXT_DELTA,
                delta,
                null,
                List.of(),
                GatewayUsageView.empty(),
                false,
                null,
                null,
                null,
                null
        );
    }

    private GatewayStreamEvent toolCallEvent(List<GatewayToolCall> toolCalls) {
        return new GatewayStreamEvent(
                GatewayStreamEventType.TOOL_CALLS,
                null,
                null,
                toolCalls,
                GatewayUsageView.empty(),
                false,
                null,
                null,
                null,
                null
        );
    }

    private GatewayStreamEvent completedEvent(GatewayFinishReason finishReason, String outputText, GatewayUsage usage) {
        return new GatewayStreamEvent(
                GatewayStreamEventType.COMPLETED,
                null,
                null,
                List.of(),
                usage.isEmpty() ? GatewayUsageView.empty() : usageView(usage),
                true,
                finishReason,
                outputText,
                null,
                null
        );
    }
}
