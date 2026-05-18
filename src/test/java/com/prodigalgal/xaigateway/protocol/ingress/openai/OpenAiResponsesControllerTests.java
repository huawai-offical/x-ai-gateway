package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionService;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamilyResolver;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionResult;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionStreamResult;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessageRole;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalPartType;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResponse;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEventType;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolCall;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalUsage;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayOpenAiPassthroughService;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceService;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

@WebFluxTest(controllers = OpenAiResponsesController.class)
@Import({PermitAllSecurityTestConfig.class, OpenAiResponsesRequestMapper.class, OpenAiResponsesFileSearchBindingService.class, GatewayClientFamilyResolver.class})
class OpenAiResponsesControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private DistributedKeyAuthenticationService distributedKeyAuthenticationService;

    @MockitoBean
    private GatewayChatExecutionService gatewayChatExecutionService;

    @MockitoBean
    private GatewayAsyncResourceService gatewayAsyncResourceService;

    @MockitoBean
    private GatewayOpenAiPassthroughService gatewayOpenAiPassthroughService;

    @MockitoBean
    private OpenAiIdempotencyReplayService openAiIdempotencyReplayService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUpIdempotencyReplayService() {
        Mockito.when(openAiIdempotencyReplayService.replay(
                        Mockito.anyLong(),
                        Mockito.anyString(),
                        Mockito.<String>nullable(String.class),
                        Mockito.any(tools.jackson.databind.JsonNode.class)
                ))
                .thenReturn(Optional.empty());
        Mockito.when(openAiIdempotencyReplayService.remember(
                        Mockito.anyLong(),
                        Mockito.anyString(),
                        Mockito.<String>nullable(String.class),
                        Mockito.any(tools.jackson.databind.JsonNode.class),
                        Mockito.any(tools.jackson.databind.JsonNode.class)
                ))
                .thenAnswer(invocation -> invocation.getArgument(4));
        Mockito.when(gatewayOpenAiPassthroughService.executeOpenAiDirectJson(
                        Mockito.anyString(),
                        Mockito.eq("/v1/responses/input_tokens"),
                        Mockito.any(tools.jackson.databind.JsonNode.class),
                        Mockito.<String>nullable(String.class)
                ))
                .thenThrow(new IllegalArgumentException("native input_tokens unavailable"));
        Mockito.when(gatewayOpenAiPassthroughService.executeOpenAiDirectJson(
                        Mockito.anyString(),
                        Mockito.eq("/v1/responses/compact"),
                        Mockito.any(tools.jackson.databind.JsonNode.class),
                        Mockito.<String>nullable(String.class)
                ))
                .thenThrow(new IllegalArgumentException("native compact unavailable"));
    }

    @Test
    void shouldExecuteMinimalResponsesRequest() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>argThat(request ->
                        request != null
                                && request.ingressProtocol() == CanonicalIngressProtocol.RESPONSES
                                && "/v1/responses".equals(request.requestPath())
                                && "writer-fast".equals(request.requestedModel())
                                && request.messages().size() == 2
                                && request.messages().get(0).role() == CanonicalMessageRole.SYSTEM
                                && "你是一个助手".equals(request.messages().get(0).parts().get(0).text())
                                && request.messages().get(1).role() == CanonicalMessageRole.USER
                                && "hello responses".equals(request.messages().get(1).parts().get(0).text())
                )))
                .thenReturn(gatewayResponse(
                        "req-responses-1",
                        "hello back",
                        new GatewayUsage(100, 40, 20, 5, 60, 0, 20, 0, null, 160, null),
                        List.of(),
                        "reasoning trace",
                        GatewayFinishReason.STOP
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
    void shouldReturnNativeRawResponsesJsonWhenAvailable() throws Exception {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>argThat(request ->
                        request != null
                                && request.ingressProtocol() == CanonicalIngressProtocol.RESPONSES
                                && "/v1/responses".equals(request.requestPath())
                )))
                .thenReturn(gatewayResponseRaw(
                        "req-responses-raw-1",
                        "raw text",
                        objectMapper.readTree("""
                                {
                                  "id":"resp_native_raw_1",
                                  "object":"response",
                                  "status":"completed",
                                  "model":"gpt-4.1-mini",
                                  "output":[
                                    {
                                      "id":"msg_raw_1",
                                      "type":"message",
                                      "role":"assistant",
                                      "content":[
                                        {
                                          "type":"output_text",
                                          "text":"raw text",
                                          "annotations":[{"type":"url_citation","url":"https://example.com"}]
                                        }
                                      ]
                                    }
                                  ],
                                  "output_text":"raw text",
                                  "incomplete_details":{"reason":"max_output_tokens"}
                                }
                                """)
                ));

        webTestClient.post()
                .uri("/v1/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"writer-fast",
                          "input":"hello raw"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("resp_native_raw_1")
                .jsonPath("$.model").isEqualTo("writer-fast")
                .jsonPath("$.output[0].content[0].annotations[0].type").isEqualTo("url_citation")
                .jsonPath("$.incomplete_details.reason").isEqualTo("max_output_tokens")
                .jsonPath("$.output_text").isEqualTo("raw text");
    }

    @Test
    void shouldReplayIdempotentResponsesPayloadWithoutExecutingGateway() throws Exception {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        var replayedPayload = objectMapper.readTree("""
                {
                  "id": "resp_cached",
                  "object": "response",
                  "model": "writer-fast",
                  "output_text": "cached response",
                  "output": []
                }
                """);
        Mockito.when(openAiIdempotencyReplayService.replay(
                        Mockito.eq(1L),
                        Mockito.eq("/v1/responses"),
                        Mockito.eq("idem-responses-replay-1"),
                        Mockito.any(tools.jackson.databind.JsonNode.class)
                ))
                .thenReturn(Optional.of(replayedPayload));

        webTestClient.post()
                .uri("/v1/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .header("Idempotency-Key", "idem-responses-replay-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"writer-fast",
                          "input":"hello replay"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(OpenAiIdempotencyReplayService.REPLAYED_HEADER, "true")
                .expectBody()
                .jsonPath("$.id").isEqualTo("resp_cached")
                .jsonPath("$.output_text").isEqualTo("cached response");

        Mockito.verifyNoInteractions(gatewayChatExecutionService);
    }

    @Test
    void shouldAcceptResponsesStyleToolsAndReturnFunctionCalls() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>argThat(request ->
                        request != null
                                && request.tools().size() == 1
                                && "lookup_weather".equals(request.tools().get(0).name())
                                && "auto".equals(request.toolChoice().asText())
                )))
                .thenReturn(gatewayResponse(
                        "req-responses-tool-1",
                        "",
                        GatewayUsage.empty(),
                        List.of(new GatewayToolCall(
                                "call_1",
                                "function",
                                "lookup_weather",
                                "{\"city\":\"Shanghai\"}"
                        )),
                        null,
                        GatewayFinishReason.TOOL_CALLS
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
    void shouldBindResponsesFileSearchToLocalVectorStoreContext() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayAsyncResourceService.searchVectorStore(
                Mockito.eq("vs_1"),
                Mockito.eq(1L),
                Mockito.argThat(request -> request.path("query").asText().contains("refund policy")
                        && request.path("max_num_results").asInt() == 2)
        )).thenReturn(fileSearchPage());
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>argThat(request ->
                        request != null
                                && request.tools().isEmpty()
                                && request.providerExtensions().path("instructions").asText().contains("Local file_search context")
                                && request.providerExtensions().path("instructions").asText().contains("refund policy allows quarterly credits")
                                && !request.providerExtensions().toString().contains("\"type\":\"file_search\"")
                )))
                .thenReturn(gatewayResponse(
                        "req-file-search-local-1",
                        "退款政策允许季度抵扣。",
                        GatewayUsage.empty(),
                        List.of(),
                        null,
                        GatewayFinishReason.STOP
                ));

        webTestClient.post()
                .uri("/v1/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"writer-fast",
                          "input":"What is the refund policy?",
                          "tools":[
                            {
                              "type":"file_search",
                              "vector_store_ids":["vs_1"],
                              "max_num_results":2
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.output_text").isEqualTo("退款政策允许季度抵扣。");
    }

    @Test
    void shouldRejectUnsupportedResponsesToolInsteadOfSilentlyDroppingIt() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));

        webTestClient.post()
                .uri("/v1/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"writer-fast",
                          "input":"请搜索最新公告",
                          "tools":[
                            {
                              "type":"web_search_preview",
                              "search_context_size":"low"
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.type").isEqualTo("invalid_request_error")
                .jsonPath("$.error.code").isEqualTo("invalid_argument")
                .jsonPath("$.error.message").value(message ->
                        org.junit.jupiter.api.Assertions.assertTrue(message.toString().contains("web_search_preview")));

        Mockito.verifyNoInteractions(gatewayChatExecutionService);
    }

    @Test
    void shouldRejectUnsupportedResponsesToolChoiceInsteadOfSilentlyDroppingIt() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));

        webTestClient.post()
                .uri("/v1/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"writer-fast",
                          "input":"调用 MCP 工具",
                          "tool_choice":{
                            "type":"mcp",
                            "server_label":"deepwiki"
                          }
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.type").isEqualTo("invalid_request_error")
                .jsonPath("$.error.code").isEqualTo("invalid_argument")
                .jsonPath("$.error.message").value(message ->
                        org.junit.jupiter.api.Assertions.assertTrue(message.toString().contains("tool_choice type 'mcp'")));

        Mockito.verifyNoInteractions(gatewayChatExecutionService);
    }

    @Test
    void shouldRejectUnsupportedAllowedToolsChoiceInsteadOfSilentlyDroppingIt() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));

        webTestClient.post()
                .uri("/v1/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"writer-fast",
                          "input":"请执行代码",
                          "tool_choice":{
                            "type":"allowed_tools",
                            "mode":"required",
                            "tools":[
                              {"type":"code_interpreter"}
                            ]
                          }
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.type").isEqualTo("invalid_request_error")
                .jsonPath("$.error.code").isEqualTo("invalid_argument")
                .jsonPath("$.error.message").value(message ->
                        org.junit.jupiter.api.Assertions.assertTrue(message.toString().contains("code_interpreter")));

        Mockito.verifyNoInteractions(gatewayChatExecutionService);
    }

    @Test
    void shouldPreserveCodexCliIngressMetadataForRoutingAndTracing() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(
                        Mockito.<CanonicalRequest>argThat(request -> {
                            var metadata = request == null ? null : request.metadata();
                            return metadata != null
                                    && "CODEX".equals(metadata.clientFamily())
                                    && "desktop-main".equals(metadata.clientInstance())
                                    && "workspace-alpha".equals(metadata.workspaceHint())
                                    && "session_id".equals(metadata.sessionAffinitySource())
                                    && metadata.sessionAffinityKey() != null
                                    && metadata.sessionAffinityKey().length() == 32
                                    && !"session-raw-1".equals(metadata.sessionAffinityKey())
                                    && "assistants=v2".equals(metadata.openAiBeta())
                                    && "org-codex-1".equals(metadata.openAiOrganization())
                                    && "proj-codex-1".equals(metadata.openAiProject())
                                    && "idem-codex-1".equals(metadata.idempotencyKey())
                                    && "codex-cli".equals(metadata.originator())
                                    && metadata.userAgent().contains("codex");
                        }),
                        Mockito.eq(GatewayClientFamily.CODEX)
                ))
                .thenReturn(gatewayResponse("req-codex-metadata-1", "ok", GatewayUsage.empty(), List.of(), null, GatewayFinishReason.STOP));

        webTestClient.post()
                .uri("/v1/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .header(HttpHeaders.USER_AGENT, "codex-cli/0.32.0")
                .header("X-AI-Gateway-Client-Instance", "desktop-main")
                .header("X-AI-Gateway-Workspace-Hint", "workspace-alpha")
                .header("openai-beta", "assistants=v2")
                .header("OpenAI-Organization", "org-codex-1")
                .header("OpenAI-Project", "proj-codex-1")
                .header("Idempotency-Key", "idem-codex-1")
                .header("originator", "codex-cli")
                .header("session_id", "session-raw-1")
                .header("conversation_id", "conversation-raw-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"writer-fast",
                          "input":"hello codex"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.output_text").isEqualTo("ok");
    }

    @Test
    void shouldPreserveResponsesParityFieldsAndNestedReasoningEffort() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>argThat(request -> {
            var extensions = request == null ? null : request.providerExtensions();
            return request != null
                    && request.reasoning() != null
                    && "medium".equals(request.reasoning().effort())
                    && extensions != null
                    && "auto".equals(extensions.path("service_tier").asText())
                    && !extensions.path("parallel_tool_calls").asBoolean(true)
                    && "cache-key-1".equals(extensions.path("prompt_cache_key").asText())
                    && "auto".equals(extensions.path("truncation").asText())
                    && "json_schema".equals(extensions.path("text").path("format").path("type").asText());
        })))
                .thenReturn(gatewayResponse("req-responses-parity-1", "ok", GatewayUsage.empty(), List.of(), null, GatewayFinishReason.STOP));

        webTestClient.post()
                .uri("/v1/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"writer-fast",
                          "input":"hello parity",
                          "service_tier":"auto",
                          "parallel_tool_calls":false,
                          "prompt_cache_key":"cache-key-1",
                          "top_logprobs":2,
                          "truncation":"auto",
                          "text":{"format":{"type":"json_schema","name":"answer"}},
                          "reasoning":{"effort":"medium","summary":"auto"}
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.output_text").isEqualTo("ok");
    }

    @Test
    void shouldAcceptFunctionCallOutputOnlyInput() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>argThat(request ->
                        request != null
                                && request.messages().size() == 1
                                && request.messages().get(0).role() == CanonicalMessageRole.TOOL
                                && request.messages().get(0).parts().stream().anyMatch(part ->
                                        part.type() == CanonicalPartType.TOOL_RESULT
                                                && "call_1".equals(part.toolCallId())
                                                && "Shanghai is sunny".equals(part.text()))
                )))
                .thenReturn(gatewayResponse("req-responses-tool-output-1", "工具结果已接收", GatewayUsage.empty(), List.of(), null, GatewayFinishReason.STOP));

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
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>argThat(request ->
                        request != null
                                && request.messages().size() == 2
                                && request.messages().get(0).role() == CanonicalMessageRole.USER
                                && "继续处理这个工具结果".equals(request.messages().get(0).parts().get(0).text())
                                && request.messages().get(1).role() == CanonicalMessageRole.TOOL
                                && request.messages().get(1).parts().stream().anyMatch(part ->
                                        part.type() == CanonicalPartType.TOOL_RESULT
                                                && "call_1".equals(part.toolCallId())
                                                && "{\"city\":\"Shanghai\"}".equals(part.text()))
                )))
                .thenReturn(gatewayResponse("req-responses-mixed-tool-output-1", "继续生成完成", GatewayUsage.empty(), List.of(), null, GatewayFinishReason.STOP));

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
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>argThat(request ->
                        request != null
                                && request.messages().size() == 1
                                && request.messages().get(0).role() == CanonicalMessageRole.USER
                                && request.messages().get(0).parts().stream().anyMatch(part ->
                                        part.type() == CanonicalPartType.IMAGE
                                                && "gateway://file-123".equals(part.uri())
                                                && "image/png".equals(part.mimeType()))
                )))
                .thenReturn(gatewayResponse("req-responses-image-fileid-1", "图片已接收", GatewayUsage.empty(), List.of(), null, GatewayFinishReason.STOP));

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
    void shouldPassThroughNativeRawResponsesSseWhenRuntimeProvidesRawEvents() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayStream(Mockito.<CanonicalRequest>argThat(request ->
                        request != null
                                && request.ingressProtocol() == CanonicalIngressProtocol.RESPONSES
                                && "/v1/responses".equals(request.requestPath())
                                && "writer-fast".equals(request.requestedModel())
                )))
                .thenReturn(new CanonicalExecutionStreamResult(
                        "req-responses-native-raw-stream-1",
                        selectionResult(),
                        plan(),
                        Flux.just(
                                CanonicalStreamEvent.rawSse("event: response.created\n"),
                                CanonicalStreamEvent.rawSse("data: {\"type\":\"response.created\",\"sequence_number\":42,\"x_raw\":true}\n"),
                                CanonicalStreamEvent.rawSse("\n"),
                                CanonicalStreamEvent.rawSse("event: response.output_text.delta\n"),
                                CanonicalStreamEvent.rawSse("data: {\"type\":\"response.output_text.delta\",\"sequence_number\":43,\"delta\":\"hi\",\"obfuscation\":\"raw-obf\",\"unknown\":{\"kept\":true}}\n"),
                                CanonicalStreamEvent.rawSse("\n")
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
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("\"sequence_number\":42"));
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("\"obfuscation\":\"raw-obf\""));
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("\"unknown\":{\"kept\":true}"));
        org.junit.jupiter.api.Assertions.assertFalse(joined.contains("\"sequence_number\":0"));
        org.junit.jupiter.api.Assertions.assertFalse(joined.contains("response.output_item.added"));
    }

    @Test
    void shouldStreamResponsesEvents() throws Exception {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayStream(Mockito.<CanonicalRequest>argThat(request ->
                        request != null
                                && request.ingressProtocol() == CanonicalIngressProtocol.RESPONSES
                                && "/v1/responses".equals(request.requestPath())
                                && "writer-fast".equals(request.requestedModel())
                )))
                .thenReturn(new CanonicalExecutionStreamResult(
                        "req-responses-stream-1",
                        selectionResult(),
                        plan(),
                        Flux.just(
                                textEvent("hello"),
                                completedEvent(GatewayFinishReason.STOP, "hello", null, new GatewayUsage(100, 40, 20, 5, 60, 0, 20, 0, null, 160, null))
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
        List<JsonNode> events = dataEvents(body);
        for (int index = 0; index < events.size(); index++) {
            org.junit.jupiter.api.Assertions.assertEquals(index, events.get(index).path("sequence_number").asInt());
        }
        assertObfuscationEnabled(firstEvent(events, "response.output_text.delta"));
    }

    @Test
    void shouldDisableResponsesStreamObfuscationWhenRequestedFalse() throws Exception {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayStream(Mockito.<CanonicalRequest>argThat(request ->
                        request != null
                                && request.ingressProtocol() == CanonicalIngressProtocol.RESPONSES
                                && "/v1/responses".equals(request.requestPath())
                                && "writer-fast".equals(request.requestedModel())
                )))
                .thenReturn(new CanonicalExecutionStreamResult(
                        "req-responses-stream-obfuscation-off-1",
                        selectionResult(),
                        plan(),
                        Flux.just(
                                textEvent("hello"),
                                completedEvent(GatewayFinishReason.STOP, "hello", null, GatewayUsage.empty())
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
                          "stream":true,
                          "stream_options":{"include_obfuscation":false}
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(String.class);

        var body = result.getResponseBody().collectList().block();
        assert body != null;
        JsonNode deltaEvent = firstEvent(dataEvents(body), "response.output_text.delta");
        org.junit.jupiter.api.Assertions.assertNull(deltaEvent.get("obfuscation"));
    }

    @Test
    void shouldStreamResponsesReasoningEvents() throws Exception {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayStream(Mockito.<CanonicalRequest>argThat(request ->
                        request != null
                                && request.ingressProtocol() == CanonicalIngressProtocol.RESPONSES
                                && "/v1/responses".equals(request.requestPath())
                )))
                .thenReturn(new CanonicalExecutionStreamResult(
                        "req-responses-stream-reasoning-1",
                        selectionResult(),
                        plan(),
                        Flux.just(
                                reasoningEvent("step 1"),
                                textEvent("hello"),
                                completedEvent(GatewayFinishReason.STOP, "hello", "step 1", GatewayUsage.empty())
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
        assertObfuscationEnabled(firstEvent(dataEvents(body), "response.reasoning_summary_text.delta"));
    }

    @Test
    void shouldStreamResponsesFunctionCallEvents() throws Exception {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayStream(Mockito.<CanonicalRequest>argThat(request ->
                        request != null
                                && request.ingressProtocol() == CanonicalIngressProtocol.RESPONSES
                                && request.tools().size() == 1
                                && "lookup_weather".equals(request.tools().get(0).name())
                )))
                .thenReturn(new CanonicalExecutionStreamResult(
                        "req-responses-stream-tool-1",
                        selectionResult(),
                        plan(),
                        Flux.just(
                                toolCallEvent(List.of(new GatewayToolCall(
                                        "call_1",
                                        "function",
                                        "lookup_weather",
                                        "{\"city\":\"Shanghai\"}"
                                ))),
                                completedEvent(GatewayFinishReason.TOOL_CALLS, "", null, GatewayUsage.empty())
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
        assertObfuscationEnabled(firstEvent(dataEvents(body), "response.function_call_arguments.delta"));
    }

    @Test
    void shouldGetStoredResponse() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayAsyncResourceService.getResponse("resp_stored_1", 1L, List.of("reasoning.encrypted_content")))
                .thenReturn(new ObjectMapper().createObjectNode()
                        .put("id", "resp_stored_1")
                        .put("object", "response")
                        .put("status", "completed"));

        webTestClient.get()
                .uri("/v1/responses/resp_stored_1?include=reasoning.encrypted_content")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("resp_stored_1")
                .jsonPath("$.status").isEqualTo("completed");
    }

    @Test
    void shouldNotPassthroughUntrackedRemoteResponseWithoutRouteHint() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayAsyncResourceService.getResponse("resp_remote_1", 1L, null))
                .thenThrow(new IllegalArgumentException("未找到指定的异步资源对象。"));

        webTestClient.get()
                .uri("/v1/responses/resp_remote_1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.type").isEqualTo("invalid_request_error")
                .jsonPath("$.error.message").isEqualTo("未找到指定的异步资源对象。");

        Mockito.verifyNoMoreInteractions(gatewayOpenAiPassthroughService);
    }

    @Test
    void shouldRetrieveUntrackedRemoteResponseWithModelRouteHint() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayAsyncResourceService.getResponse(
                        "resp_remote_1",
                        1L,
                        List.of("reasoning.encrypted_content")))
                .thenThrow(new IllegalArgumentException("未找到指定的异步资源对象。"));
        Mockito.when(gatewayOpenAiPassthroughService.executeOpenAiDirectLifecycleJson(
                        "sk-gw-test",
                        "GET",
                        "/v1/responses/resp_remote_1?include=reasoning.encrypted_content",
                        "gpt-4o-mini"))
                .thenReturn(ResponseEntity.ok(objectMapper.createObjectNode()
                        .put("id", "resp_remote_1")
                        .put("object", "response")
                        .put("status", "completed")));

        webTestClient.get()
                .uri("/v1/responses/resp_remote_1?model=gpt-4o-mini&include=reasoning.encrypted_content")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("resp_remote_1")
                .jsonPath("$.status").isEqualTo("completed");
    }

    @Test
    void shouldDeleteStoredResponse() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayAsyncResourceService.deleteResponse("resp_stored_1", 1L))
                .thenReturn(new ObjectMapper().createObjectNode()
                        .put("id", "resp_stored_1")
                        .put("object", "response")
                        .put("deleted", true));

        webTestClient.delete()
                .uri("/v1/responses/resp_stored_1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("response")
                .jsonPath("$.deleted").isEqualTo(true);
    }

    @Test
    void shouldDeleteUntrackedRemoteResponseWithHeaderRouteHintAndPreserveUpstreamError() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayAsyncResourceService.deleteResponse("resp_missing_remote", 1L))
                .thenThrow(new IllegalArgumentException("未找到指定的异步资源对象。"));
        var error = objectMapper.createObjectNode();
        error.putObject("error")
                .put("type", "invalid_request_error")
                .put("code", "response_not_found")
                .put("message", "No response found");
        Mockito.when(gatewayOpenAiPassthroughService.executeOpenAiDirectLifecycleJson(
                        "sk-gw-test",
                        "DELETE",
                        "/v1/responses/resp_missing_remote",
                        "gpt-4.1-mini"))
                .thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).body(error));

        webTestClient.delete()
                .uri("/v1/responses/resp_missing_remote")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .header("X-AI-Gateway-OpenAI-Model", "gpt-4.1-mini")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("response_not_found")
                .jsonPath("$.error.message").isEqualTo("No response found");
    }

    @Test
    void shouldCancelStoredResponse() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayAsyncResourceService.cancelResponse("resp_stored_1", 1L))
                .thenReturn(new ObjectMapper().createObjectNode()
                        .put("id", "resp_stored_1")
                        .put("object", "response")
                        .put("status", "cancelled"));

        webTestClient.post()
                .uri("/v1/responses/resp_stored_1/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("response")
                .jsonPath("$.status").isEqualTo("cancelled");
    }

    @Test
    void shouldCancelUntrackedRemoteResponseWithRouteHint() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayAsyncResourceService.cancelResponse("resp_remote_background", 1L))
                .thenThrow(new IllegalArgumentException("未找到指定的异步资源对象。"));
        Mockito.when(gatewayOpenAiPassthroughService.executeOpenAiDirectLifecycleJson(
                        "sk-gw-test",
                        "POST",
                        "/v1/responses/resp_remote_background/cancel",
                        "gpt-4o-mini"))
                .thenReturn(ResponseEntity.ok(objectMapper.createObjectNode()
                        .put("id", "resp_remote_background")
                        .put("object", "response")
                        .put("status", "cancelled")));

        webTestClient.post()
                .uri("/v1/responses/resp_remote_background/cancel?model=gpt-4o-mini")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("cancelled");
    }

    @Test
    void shouldListResponseInputItems() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        var data = new ObjectMapper().createArrayNode();
        data.add(new ObjectMapper().createObjectNode()
                .put("id", "msg_resp_stored_1_0")
                .put("type", "message")
                .put("role", "user"));
        var list = new ObjectMapper().createObjectNode();
        list.put("object", "list");
        list.set("data", data);
        list.put("has_more", false);
        list.put("first_id", "msg_resp_stored_1_0");
        list.put("last_id", "msg_resp_stored_1_0");
        Mockito.when(gatewayAsyncResourceService.listResponseInputItems(
                        "resp_stored_1",
                        1L,
                        null,
                        List.of("message.input_image.image_url", "file_search_call.results"),
                        20,
                        "asc"))
                .thenReturn(list);

        webTestClient.get()
                .uri("/v1/responses/resp_stored_1/input_items?limit=20&order=asc&include=message.input_image.image_url&include=file_search_call.results")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("list")
                .jsonPath("$.data[0].id").isEqualTo("msg_resp_stored_1_0")
                .jsonPath("$.has_more").isEqualTo(false);
    }

    @Test
    void shouldListUntrackedRemoteResponseInputItemsWithRouteHintAndQuery() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayAsyncResourceService.listResponseInputItems(
                        "resp_remote_2",
                        1L,
                        "msg_1",
                        List.of("file_search_call.results", "message.input_image.image_url"),
                        10,
                        "desc"))
                .thenThrow(new IllegalArgumentException("未找到指定的异步资源对象。"));
        var data = objectMapper.createArrayNode();
        data.add(objectMapper.createObjectNode().put("id", "msg_2").put("type", "message"));
        var list = objectMapper.createObjectNode();
        list.put("object", "list");
        list.set("data", data);
        list.put("has_more", false);
        Mockito.when(gatewayOpenAiPassthroughService.executeOpenAiDirectLifecycleJson(
                        "sk-gw-test",
                        "GET",
                        "/v1/responses/resp_remote_2/input_items?include=file_search_call.results&include=message.input_image.image_url&after=msg_1&limit=10&order=desc",
                        "gpt-4o-mini"))
                .thenReturn(ResponseEntity.ok(list));

        webTestClient.get()
                .uri("/v1/responses/resp_remote_2/input_items?model=gpt-4o-mini&include=file_search_call.results&include=message.input_image.image_url&after=msg_1&limit=10&order=desc")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].id").isEqualTo("msg_2")
                .jsonPath("$.has_more").isEqualTo(false);
    }

    @Test
    void shouldCountResponseInputTokensWithStableLocalEstimate() throws Exception {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));

        JsonNode shortResult = readJson(webTestClient.post()
                .uri("/v1/responses/input_tokens")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"writer-fast",
                          "input":"hello"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody());
        JsonNode longResult = readJson(webTestClient.post()
                .uri("/v1/responses/input_tokens")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"writer-fast",
                          "instructions":"You are concise.",
                          "input":"hello, please write a longer answer with extra context"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody());

        org.junit.jupiter.api.Assertions.assertEquals("response.input_tokens", shortResult.path("object").asText());
        org.junit.jupiter.api.Assertions.assertTrue(shortResult.path("input_tokens").asInt() > 0);
        org.junit.jupiter.api.Assertions.assertTrue(longResult.path("input_tokens").asInt() >= shortResult.path("input_tokens").asInt());
    }

    @Test
    void shouldUseNativeInputTokensWhenOpenAiDirectPassthroughIsAvailable() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayOpenAiPassthroughService.executeOpenAiDirectJson(
                        Mockito.eq("sk-gw-test"),
                        Mockito.eq("/v1/responses/input_tokens"),
                        Mockito.any(tools.jackson.databind.JsonNode.class),
                        Mockito.<String>nullable(String.class)
                ))
                .thenReturn(ResponseEntity.ok(new ObjectMapper().createObjectNode()
                        .put("object", "response.input_tokens")
                        .put("input_tokens", 17)));

        webTestClient.post()
                .uri("/v1/responses/input_tokens")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"writer-fast",
                          "input":"hello"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("response.input_tokens")
                .jsonPath("$.input_tokens").isEqualTo(17);
    }

    @Test
    void shouldPreserveNativeInputTokensUpstreamErrorStatus() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        var error = new ObjectMapper().createObjectNode();
        error.putObject("error")
                .put("type", "invalid_request_error")
                .put("code", "invalid_model")
                .put("message", "Unknown model");
        Mockito.when(gatewayOpenAiPassthroughService.executeOpenAiDirectJson(
                        Mockito.eq("sk-gw-test"),
                        Mockito.eq("/v1/responses/input_tokens"),
                        Mockito.any(tools.jackson.databind.JsonNode.class),
                        Mockito.<String>nullable(String.class)
                ))
                .thenReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error));

        webTestClient.post()
                .uri("/v1/responses/input_tokens")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"missing-model",
                          "input":"hello"
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("invalid_model")
                .jsonPath("$.error.message").isEqualTo("Unknown model");
    }

    @Test
    void shouldUseNativeCompactWhenOpenAiDirectPassthroughIsAvailable() throws Exception {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayOpenAiPassthroughService.executeOpenAiDirectJson(
                        Mockito.eq("sk-gw-test"),
                        Mockito.eq("/v1/responses/compact"),
                        Mockito.any(tools.jackson.databind.JsonNode.class),
                        Mockito.<String>nullable(String.class)
                ))
                .thenReturn(ResponseEntity.ok(objectMapper.readTree("""
                        {
                          "id":"resp_compact_native_1",
                          "object":"response.compaction",
                          "created_at":1764967971,
                          "output":[
                            {
                              "id":"cmp_native_1",
                              "type":"compaction",
                              "encrypted_content":"gAAAAABnative"
                            }
                          ],
                          "usage":{"input_tokens":139,"output_tokens":438,"total_tokens":577}
                        }
                        """)));

        webTestClient.post()
                .uri("/v1/responses/compact")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"writer-fast",
                          "input":"hello compact"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("resp_compact_native_1")
                .jsonPath("$.object").isEqualTo("response.compaction")
                .jsonPath("$.output[0].type").isEqualTo("compaction")
                .jsonPath("$.output[0].encrypted_content").isEqualTo("gAAAAABnative")
                .jsonPath("$.usage.total_tokens").isEqualTo(577);
    }

    @Test
    void shouldPreserveNativeCompactUpstreamErrorStatus() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        var error = new ObjectMapper().createObjectNode();
        error.putObject("error")
                .put("type", "invalid_request_error")
                .put("code", "invalid_model")
                .put("message", "Unknown model");
        Mockito.when(gatewayOpenAiPassthroughService.executeOpenAiDirectJson(
                        Mockito.eq("sk-gw-test"),
                        Mockito.eq("/v1/responses/compact"),
                        Mockito.any(tools.jackson.databind.JsonNode.class),
                        Mockito.<String>nullable(String.class)
                ))
                .thenReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error));

        webTestClient.post()
                .uri("/v1/responses/compact")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"missing-model",
                          "input":"hello"
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("invalid_model")
                .jsonPath("$.error.message").isEqualTo("Unknown model");
    }

    @Test
    void shouldCompactResponsesWithLocalCompactionShape() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));

        webTestClient.post()
                .uri("/v1/responses/compact")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"writer-fast",
                          "input":[
                            {
                              "role":"user",
                              "content":"Summarize our launch checklist."
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("response.compaction")
                .jsonPath("$.id").exists()
                .jsonPath("$.created_at").exists()
                .jsonPath("$.output[0].role").isEqualTo("user")
                .jsonPath("$.output[1].type").isEqualTo("compaction")
                .jsonPath("$.output[1].encrypted_content").exists()
                .jsonPath("$.usage.input_tokens").isNumber()
                .jsonPath("$.usage.total_tokens").isNumber();
    }

    @Test
    void shouldRejectFunctionCallOutputWithoutCallId() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));

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
                              "output":"Shanghai is sunny"
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("invalid_argument")
                .jsonPath("$.error.type").isEqualTo("invalid_request_error")
                .jsonPath("$.error.message").isEqualTo("function_call_output 缺少 call_id。");
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

    private CanonicalExecutionResult gatewayResponse(
            String requestId,
            String text,
            GatewayUsage usage,
            List<GatewayToolCall> toolCalls,
            String reasoning,
            GatewayFinishReason finishReason) {
        return new CanonicalExecutionResult(
                requestId,
                selectionResult(),
                plan(),
                new CanonicalResponse(
                        requestId,
                        selectionResult().publicModel(),
                        text,
                        reasoning,
                        toCanonicalToolCalls(toolCalls),
                        toCanonicalUsage(usage),
                        finishReason
                )
        );
    }

    private CanonicalExecutionResult gatewayResponseRaw(String requestId, String text, JsonNode rawResponse) {
        return new CanonicalExecutionResult(
                requestId,
                selectionResult(),
                plan(),
                new CanonicalResponse(
                        requestId,
                        selectionResult().publicModel(),
                        text,
                        null,
                        List.of(),
                        CanonicalUsage.empty(),
                        GatewayFinishReason.STOP,
                        rawResponse
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

    private CanonicalStreamEvent reasoningEvent(String delta) {
        return new CanonicalStreamEvent(
                CanonicalStreamEventType.REASONING_DELTA,
                null,
                delta,
                List.of(),
                CanonicalUsage.empty(),
                false,
                null,
                null,
                null
        );
    }

    private CanonicalStreamEvent toolCallEvent(List<GatewayToolCall> toolCalls) {
        return new CanonicalStreamEvent(
                CanonicalStreamEventType.TOOL_CALLS,
                null,
                null,
                toCanonicalToolCalls(toolCalls),
                CanonicalUsage.empty(),
                false,
                null,
                null,
                null
        );
    }

    private CanonicalStreamEvent completedEvent(
            GatewayFinishReason finishReason,
            String outputText,
            String reasoning,
            GatewayUsage usage) {
        return new CanonicalStreamEvent(
                CanonicalStreamEventType.COMPLETED,
                null,
                null,
                List.of(),
                toCanonicalUsage(usage),
                true,
                finishReason,
                outputText,
                reasoning
        );
    }

    private CanonicalExecutionPlan plan() {
        return new CanonicalExecutionPlan(
                true,
                CanonicalIngressProtocol.RESPONSES,
                "/v1/responses",
                "writer-fast",
                "gpt-4o",
                "gpt-4o",
                TranslationResourceType.RESPONSE,
                TranslationOperation.RESPONSE_CREATE,
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

    private List<JsonNode> dataEvents(List<String> body) throws Exception {
        java.util.ArrayList<JsonNode> events = new java.util.ArrayList<>();
        for (String item : body) {
            for (String line : item.split("\\R")) {
                if (line.startsWith("data: ")) {
                    events.add(objectMapper.readTree(line.substring("data: ".length())));
                }
            }
        }
        return events;
    }

    private JsonNode readJson(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private JsonNode fileSearchPage() {
        var page = objectMapper.createObjectNode();
        page.put("object", "vector_store.search_results.page");
        page.putArray("data")
                .addObject()
                .put("file_id", "file_finance")
                .put("filename", "finance.txt")
                .put("score", 1.0d)
                .putArray("content")
                .addObject()
                .put("type", "text")
                .put("text", "The refund policy allows quarterly credits.");
        page.put("has_more", false);
        page.putNull("next_page");
        return page;
    }

    private JsonNode firstEvent(List<JsonNode> events, String type) {
        return events.stream()
                .filter(event -> type.equals(event.path("type").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到 SSE data event: " + type));
    }

    private void assertObfuscationEnabled(JsonNode event) {
        JsonNode obfuscation = event.get("obfuscation");
        org.junit.jupiter.api.Assertions.assertNotNull(obfuscation);
        org.junit.jupiter.api.Assertions.assertFalse(obfuscation.asText("").isBlank());
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
