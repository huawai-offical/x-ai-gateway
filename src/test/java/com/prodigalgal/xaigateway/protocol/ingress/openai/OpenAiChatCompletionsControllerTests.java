package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionService;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamilyResolver;
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
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason;
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
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@WebFluxTest(controllers = OpenAiChatCompletionsController.class)
@Import({PermitAllSecurityTestConfig.class, OpenAiChatCompletionRequestMapper.class, GatewayClientFamilyResolver.class})
class OpenAiChatCompletionsControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private DistributedKeyAuthenticationService distributedKeyAuthenticationService;

    @MockitoBean
    private GatewayChatExecutionService gatewayChatExecutionService;

    @MockitoBean
    private GatewayAsyncResourceService gatewayAsyncResourceService;

    @MockitoBean
    private OpenAiIdempotencyReplayService openAiIdempotencyReplayService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUpIdempotencyReplayService() {
        Mockito.when(openAiIdempotencyReplayService.replay(
                        Mockito.anyLong(),
                        Mockito.anyString(),
                        Mockito.<String>nullable(String.class),
                        Mockito.any(JsonNode.class)
                ))
                .thenReturn(Optional.empty());
        Mockito.when(openAiIdempotencyReplayService.remember(
                        Mockito.anyLong(),
                        Mockito.anyString(),
                        Mockito.<String>nullable(String.class),
                        Mockito.any(JsonNode.class),
                        Mockito.any(JsonNode.class)
                ))
                .thenAnswer(invocation -> invocation.getArgument(4));
    }

    @Test
    void shouldExecuteMinimalOpenAiCompletion() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>any()))
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
    void shouldPreserveOfficialOpenAiHeadersAsCanonicalMetadata() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>argThat(request -> {
            var metadata = request == null ? null : request.metadata();
            return metadata != null
                    && "GENERIC_OPENAI".equals(metadata.clientFamily())
                    && "org-test-1".equals(metadata.openAiOrganization())
                    && "proj-test-1".equals(metadata.openAiProject())
                    && "idem-test-1".equals(metadata.idempotencyKey())
                    && metadata.userAgent().contains("openai-java");
        }))).thenReturn(gatewayResponse(
                "req-openai-headers-1",
                "headers ok",
                GatewayUsage.empty(),
                List.of(),
                GatewayFinishReason.STOP
        ));

        webTestClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .header(HttpHeaders.USER_AGENT, "openai-java/2.0")
                .header("OpenAI-Organization", " org-test-1 ")
                .header("OpenAI-Project", "proj-test-1")
                .header("Idempotency-Key", "idem-test-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "gpt-4o",
                          "messages": [
                            {"role":"user","content":"hello headers"}
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.choices[0].message.content").isEqualTo("headers ok");
    }

    @Test
    void shouldReplayIdempotentChatCompletionWithoutExecutingGateway() throws Exception {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        JsonNode replayedPayload = objectMapper.readTree("""
                {
                  "id": "chatcmpl_cached",
                  "object": "chat.completion",
                  "model": "gpt-4o",
                  "choices": [
                    {
                      "index": 0,
                      "message": {"role": "assistant", "content": "cached chat"},
                      "finish_reason": "stop"
                    }
                  ]
                }
                """);
        Mockito.when(openAiIdempotencyReplayService.replay(
                        Mockito.eq(1L),
                        Mockito.eq("/v1/chat/completions"),
                        Mockito.eq("idem-chat-replay-1"),
                        Mockito.any(JsonNode.class)
                ))
                .thenReturn(Optional.of(replayedPayload));

        webTestClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .header("Idempotency-Key", "idem-chat-replay-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "gpt-4o",
                          "messages": [{"role":"user","content":"hello replay"}]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(OpenAiIdempotencyReplayService.REPLAYED_HEADER, "true")
                .expectBody()
                .jsonPath("$.id").isEqualTo("chatcmpl_cached")
                .jsonPath("$.choices[0].message.content").isEqualTo("cached chat");

        Mockito.verifyNoInteractions(gatewayChatExecutionService);
    }

    @Test
    void shouldPreserveOpenAiChatCreateParityFieldsAsProviderExtensions() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>argThat(request -> {
            var extensions = request == null ? null : request.providerExtensions();
            return extensions != null
                    && extensions.path("store").asBoolean(false)
                    && "parity".equals(extensions.path("metadata").path("purpose").asText())
                    && extensions.path("frequency_penalty").asDouble() == 0.1D
                    && extensions.path("logit_bias").path("50256").asInt() == -100
                    && extensions.path("logprobs").asBoolean(false)
                    && extensions.path("top_logprobs").asInt() == 2
                    && extensions.path("top_p").asDouble() == 0.8D
                    && extensions.path("max_completion_tokens").asInt() == 300
                    && extensions.path("n").asInt() == 2
                    && extensions.path("presence_penalty").asDouble() == 0.2D
                    && "json_schema".equals(extensions.path("response_format").path("type").asText())
                    && extensions.path("seed").asInt() == 42
                    && "flex".equals(extensions.path("service_tier").asText())
                    && "END".equals(extensions.path("stop").path(0).asText())
                    && !extensions.path("parallel_tool_calls").asBoolean(true)
                    && "user-hash-1".equals(extensions.path("user").asText())
                    && "high".equals(extensions.path("verbosity").asText())
                    && "cache-key-chat-1".equals(extensions.path("prompt_cache_key").asText())
                    && "safety-user-1".equals(extensions.path("safety_identifier").asText())
                    && "static".equals(extensions.path("prediction").path("type").asText());
        }))).thenReturn(gatewayResponse(
                "req-chat-parity-1",
                "parity ok",
                GatewayUsage.empty(),
                List.of(),
                GatewayFinishReason.STOP
        ));
        Mockito.when(gatewayAsyncResourceService.storeChatCompletion(
                Mockito.eq(1L),
                Mockito.eq("gpt-4o"),
                Mockito.any(JsonNode.class),
                Mockito.any(JsonNode.class)
        )).thenAnswer(invocation -> {
            JsonNode responsePayload = invocation.getArgument(3);
            ObjectNode stored = objectMapper.createObjectNode();
            stored.put("id", "chatcmpl_parity_1");
            stored.put("object", "chat.completion");
            stored.put("status", "completed");
            stored.put("model", "gpt-4o");
            stored.set("choices", responsePayload.path("choices").deepCopy());
            return stored;
        });

        webTestClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "gpt-4o",
                          "messages": [
                            {"role":"user","content":"hello parity"}
                          ],
                          "store": true,
                          "metadata": {"purpose":"parity"},
                          "frequency_penalty": 0.1,
                          "logit_bias": {"50256": -100},
                          "logprobs": true,
                          "top_logprobs": 2,
                          "top_p": 0.8,
                          "max_completion_tokens": 300,
                          "n": 2,
                          "presence_penalty": 0.2,
                          "response_format": {"type":"json_schema","json_schema":{"name":"answer","schema":{"type":"object"},"strict":false}},
                          "seed": 42,
                          "service_tier": "flex",
                          "stop": ["END"],
                          "parallel_tool_calls": false,
                          "user": "user-hash-1",
                          "verbosity": "high",
                          "prompt_cache_key": "cache-key-chat-1",
                          "safety_identifier": "safety-user-1",
                          "prediction": {"type":"static","content":"hello"}
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.choices[0].message.content").isEqualTo("parity ok");
    }

    @Test
    void shouldStoreOpenAiCompletionWhenStoreEnabled() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>any()))
                .thenReturn(gatewayResponse(
                        "req-openai-store-1",
                        "stored ok",
                        GatewayUsage.empty(),
                        List.of(),
                        GatewayFinishReason.STOP
                ));
        Mockito.when(gatewayAsyncResourceService.storeChatCompletion(
                Mockito.eq(1L),
                Mockito.eq("gpt-4o"),
                Mockito.any(JsonNode.class),
                Mockito.any(JsonNode.class)
        )).thenAnswer(invocation -> {
            JsonNode requestPayload = invocation.getArgument(2);
            JsonNode responsePayload = invocation.getArgument(3);
            org.junit.jupiter.api.Assertions.assertEquals("hello store", requestPayload.path("messages").path(0).path("content").asText());
            org.junit.jupiter.api.Assertions.assertEquals("chat.completion", responsePayload.path("object").asText());
            ObjectNode stored = objectMapper.createObjectNode();
            stored.put("id", "chatcmpl_stored_1");
            stored.put("object", "chat.completion");
            stored.put("status", "completed");
            stored.put("model", "gpt-4o");
            stored.set("choices", responsePayload.path("choices").deepCopy());
            return stored;
        });

        webTestClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .header("Idempotency-Key", "idem-store-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "gpt-4o",
                          "store": true,
                          "messages": [
                            {"role":"user","content":"hello store"}
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("chatcmpl_stored_1")
                .jsonPath("$.object").isEqualTo("chat.completion")
                .jsonPath("$.choices[0].message.content").isEqualTo("stored ok");
        Mockito.verify(openAiIdempotencyReplayService).remember(
                Mockito.eq(1L),
                Mockito.eq("/v1/chat/completions"),
                Mockito.eq("idem-store-1"),
                Mockito.any(JsonNode.class),
                Mockito.argThat(payload -> "chatcmpl_stored_1".equals(payload.path("id").asText()))
        );
    }

    @Test
    void shouldRejectInvalidOpenAiResponseFormatType() {
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
                            {"role":"user","content":"hello"}
                          ],
                          "response_format": {"type":"xml"}
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("invalid_argument")
                .jsonPath("$.error.type").isEqualTo("invalid_request_error")
                .jsonPath("$.error.message").isEqualTo("response_format.type 只支持 text、json_object 或 json_schema。");
    }

    @Test
    void shouldRejectInvalidOpenAiAudioVoice() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));

        webTestClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "gpt-4o-audio-preview",
                          "messages": [
                            {"role":"user","content":"hello"}
                          ],
                          "modalities": ["text", "audio"],
                          "audio": {"voice":"unknown_voice","format":"wav"}
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("invalid_argument")
                .jsonPath("$.error.type").isEqualTo("invalid_request_error")
                .jsonPath("$.error.message").isEqualTo("audio.voice 不支持值 unknown_voice。");
    }

    @Test
    void shouldExposeStoredChatCompletionLifecycleEndpoints() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));

        ObjectNode stored = storedCompletion("chatcmpl_lifecycle_1", "gpt-4o");
        ObjectNode list = objectMapper.createObjectNode();
        var data = objectMapper.createArrayNode();
        data.add(stored);
        list.put("object", "list");
        list.set("data", data);
        list.put("has_more", false);
        list.put("first_id", "chatcmpl_lifecycle_1");
        list.put("last_id", "chatcmpl_lifecycle_1");
        Mockito.when(gatewayAsyncResourceService.listChatCompletions(
                Mockito.eq(1L),
                Mockito.eq("chatcmpl_before"),
                Mockito.eq(2),
                Mockito.eq("gpt-4o"),
                Mockito.eq("asc"),
                Mockito.<Map<String, String>>argThat(filter -> "qa".equals(filter.get("purpose")))
        )).thenReturn(list);
        Mockito.when(gatewayAsyncResourceService.getChatCompletion("chatcmpl_lifecycle_1", 1L))
                .thenReturn(stored);

        ObjectNode updated = storedCompletion("chatcmpl_lifecycle_1", "gpt-4o");
        updated.set("metadata", objectMapper.createObjectNode().put("purpose", "updated"));
        Mockito.when(gatewayAsyncResourceService.updateChatCompletionMetadata(
                Mockito.eq("chatcmpl_lifecycle_1"),
                Mockito.eq(1L),
                Mockito.any(JsonNode.class)
        )).thenReturn(updated);

        ObjectNode deleted = objectMapper.createObjectNode();
        deleted.put("id", "chatcmpl_lifecycle_1");
        deleted.put("object", "chat.completion.deleted");
        deleted.put("deleted", true);
        Mockito.when(gatewayAsyncResourceService.deleteChatCompletion("chatcmpl_lifecycle_1", 1L))
                .thenReturn(deleted);

        ObjectNode messages = objectMapper.createObjectNode();
        var messageData = objectMapper.createArrayNode();
        messageData.add(objectMapper.createObjectNode().put("id", "msg_1").put("role", "user").put("content", "hello"));
        messages.put("object", "list");
        messages.set("data", messageData);
        messages.put("has_more", false);
        Mockito.when(gatewayAsyncResourceService.listChatCompletionMessages(
                "chatcmpl_lifecycle_1",
                1L,
                "msg_0",
                10,
                "desc"
        )).thenReturn(messages);

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v1/chat/completions")
                        .queryParam("after", "chatcmpl_before")
                        .queryParam("limit", "2")
                        .queryParam("model", "gpt-4o")
                        .queryParam("order", "asc")
                        .queryParam("metadata[purpose]", "qa")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].id").isEqualTo("chatcmpl_lifecycle_1");

        webTestClient.get()
                .uri("/v1/chat/completions/chatcmpl_lifecycle_1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("chatcmpl_lifecycle_1");

        webTestClient.post()
                .uri("/v1/chat/completions/chatcmpl_lifecycle_1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"metadata":{"purpose":"updated"}}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.metadata.purpose").isEqualTo("updated");

        webTestClient.get()
                .uri("/v1/chat/completions/chatcmpl_lifecycle_1/messages?after=msg_0&limit=10&order=desc")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].id").isEqualTo("msg_1");

        webTestClient.delete()
                .uri("/v1/chat/completions/chatcmpl_lifecycle_1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("chat.completion.deleted")
                .jsonPath("$.deleted").isEqualTo(true);
    }

    @Test
    void shouldRejectInvalidStoredChatPaginationParameters() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));

        webTestClient.get()
                .uri("/v1/chat/completions?limit=0")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.type").isEqualTo("invalid_request_error")
                .jsonPath("$.error.code").isEqualTo("invalid_argument")
                .jsonPath("$.error.message").isEqualTo("limit 必须在 1 到 100 之间。");

        webTestClient.get()
                .uri("/v1/chat/completions?order=newest")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.type").isEqualTo("invalid_request_error")
                .jsonPath("$.error.code").isEqualTo("invalid_argument")
                .jsonPath("$.error.message").isEqualTo("order 必须是 asc 或 desc。");

        webTestClient.get()
                .uri("/v1/chat/completions/chatcmpl_lifecycle_1/messages?limit=101")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.type").isEqualTo("invalid_request_error")
                .jsonPath("$.error.code").isEqualTo("invalid_argument")
                .jsonPath("$.error.message").isEqualTo("limit 必须在 1 到 100 之间。");

        Mockito.verifyNoInteractions(gatewayAsyncResourceService);
    }

    @Test
    void shouldExecuteMinimalOpenAiStreamCompletion() throws Exception {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayStream(Mockito.<CanonicalRequest>any()))
                .thenReturn(new CanonicalExecutionStreamResult(
                        "req-openai-stream-1",
                        selectionResult(),
                        plan(),
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
        List<JsonNode> chunks = dataChunks(body);
        org.junit.jupiter.api.Assertions.assertEquals(3, chunks.size());
        org.junit.jupiter.api.Assertions.assertEquals(chunks.get(0).path("id").asText(), chunks.get(1).path("id").asText());
        org.junit.jupiter.api.Assertions.assertEquals(chunks.get(1).path("id").asText(), chunks.get(2).path("id").asText());
        org.junit.jupiter.api.Assertions.assertEquals(chunks.get(0).path("created").asLong(), chunks.get(1).path("created").asLong());
        org.junit.jupiter.api.Assertions.assertEquals(chunks.get(1).path("created").asLong(), chunks.get(2).path("created").asLong());
    }

    @Test
    void shouldStreamUsageChunkWhenIncludeUsageIsEnabled() throws Exception {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayStream(Mockito.<CanonicalRequest>any()))
                .thenReturn(new CanonicalExecutionStreamResult(
                        "req-openai-stream-usage-1",
                        selectionResult(),
                        plan(),
                        Flux.just(
                                textEvent("hello"),
                                completedEvent(GatewayFinishReason.STOP, "hello", new GatewayUsage(100, 40, 20, 5, 60, 0, 20, 0, null, 160, null))
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
                          "stream": true,
                          "stream_options": {
                            "include_usage": true
                          }
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(String.class);

        var body = result.getResponseBody().collectList().block();
        assert body != null;
        List<JsonNode> chunks = dataChunks(body);
        org.junit.jupiter.api.Assertions.assertEquals(4, chunks.size());
        JsonNode usageChunk = chunks.get(3);
        org.junit.jupiter.api.Assertions.assertEquals(0, usageChunk.path("choices").size());
        org.junit.jupiter.api.Assertions.assertEquals(160, usageChunk.path("usage").path("total_tokens").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(chunks.get(0).path("id").asText(), usageChunk.path("id").asText());
        org.junit.jupiter.api.Assertions.assertEquals(chunks.get(0).path("created").asLong(), usageChunk.path("created").asLong());
    }

    @Test
    void shouldStreamToolCallDeltas() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayStream(Mockito.<CanonicalRequest>any()))
                .thenReturn(new CanonicalExecutionStreamResult(
                        "req-openai-stream-tool-1",
                        selectionResult(),
                        plan(),
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
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>any()))
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
    void shouldConvertLegacyFunctionsAndFunctionCallIntoCanonicalToolSemantics() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>argThat(request ->
                request != null
                        && request.tools().size() == 1
                        && "legacy_lookup".equals(request.tools().getFirst().name())
                        && "Lookup from legacy function".equals(request.tools().getFirst().description())
                        && request.tools().getFirst().strict()
                        && "object".equals(request.tools().getFirst().inputSchema().path("type").asText())
                        && "function".equals(request.toolChoice().path("type").asText())
                        && "legacy_lookup".equals(request.toolChoice().path("function").path("name").asText())
                        && "legacy_lookup".equals(request.providerExtensions().path("functions").path(0).path("name").asText())
                        && "legacy_lookup".equals(request.providerExtensions().path("function_call").path("name").asText())
        ))).thenReturn(gatewayResponse(
                "req-openai-legacy-functions-1",
                "legacy function ok",
                GatewayUsage.empty(),
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
                            {"role":"user","content":"legacy tool"}
                          ],
                          "functions": [
                            {
                              "name": "legacy_lookup",
                              "description": "Lookup from legacy function",
                              "parameters": {"type":"object"},
                              "strict": true
                            }
                          ],
                          "function_call": {"name":"legacy_lookup"}
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.choices[0].message.content").isEqualTo("legacy function ok");
    }

    @Test
    void shouldAcceptOpenAiContentArrayWithImageUrl() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>any()))
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
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>any()))
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
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>any()))
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
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<CanonicalRequest>any()))
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
                .jsonPath("$.error.code").isEqualTo("invalid_argument")
                .jsonPath("$.error.type").isEqualTo("invalid_request_error")
                .jsonPath("$.error.message").isEqualTo("至少需要一条 user 消息。");
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

    private ObjectNode storedCompletion(String id, String model) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", id);
        response.put("object", "chat.completion");
        response.put("status", "completed");
        response.put("model", model);
        response.set("metadata", objectMapper.createObjectNode().put("purpose", "qa"));
        return response;
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

    private CanonicalStreamEvent completedEvent(GatewayFinishReason finishReason, String outputText, GatewayUsage usage) {
        return new CanonicalStreamEvent(
                CanonicalStreamEventType.COMPLETED,
                null,
                null,
                List.of(),
                toCanonicalUsage(usage),
                true,
                finishReason,
                outputText,
                null
        );
    }

    private CanonicalExecutionPlan plan() {
        return new CanonicalExecutionPlan(
                true,
                CanonicalIngressProtocol.OPENAI,
                "/v1/chat/completions",
                "gpt-4o",
                "gpt-4o",
                "gpt-4o",
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

    private List<JsonNode> dataChunks(List<String> body) throws Exception {
        java.util.ArrayList<JsonNode> chunks = new java.util.ArrayList<>();
        for (String item : body) {
            for (String line : item.split("\\R")) {
                if (line.startsWith("data: ") && !"data: [DONE]".equals(line)) {
                    chunks.add(objectMapper.readTree(line.substring("data: ".length())));
                }
            }
        }
        return chunks;
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
