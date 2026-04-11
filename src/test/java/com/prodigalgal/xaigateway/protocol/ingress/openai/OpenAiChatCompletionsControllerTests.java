package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionService;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionResponse;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionStreamChunk;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionStreamResponse;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
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
@Import(PermitAllSecurityTestConfig.class)
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
        Mockito.when(gatewayChatExecutionService.execute(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(new ChatExecutionResponse(
                        "req-openai-1",
                        selectionResult(),
                        "hello back",
                        new GatewayUsage(1000, 700, 200, 20, 300, 0, 300, 0, null, 1200, null),
                        List.of()
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
        Mockito.when(gatewayChatExecutionService.executeStream(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(new ChatExecutionStreamResponse(
                        "req-openai-stream-1",
                        selectionResult(),
                        Flux.just(
                                new ChatExecutionStreamChunk("hello", null, GatewayUsage.empty(), false),
                                new ChatExecutionStreamChunk(null, "stop", GatewayUsage.empty(), true)
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
    void shouldReturnToolCallsWhenModelRequestsFunctionExecution() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.execute(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(new ChatExecutionResponse(
                        "req-openai-tool-1",
                        selectionResult(),
                        "",
                        GatewayUsage.empty(),
                        List.of(new GatewayToolCall(
                                "call_1",
                                "function",
                                "lookup_weather",
                                "{\"city\":\"Shanghai\"}"
                        ))
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
        Mockito.when(gatewayChatExecutionService.execute(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(new ChatExecutionResponse(
                        "req-openai-image-1",
                        selectionResult(),
                        "image processed",
                        GatewayUsage.empty(),
                        List.of()
                ));

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
        Mockito.when(gatewayChatExecutionService.execute(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(new ChatExecutionResponse(
                        "req-openai-file-1",
                        selectionResult(),
                        "file processed",
                        GatewayUsage.empty(),
                        List.of()
                ));

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
        Mockito.when(gatewayChatExecutionService.execute(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(new ChatExecutionResponse(
                        "req-openai-fileid-1",
                        selectionResult(),
                        "gateway file processed",
                        GatewayUsage.empty(),
                        List.of()
                ));

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
        Mockito.when(gatewayChatExecutionService.execute(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(new ChatExecutionResponse(
                        "req-openai-image-only-1",
                        selectionResult(),
                        "image only processed",
                        GatewayUsage.empty(),
                        List.of()
                ));

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
}
