package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionService;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionResponse;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionStreamChunk;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionStreamResponse;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

@WebFluxTest(controllers = OpenAiResponsesController.class)
@Import(PermitAllSecurityTestConfig.class)
class OpenAiResponsesControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private DistributedKeyAuthenticationService distributedKeyAuthenticationService;

    @MockitoBean
    private GatewayChatExecutionService gatewayChatExecutionService;

    @MockitoBean
    private GatewayAsyncResourceService gatewayAsyncResourceService;

    @Test
    void shouldExecuteMinimalResponsesRequest() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.execute(Mockito.<ChatExecutionRequest>argThat(request ->
                        request != null
                                && "responses".equals(request.protocol())
                                && "/v1/responses".equals(request.requestPath())
                                && "writer-fast".equals(request.requestedModel())
                                && request.messages().size() == 2
                                && "system".equals(request.messages().get(0).role())
                                && "你是一个助手".equals(request.messages().get(0).content())
                                && "user".equals(request.messages().get(1).role())
                                && "hello responses".equals(request.messages().get(1).content())
                )))
                .thenReturn(new ChatExecutionResponse(
                        "req-responses-1",
                        selectionResult(),
                        "hello back",
                        new GatewayUsage(100, 40, 20, 5, 60, 0, 20, 0, null, 160, null),
                        List.of(),
                        "reasoning trace"
                ));

        webTestClient.post()
                .uri("/v1/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"writer-fast",
                          "instructions":"你是一个助手",
                          "input":"hello responses"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("response")
                .jsonPath("$.model").isEqualTo("writer-fast")
                .jsonPath("$.output_text").isEqualTo("hello back")
                .jsonPath("$.output[0].type").isEqualTo("reasoning")
                .jsonPath("$.output[0].summary[0].text").isEqualTo("reasoning trace")
                .jsonPath("$.output[1].type").isEqualTo("message")
                .jsonPath("$.output[1].content[0].type").isEqualTo("output_text")
                .jsonPath("$.usage.input_tokens_details.cached_tokens").isEqualTo(60);
    }

    @Test
    void shouldAcceptResponsesStyleToolsAndReturnFunctionCalls() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.execute(Mockito.<ChatExecutionRequest>argThat(request ->
                        request != null
                                && request.tools().size() == 1
                                && "lookup_weather".equals(request.tools().get(0).name())
                                && "auto".equals(request.toolChoice().asText())
                )))
                .thenReturn(new ChatExecutionResponse(
                        "req-responses-tool-1",
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
                .uri("/v1/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"writer-fast",
                          "input":[
                            {
                              "role":"user",
                              "content":[
                                {"type":"input_text","text":"帮我查询上海天气"}
                              ]
                            }
                          ],
                          "tools":[
                            {
                              "type":"function",
                              "name":"lookup_weather",
                              "description":"Lookup weather",
                              "parameters":{"type":"object"}
                            }
                          ],
                          "tool_choice":"auto"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.output[0].type").isEqualTo("function_call")
                .jsonPath("$.output[0].name").isEqualTo("lookup_weather")
                .jsonPath("$.output[0].arguments").isEqualTo("{\"city\":\"Shanghai\"}");
    }

    @Test
    void shouldAcceptFunctionCallOutputOnlyInput() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.execute(Mockito.<ChatExecutionRequest>argThat(request ->
                        request != null
                                && request.messages().size() == 1
                                && "tool".equals(request.messages().get(0).role())
                                && "call_1".equals(request.messages().get(0).toolCallId())
                                && "Shanghai is sunny".equals(request.messages().get(0).content())
                )))
                .thenReturn(new ChatExecutionResponse(
                        "req-responses-tool-output-1",
                        selectionResult(),
                        "工具结果已接收",
                        GatewayUsage.empty(),
                        List.of()
                ));

        webTestClient.post()
                .uri("/v1/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"writer-fast",
                          "input":[
                            {
                              "type":"function_call_output",
                              "call_id":"call_1",
                              "output":"Shanghai is sunny"
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.output_text").isEqualTo("工具结果已接收");
    }

    @Test
    void shouldAcceptMixedConversationItemsWithFunctionCallOutput() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.execute(Mockito.<ChatExecutionRequest>argThat(request ->
                        request != null
                                && request.messages().size() == 2
                                && "user".equals(request.messages().get(0).role())
                                && "继续处理这个工具结果".equals(request.messages().get(0).content())
                                && "tool".equals(request.messages().get(1).role())
                                && "call_1".equals(request.messages().get(1).toolCallId())
                                && "{\"city\":\"Shanghai\"}".equals(request.messages().get(1).content())
                )))
                .thenReturn(new ChatExecutionResponse(
                        "req-responses-mixed-tool-output-1",
                        selectionResult(),
                        "继续生成完成",
                        GatewayUsage.empty(),
                        List.of()
                ));

        webTestClient.post()
                .uri("/v1/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"writer-fast",
                          "input":[
                            {
                              "role":"user",
                              "content":[
                                {"type":"input_text","text":"继续处理这个工具结果"}
                              ]
                            },
                            {
                              "type":"function_call_output",
                              "call_id":"call_1",
                              "output":{"city":"Shanghai"}
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.output_text").isEqualTo("继续生成完成");
    }

    @Test
    void shouldAcceptTopLevelInputImageByFileId() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.execute(Mockito.<ChatExecutionRequest>argThat(request ->
                        request != null
                                && request.messages().size() == 1
                                && "user".equals(request.messages().get(0).role())
                                && request.messages().get(0).media().size() == 1
                                && "image".equals(request.messages().get(0).media().get(0).kind())
                                && "gateway://file-123".equals(request.messages().get(0).media().get(0).url())
                                && "image/png".equals(request.messages().get(0).media().get(0).mimeType())
                )))
                .thenReturn(new ChatExecutionResponse(
                        "req-responses-image-fileid-1",
                        selectionResult(),
                        "图片已接收",
                        GatewayUsage.empty(),
                        List.of()
                ));

        webTestClient.post()
                .uri("/v1/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"writer-fast",
                          "input":[
                            {
                              "type":"input_image",
                              "file_id":"file-123",
                              "mime_type":"image/png"
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.output_text").isEqualTo("图片已接收");
    }

    @Test
    void shouldStreamResponsesEvents() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeStream(Mockito.<ChatExecutionRequest>argThat(request ->
                        request != null
                                && "responses".equals(request.protocol())
                                && "/v1/responses".equals(request.requestPath())
                                && "writer-fast".equals(request.requestedModel())
                )))
                .thenReturn(new ChatExecutionStreamResponse(
                        "req-responses-stream-1",
                        selectionResult(),
                        Flux.just(
                                new ChatExecutionStreamChunk(
                                        "hello",
                                        null,
                                        new GatewayUsage(100, 40, 20, 5, 60, 0, 20, 0, null, 160, null),
                                        false
                                ),
                                new ChatExecutionStreamChunk(null, "stop", GatewayUsage.empty(), true)
                        )
                ));

        var result = webTestClient.post()
                .uri("/v1/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"writer-fast",
                          "input":"hello",
                          "stream":true
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(String.class);

        var body = result.getResponseBody().collectList().block();
        assert body != null;
        String joined = String.join("", body);
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("event: response.created"));
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("event: response.output_text.delta"));
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("\"delta\":\"hello\""));
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("event: response.completed"));
    }

    @Test
    void shouldStreamResponsesReasoningEvents() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeStream(Mockito.<ChatExecutionRequest>argThat(request ->
                        request != null
                                && "responses".equals(request.protocol())
                                && "/v1/responses".equals(request.requestPath())
                )))
                .thenReturn(new ChatExecutionStreamResponse(
                        "req-responses-stream-reasoning-1",
                        selectionResult(),
                        Flux.just(
                                new ChatExecutionStreamChunk(
                                        null,
                                        null,
                                        GatewayUsage.empty(),
                                        false,
                                        List.of(),
                                        "step 1"
                                ),
                                new ChatExecutionStreamChunk("hello", null, GatewayUsage.empty(), false),
                                new ChatExecutionStreamChunk(null, "stop", GatewayUsage.empty(), true)
                        )
                ));

        var result = webTestClient.post()
                .uri("/v1/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"writer-fast",
                          "input":"hello",
                          "stream":true
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(String.class);

        var body = result.getResponseBody().collectList().block();
        assert body != null;
        String joined = String.join("", body);
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("event: response.reasoning_summary_text.delta"));
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("event: response.reasoning_summary_text.done"));
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("\"text\":\"step 1\""));
    }

    @Test
    void shouldStreamResponsesFunctionCallEvents() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeStream(Mockito.<ChatExecutionRequest>argThat(request ->
                        request != null
                                && "responses".equals(request.protocol())
                                && request.tools().size() == 1
                                && "lookup_weather".equals(request.tools().get(0).name())
                )))
                .thenReturn(new ChatExecutionStreamResponse(
                        "req-responses-stream-tool-1",
                        selectionResult(),
                        Flux.just(
                                new ChatExecutionStreamChunk(
                                        null,
                                        null,
                                        GatewayUsage.empty(),
                                        false,
                                        List.of(new GatewayToolCall(
                                                "call_1",
                                                "function",
                                                "lookup_weather",
                                                "{\"city\":\"Shanghai\"}"
                                        ))
                                ),
                                new ChatExecutionStreamChunk(null, "stop", GatewayUsage.empty(), true)
                        )
                ));

        var result = webTestClient.post()
                .uri("/v1/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"writer-fast",
                          "input":"帮我查询上海天气",
                          "stream":true,
                          "tools":[
                            {
                              "type":"function",
                              "name":"lookup_weather",
                              "description":"Lookup weather",
                              "parameters":{"type":"object"}
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(String.class);

        var body = result.getResponseBody().collectList().block();
        assert body != null;
        String joined = String.join("", body);
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("event: response.function_call_arguments.delta"));
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("event: response.function_call_arguments.done"));
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("\"name\":\"lookup_weather\""));
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("{\\\"city\\\":\\\"Shanghai\\\"}"));
    }

    @Test
    void shouldGetStoredResponse() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayAsyncResourceService.getResponse("resp_stored_1", 1L))
                .thenReturn(new ObjectMapper().createObjectNode()
                        .put("id", "resp_stored_1")
                        .put("object", "response")
                        .put("status", "completed"));

        webTestClient.get()
                .uri("/v1/responses/resp_stored_1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("resp_stored_1")
                .jsonPath("$.status").isEqualTo("completed");
    }

    @Test
    void shouldDeleteStoredResponse() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayAsyncResourceService.deleteResponse("resp_stored_1", 1L))
                .thenReturn(new ObjectMapper().createObjectNode()
                        .put("id", "resp_stored_1")
                        .put("object", "response.deleted")
                        .put("deleted", true));

        webTestClient.delete()
                .uri("/v1/responses/resp_stored_1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("response.deleted")
                .jsonPath("$.deleted").isEqualTo(true);
    }

    private RouteSelectionResult selectionResult() {
        CatalogCandidateView candidate = new CatalogCandidateView(
                101L,
                "openai-primary",
                ProviderType.OPENAI_DIRECT,
                "https://api.openai.com",
                "gpt-4o",
                "gpt-4o",
                List.of("openai", "responses"),
                true,
                false,
                true,
                true,
                true,
                true,
                ReasoningTransport.RESPONSES
        );
        RouteCandidateView routeCandidateView = new RouteCandidateView(candidate, 11L, 10, 100);
        return new RouteSelectionResult(
                1L,
                "sk-gw-test",
                "writer-fast",
                "writer-fast",
                "gpt-4o",
                "responses",
                "prefix-hash",
                "fingerprint",
                "gpt-4o",
                RouteSelectionSource.PREFIX_AFFINITY,
                routeCandidateView,
                List.of(routeCandidateView)
        );
    }
}
