package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalContentPart;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessageRole;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalReasoningConfig;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequestMetadata;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEventType;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolCall;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolDefinition;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialAuthKind;
import com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
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
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiNativeGatewayChatRuntimeTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAiNativeGatewayChatRuntime runtime = new OpenAiNativeGatewayChatRuntime(
            null,
            null,
            null,
            null,
            objectMapper
    );

    @Test
    void shouldMapResponsesParityFieldsIntoOpenAiCompatibleRequest() throws Exception {
        var extensions = objectMapper.readTree("""
                {
                  "service_tier":"auto",
                  "parallel_tool_calls":false,
                  "prompt_cache_key":"conv-cache-1",
                  "prompt_cache_retention":"24h",
                  "top_logprobs":3,
                  "truncation":"auto",
                  "text":{"format":{"type":"json_schema","name":"answer"}},
                  "metadata":{"tenant":"community"}
                }
                """);

        var request = new CanonicalRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.RESPONSES,
                "/v1/responses",
                "grok-4.3",
                List.of(new CanonicalMessage(CanonicalMessageRole.USER, List.of(CanonicalContentPart.text("hello")))),
                List.of(),
                null,
                0.2,
                256,
                new CanonicalReasoningConfig(objectMapper.readTree("{\"effort\":\"low\"}"), "low"),
                extensions,
                new CanonicalRequestMetadata("GENERIC_OPENAI", null, null, "session_id", "session-fp-1", null, null, null)
        );

        var mapped = runtime.buildRequest(request, "grok-4.3", false, ProviderType.OPENAI_COMPATIBLE);

        assertEquals("auto", mapped.serviceTier());
        assertEquals(Boolean.FALSE, mapped.parallelToolCalls());
        assertEquals("conv-cache-1", mapped.promptCacheKey());
        assertEquals(3, mapped.topLogprobs());
        assertEquals(Boolean.TRUE, mapped.logprobs());
        assertEquals("community", mapped.metadata().get("tenant"));
        assertEquals("auto", mapped.extraBody().get("truncation"));
        assertTrue(mapped.extraBody().containsKey("text"));
        assertEquals("24h", mapped.extraBody().get("prompt_cache_retention"));
    }

    @Test
    void shouldUsePromptCacheKeyAsXaiConversationHeader() throws Exception {
        var request = new CanonicalRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.RESPONSES,
                "/v1/responses",
                "grok-4.3",
                List.of(new CanonicalMessage(CanonicalMessageRole.USER, List.of(CanonicalContentPart.text("hello")))),
                List.of(),
                null,
                null,
                null,
                null,
                objectMapper.readTree("{\"prompt_cache_key\":\"grok-conv-1\"}")
        );

        assertEquals("grok-conv-1", runtime.upstreamHeaders(UpstreamSiteKind.GROK, request).get("x-grok-conv-id"));
        assertFalse(runtime.upstreamHeaders(UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC, request).containsKey("x-grok-conv-id"));
    }

    @Test
    void shouldForwardOfficialOpenAiHeadersOnlyForOpenAiDirect() throws Exception {
        var request = new CanonicalRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.RESPONSES,
                "/v1/responses",
                "gpt-4o",
                List.of(new CanonicalMessage(CanonicalMessageRole.USER, List.of(CanonicalContentPart.text("hello")))),
                List.of(),
                null,
                null,
                null,
                null,
                objectMapper.readTree("{\"prompt_cache_key\":\"grok-conv-1\"}"),
                new CanonicalRequestMetadata(
                        "GENERIC_OPENAI",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "openai-java/2.0",
                        "org-test-1",
                        "proj-test-1",
                        "idem-test-1"
                )
        );

        var openAiHeaders = runtime.upstreamHeaders(UpstreamSiteKind.OPENAI_DIRECT, request);
        assertEquals("org-test-1", openAiHeaders.get("OpenAI-Organization"));
        assertEquals("proj-test-1", openAiHeaders.get("OpenAI-Project"));
        assertEquals("idem-test-1", openAiHeaders.get("Idempotency-Key"));

        var compatibleHeaders = runtime.upstreamHeaders(UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC, request);
        assertFalse(compatibleHeaders.containsKey("OpenAI-Organization"));
        assertFalse(compatibleHeaders.containsKey("OpenAI-Project"));
        assertFalse(compatibleHeaders.containsKey("Idempotency-Key"));

        var grokHeaders = runtime.upstreamHeaders(UpstreamSiteKind.GROK, request);
        assertEquals("grok-conv-1", grokHeaders.get("x-grok-conv-id"));
        assertFalse(grokHeaders.containsKey("OpenAI-Organization"));
        assertFalse(grokHeaders.containsKey("OpenAI-Project"));
        assertFalse(grokHeaders.containsKey("Idempotency-Key"));
    }

    @Test
    void shouldExecuteOpenAiDirectResponsesThroughNativeHttpCreate() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> upstreamPath = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> organization = new AtomicReference<>();
        AtomicReference<String> project = new AtomicReference<>();
        AtomicReference<String> idempotencyKey = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/v1/responses", exchange -> {
            upstreamPath.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("authorization"));
            organization.set(exchange.getRequestHeaders().getFirst("OpenAI-Organization"));
            project.set(exchange.getRequestHeaders().getFirst("OpenAI-Project"));
            idempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendJson(exchange, 200, """
                    {
                      "id":"resp_native_1",
                      "object":"response",
                      "status":"completed",
                      "model":"gpt-4.1-mini",
                      "output":[
                        {"type":"reasoning","summary":[{"type":"summary_text","text":"native reasoning"}]},
                        {"type":"message","content":[{"type":"output_text","text":"native ok"}]},
                        {"type":"function_call","call_id":"call_native_1","name":"lookup","arguments":"{\\"q\\":\\"x\\"}"}
                      ],
                      "usage":{
                        "input_tokens":11,
                        "output_tokens":7,
                        "total_tokens":18,
                        "input_tokens_details":{"cached_tokens":3},
                        "output_tokens_details":{"reasoning_tokens":2}
                      }
                    }
                    """);
        });
        server.start();
        try {
            var extensions = objectMapper.readTree("""
                    {
                      "model":"gpt-4.1-public",
                      "input":"hello",
                      "include":["reasoning.encrypted_content"],
                      "previous_response_id":"resp_prev_1",
                      "store":false,
                      "metadata":{"purpose":"native-create"},
                      "stream":false
                    }
                    """);
            var request = new CanonicalRequest(
                    "sk-gw-test",
                    CanonicalIngressProtocol.RESPONSES,
                    "/v1/responses",
                    "gpt-4.1-public",
                    List.of(new CanonicalMessage(CanonicalMessageRole.USER, List.of(CanonicalContentPart.text("hello")))),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    extensions,
                    new CanonicalRequestMetadata(
                            "GENERIC_OPENAI",
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            "openai-java/2.0",
                            "org-native",
                            "proj-native",
                            "idem-native"
                    )
            );
            UpstreamCredentialEntity credential = new UpstreamCredentialEntity();
            credential.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            var context = new GatewayChatRuntimeContext(
                    openAiDirectSelection("gpt-4.1-public", "gpt-4.1-mini"),
                    credential,
                    new ResolvedCredentialMaterial(1L, 1L, CredentialAuthKind.API_KEY, "upstream-secret", null, Map.of(), null, "test"),
                    request,
                    null
            );

            var response = runtime.execute(context);

            assertEquals("/v1/responses", upstreamPath.get());
            assertEquals("Bearer upstream-secret", authorization.get());
            assertEquals("org-native", organization.get());
            assertEquals("proj-native", project.get());
            assertEquals("idem-native", idempotencyKey.get());
            JsonNode sent = objectMapper.readTree(requestBody.get());
            assertEquals("gpt-4.1-mini", sent.path("model").asText());
            assertEquals("resp_prev_1", sent.path("previous_response_id").asText());
            assertEquals("reasoning.encrypted_content", sent.path("include").get(0).asText());
            assertEquals(false, sent.path("stream").asBoolean());

            assertEquals("resp_native_1", response.requestId());
            assertEquals("gpt-4.1-public", response.publicModel());
            assertEquals("native ok", response.outputText());
            assertEquals("native reasoning", response.reasoning());
            assertEquals(1, response.toolCalls().size());
            assertEquals("call_native_1", response.toolCalls().getFirst().id());
            assertEquals("lookup", response.toolCalls().getFirst().name());
            assertEquals(11, response.usage().promptTokens());
            assertEquals(7, response.usage().completionTokens());
            assertEquals(18, response.usage().totalTokens());
            assertEquals(3, response.usage().cacheHitTokens());
            assertEquals(2, response.usage().reasoningTokens());
            assertNotNull(response.finishReason());
            assertEquals("response", response.rawResponse().path("object").asText());
            assertEquals("gpt-4.1-mini", response.rawResponse().path("model").asText());
            assertEquals("native ok", response.rawResponse().path("output").get(1).path("content").get(0).path("text").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldStreamOpenAiDirectResponsesThroughNativeSsePassthrough() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> upstreamPath = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> accept = new AtomicReference<>();
        AtomicReference<String> organization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/v1/responses", exchange -> {
            upstreamPath.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("authorization"));
            accept.set(exchange.getRequestHeaders().getFirst("accept"));
            organization.set(exchange.getRequestHeaders().getFirst("OpenAI-Organization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendSse(exchange, """
                    event: response.created
                    data: {"type":"response.created","sequence_number":42,"model":"gpt-4.1-mini","x_raw":true}

                    event: response.output_text.delta
                    data: {"type":"response.output_text.delta","sequence_number":43,"delta":"hi","obfuscation":"raw-obf","unknown":{"kept":true}}

                    event: response.completed
                    data: {"type":"response.completed","sequence_number":44,"response":{"id":"resp_stream_1"}}

                    """);
        });
        server.start();
        try {
            var extensions = objectMapper.readTree("""
                    {
                      "model":"gpt-4.1-public",
                      "input":"hello",
                      "stream":true,
                      "stream_options":{"include_obfuscation":true}
                    }
                    """);
            var request = new CanonicalRequest(
                    "sk-gw-test",
                    CanonicalIngressProtocol.RESPONSES,
                    "/v1/responses",
                    "gpt-4.1-public",
                    List.of(new CanonicalMessage(CanonicalMessageRole.USER, List.of(CanonicalContentPart.text("hello")))),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    extensions,
                    new CanonicalRequestMetadata(
                            "GENERIC_OPENAI",
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            "openai-java/2.0",
                            "org-native-stream",
                            null,
                            null
                    )
            );
            UpstreamCredentialEntity credential = new UpstreamCredentialEntity();
            credential.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            var context = new GatewayChatRuntimeContext(
                    openAiDirectSelection("gpt-4.1-public", "gpt-4.1-mini"),
                    credential,
                    new ResolvedCredentialMaterial(1L, 1L, CredentialAuthKind.API_KEY, "upstream-secret", null, Map.of(), null, "test"),
                    request,
                    null
            );

            var events = runtime.executeStream(context).collectList().block();
            assertNotNull(events);
            String joined = String.join("", events.stream().map(event -> event.rawSsePayload() == null ? "" : event.rawSsePayload()).toList());

            assertEquals("/v1/responses", upstreamPath.get());
            assertEquals("Bearer upstream-secret", authorization.get());
            assertEquals("text/event-stream", accept.get());
            assertEquals("org-native-stream", organization.get());
            JsonNode sent = objectMapper.readTree(requestBody.get());
            assertEquals("gpt-4.1-mini", sent.path("model").asText());
            assertTrue(sent.path("stream").asBoolean());
            assertTrue(events.stream().allMatch(event -> event.type() == CanonicalStreamEventType.RAW_SSE));
            assertTrue(joined.contains("\"sequence_number\":42"));
            assertTrue(joined.contains("\"obfuscation\":\"raw-obf\""));
            assertTrue(joined.contains("\"unknown\":{\"kept\":true}"));
            assertTrue(joined.contains("event: response.completed\n"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapChatCreateParityFieldsIntoNativeOpenAiRequest() throws Exception {
        var extensions = objectMapper.readTree("""
                {
                  "store": true,
                  "metadata": {"purpose":"parity"},
                  "frequency_penalty": 0.1,
                  "logit_bias": {"50256": -100},
                  "logprobs": true,
                  "top_logprobs": 2,
                  "max_completion_tokens": 300,
                  "n": 2,
                  "presence_penalty": 0.2,
                  "seed": 42,
                  "service_tier": "flex",
                  "stop": ["END"],
                  "top_p": 0.8,
                  "parallel_tool_calls": false,
                  "user": "user-hash-1",
                  "verbosity": "high",
                  "prompt_cache_key": "cache-key-chat-1",
                  "safety_identifier": "safety-user-1",
                  "response_format": {"type":"json_schema","json_schema":{"name":"answer","schema":{"type":"object"},"strict":false}},
                  "prediction": {"type":"static","content":"hello"}
                }
                """);
        var request = new CanonicalRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.OPENAI,
                "/v1/chat/completions",
                "gpt-4o",
                List.of(new CanonicalMessage(CanonicalMessageRole.USER, List.of(CanonicalContentPart.text("hello")))),
                List.of(),
                null,
                0.2,
                256,
                null,
                extensions
        );

        var mapped = runtime.buildRequest(request, "gpt-4o", false, ProviderType.OPENAI_DIRECT);

        assertEquals(Boolean.TRUE, mapped.store());
        assertEquals("parity", mapped.metadata().get("purpose"));
        assertEquals(0.1D, mapped.frequencyPenalty());
        assertEquals(-100, mapped.logitBias().get("50256"));
        assertEquals(Boolean.TRUE, mapped.logprobs());
        assertEquals(2, mapped.topLogprobs());
        assertEquals(300, mapped.maxCompletionTokens());
        assertEquals(2, mapped.n());
        assertEquals(0.2D, mapped.presencePenalty());
        assertEquals(42, mapped.seed());
        assertEquals("flex", mapped.serviceTier());
        assertEquals(List.of("END"), mapped.stop());
        assertEquals(0.8D, mapped.topP());
        assertEquals(Boolean.FALSE, mapped.parallelToolCalls());
        assertEquals("user-hash-1", mapped.user());
        assertEquals("high", mapped.verbosity());
        assertEquals("cache-key-chat-1", mapped.promptCacheKey());
        assertEquals("safety-user-1", mapped.safetyIdentifier());
        assertEquals(ResponseFormat.Type.JSON_SCHEMA, mapped.responseFormat().getType());
        assertEquals("answer", mapped.responseFormat().getJsonSchema().getName());
        assertEquals("object", mapped.responseFormat().getJsonSchema().getSchema().get("type"));
        assertEquals(Boolean.FALSE, mapped.responseFormat().getJsonSchema().getStrict());
        assertFalse(mapped.extraBody().containsKey("response_format"));
        assertTrue(mapped.extraBody().containsKey("prediction"));
    }

    @Test
    void shouldMapJsonObjectResponseFormatIntoNativeOpenAiRequest() throws Exception {
        var request = new CanonicalRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.OPENAI,
                "/v1/chat/completions",
                "gpt-4o",
                List.of(new CanonicalMessage(CanonicalMessageRole.USER, List.of(CanonicalContentPart.text("hello")))),
                List.of(),
                null,
                null,
                null,
                null,
                objectMapper.readTree("{\"response_format\":{\"type\":\"json_object\"}}")
        );

        var mapped = runtime.buildRequest(request, "gpt-4o", false, ProviderType.OPENAI_DIRECT);

        assertEquals(ResponseFormat.Type.JSON_OBJECT, mapped.responseFormat().getType());
        assertFalse(mapped.extraBody().containsKey("response_format"));
    }

    @Test
    void shouldMapModalitiesAudioAndWebSearchOptionsIntoNativeOpenAiRequest() throws Exception {
        var extensions = objectMapper.readTree("""
                {
                  "modalities": ["text", "audio"],
                  "audio": {"voice": "nova", "format": "wav"},
                  "web_search_options": {
                    "search_context_size": "high",
                    "user_location": {
                      "type": "approximate",
                      "approximate": {
                        "city": "Shanghai",
                        "country": "CN",
                        "region": "Shanghai",
                        "timezone": "Asia/Shanghai"
                      }
                    }
                  }
                }
                """);
        var request = new CanonicalRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.OPENAI,
                "/v1/chat/completions",
                "gpt-4o-audio-preview",
                List.of(new CanonicalMessage(CanonicalMessageRole.USER, List.of(CanonicalContentPart.text("hello")))),
                List.of(),
                null,
                null,
                null,
                null,
                extensions
        );

        var mapped = runtime.buildRequest(request, "gpt-4o-audio-preview", false, ProviderType.OPENAI_DIRECT);

        assertEquals(List.of(OpenAiApi.OutputModality.TEXT, OpenAiApi.OutputModality.AUDIO), mapped.outputModalities());
        assertEquals(OpenAiApi.ChatCompletionRequest.AudioParameters.Voice.NOVA, mapped.audioParameters().voice());
        assertEquals(OpenAiApi.ChatCompletionRequest.AudioParameters.AudioResponseFormat.WAV, mapped.audioParameters().format());
        assertEquals(OpenAiApi.ChatCompletionRequest.WebSearchOptions.SearchContextSize.HIGH, mapped.webSearchOptions().searchContextSize());
        assertEquals("Shanghai", mapped.webSearchOptions().userLocation().approximate().city());
        assertEquals("CN", mapped.webSearchOptions().userLocation().approximate().country());
        assertEquals("Asia/Shanghai", mapped.webSearchOptions().userLocation().approximate().timezone());
        assertFalse(mapped.extraBody().containsKey("modalities"));
        assertFalse(mapped.extraBody().containsKey("audio"));
        assertFalse(mapped.extraBody().containsKey("web_search_options"));
    }

    @Test
    void shouldConvertLegacyFunctionsWithoutLeakingRawFieldsToOpenAiDirect() throws Exception {
        var extensions = objectMapper.readTree("""
                {
                  "functions": [
                    {
                      "name": "legacy_lookup",
                      "description": "Lookup from legacy function",
                      "parameters": {"type":"object"}
                    }
                  ],
                  "function_call": {"name":"legacy_lookup"}
                }
                """);
        var toolChoice = objectMapper.readTree("""
                {"type":"function","function":{"name":"legacy_lookup"}}
                """);
        var request = new CanonicalRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.OPENAI,
                "/v1/chat/completions",
                "gpt-4o",
                List.of(new CanonicalMessage(CanonicalMessageRole.USER, List.of(CanonicalContentPart.text("hello")))),
                List.of(new CanonicalToolDefinition(
                        "legacy_lookup",
                        "Lookup from legacy function",
                        objectMapper.readTree("{\"type\":\"object\"}"),
                        null
                )),
                toolChoice,
                null,
                null,
                null,
                extensions
        );

        var direct = runtime.buildRequest(request, "gpt-4o", false, ProviderType.OPENAI_DIRECT);
        var compatible = runtime.buildRequest(request, "gpt-4o", false, ProviderType.OPENAI_COMPATIBLE);

        assertEquals("legacy_lookup", direct.tools().getFirst().getFunction().getName());
        assertEquals(toolChoice, direct.toolChoice());
        assertFalse(direct.extraBody().containsKey("functions"));
        assertFalse(direct.extraBody().containsKey("function_call"));

        assertEquals("legacy_lookup", compatible.tools().getFirst().getFunction().getName());
        assertTrue(compatible.extraBody().containsKey("functions"));
        assertTrue(compatible.extraBody().containsKey("function_call"));
    }

    @Test
    void shouldTranslateResponsesConversationToOpenAiCompatibleChatWithReasoningHistory() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> upstreamPath = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            upstreamPath.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendJson(exchange, 200, """
                    {
                      "id":"chatcmpl_mimo_1",
                      "object":"chat.completion",
                      "model":"mimo-v2.5-pro",
                      "choices":[
                        {
                          "index":0,
                          "finish_reason":"stop",
                          "message":{
                            "role":"assistant",
                            "content":"上海比北京低 3 度。",
                            "reasoning_content":"我复用了上一轮工具结果，再比较上海天气。",
                            "tool_calls":[]
                          }
                        }
                      ],
                      "usage":{
                        "prompt_tokens":31,
                        "completion_tokens":9,
                        "total_tokens":40,
                        "completion_tokens_details":{"reasoning_tokens":4}
                      }
                    }
                    """);
        });
        server.start();
        try {
            var request = new CanonicalRequest(
                    "sk-gw-test",
                    CanonicalIngressProtocol.RESPONSES,
                    "/v1/responses",
                    "mimo-public",
                    List.of(
                            new CanonicalMessage(CanonicalMessageRole.USER, List.of(CanonicalContentPart.text("北京天气怎么样？"))),
                            new CanonicalMessage(
                                    CanonicalMessageRole.ASSISTANT,
                                    List.of(),
                                    "我需要调用天气工具查询北京。",
                                    List.of(new CanonicalToolCall("call_mimo_1", "function", "get_weather", "{\"city\":\"北京\"}")),
                                    null
                            ),
                            new CanonicalMessage(
                                    CanonicalMessageRole.TOOL,
                                    List.of(CanonicalContentPart.toolResult("call_mimo_1", "get_weather", "北京晴 25 度"))
                            ),
                            new CanonicalMessage(CanonicalMessageRole.USER, List.of(CanonicalContentPart.text("那上海呢？和北京比呢？")))
                    ),
                    List.of(new CanonicalToolDefinition(
                            "get_weather",
                            "查询城市天气",
                            objectMapper.readTree("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}},\"required\":[\"city\"]}"),
                            null
                    )),
                    null,
                    null,
                    null,
                    new CanonicalReasoningConfig(objectMapper.readTree("{\"effort\":\"low\"}"), "low"),
                    objectMapper.readTree("{\"metadata\":{\"client\":\"codex\"}}")
            );
            UpstreamCredentialEntity credential = new UpstreamCredentialEntity();
            credential.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            credential.setCredentialMetadataJson(mimoConversationProfileJson());
            var context = new GatewayChatRuntimeContext(
                    openAiCompatibleSelection("mimo-public", "mimo-v2.5-pro"),
                    credential,
                    new ResolvedCredentialMaterial(1L, 1L, CredentialAuthKind.API_KEY, "mimo-secret", null, Map.of(), null, "test"),
                    request,
                    null
            );

            var response = runtime.execute(context);

            assertEquals("/v1/chat/completions", upstreamPath.get());
            assertEquals("Bearer mimo-secret", authorization.get());
            JsonNode sent = objectMapper.readTree(requestBody.get());
            assertEquals("mimo-v2.5-pro", sent.path("model").asText());
            assertEquals("codex", sent.path("metadata").path("client").asText());
            assertEquals("low", sent.path("reasoning_effort").asText());
            assertEquals("enabled", sent.path("thinking").path("type").asText());
            JsonNode assistant = sent.path("messages").get(1);
            assertEquals("assistant", assistant.path("role").asText());
            assertEquals("我需要调用天气工具查询北京。", assistant.path("reasoning_content").asText());
            assertEquals("call_mimo_1", assistant.path("tool_calls").get(0).path("id").asText());
            assertEquals("get_weather", assistant.path("tool_calls").get(0).path("function").path("name").asText());
            assertEquals("{\"city\":\"北京\"}", assistant.path("tool_calls").get(0).path("function").path("arguments").asText());
            JsonNode tool = sent.path("messages").get(2);
            assertEquals("tool", tool.path("role").asText());
            assertEquals("call_mimo_1", tool.path("tool_call_id").asText());
            assertEquals("北京晴 25 度", tool.path("content").asText());
            assertEquals("上海比北京低 3 度。", response.outputText());
            assertEquals("我复用了上一轮工具结果，再比较上海天气。", response.reasoning());
            assertEquals(31, response.usage().promptTokens());
            assertEquals(9, response.usage().completionTokens());
            assertEquals(4, response.usage().reasoningTokens());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldRejectAssistantToolCallHistoryWithoutReasoningWhenProviderRequiresReplay() throws Exception {
        var request = new CanonicalRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.RESPONSES,
                "/v1/responses",
                "mimo-public",
                List.of(new CanonicalMessage(
                        CanonicalMessageRole.ASSISTANT,
                        List.of(),
                        null,
                        List.of(new CanonicalToolCall("call_mimo_1", "function", "get_weather", "{\"city\":\"北京\"}")),
                        null
                )),
                List.of(),
                null,
                null,
                null,
                null,
                objectMapper.createObjectNode()
        );
        UpstreamCredentialEntity credential = new UpstreamCredentialEntity();
        credential.setBaseUrl("http://127.0.0.1:65535/v1");
        credential.setCredentialMetadataJson(mimoConversationProfileJson());
        var context = new GatewayChatRuntimeContext(
                openAiCompatibleSelection("mimo-public", "mimo-v2.5-pro"),
                credential,
                new ResolvedCredentialMaterial(1L, 1L, CredentialAuthKind.API_KEY, "mimo-secret", null, Map.of(), null, "test"),
                request,
                null
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> runtime.execute(context));

        assertTrue(exception.getMessage().contains("reasoning_content"));
    }

    private RouteSelectionResult openAiDirectSelection(String publicModel, String resolvedModel) {
        var candidate = new CatalogCandidateView(
                1L,
                "openai-direct",
                ProviderType.OPENAI_DIRECT,
                1L,
                ProviderFamily.OPENAI,
                UpstreamSiteKind.OPENAI_DIRECT,
                AuthStrategy.BEARER,
                PathStrategy.OPENAI_V1,
                ErrorSchemaStrategy.OPENAI_ERROR,
                "https://api.openai.com/v1",
                publicModel,
                resolvedModel,
                List.of("openai", "responses"),
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                ReasoningTransport.RESPONSES,
                InteropCapabilityLevel.NATIVE
        );
        var routeCandidate = new RouteCandidateView(candidate, 1L, 0, 100);
        return new RouteSelectionResult(
                1L,
                "sk-gw-test",
                publicModel,
                publicModel,
                resolvedModel,
                "openai",
                "prefix",
                "fingerprint",
                "default",
                RouteSelectionSource.WEIGHTED_HASH,
                routeCandidate,
                List.of(routeCandidate)
        );
    }

    private RouteSelectionResult openAiCompatibleSelection(String publicModel, String resolvedModel) {
        var candidate = new CatalogCandidateView(
                1L,
                "xiaomi-mimo-openai-compatible",
                ProviderType.OPENAI_COMPATIBLE,
                1L,
                ProviderFamily.OPENAI,
                UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC,
                AuthStrategy.BEARER,
                PathStrategy.OPENAI_V1,
                ErrorSchemaStrategy.OPENAI_ERROR,
                "https://api.xiaomimimo.com/v1",
                publicModel,
                resolvedModel,
                List.of("openai_compatible", "responses", "reasoning"),
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                ReasoningTransport.OPENAI_CHAT,
                InteropCapabilityLevel.EMULATED
        );
        var routeCandidate = new RouteCandidateView(candidate, 1L, 0, 100);
        return new RouteSelectionResult(
                1L,
                "sk-gw-test",
                publicModel,
                publicModel,
                resolvedModel,
                "xiaomi_mimo",
                "prefix",
                "fingerprint",
                "default",
                RouteSelectionSource.WEIGHTED_HASH,
                routeCandidate,
                List.of(routeCandidate)
        );
    }

    private String mimoConversationProfileJson() {
        return """
                {
                  "conversationProfile": {
                    "ingressProtocol": "responses",
                    "upstreamSurface": "chat_completions",
                    "responsesCompatibility": {"mode": "emulate_with_chat_completions"},
                    "reasoning": {
                      "requestField": "extra_body.thinking",
                      "requestEnabledValue": {"type": "enabled"},
                      "assistantReasoningField": "reasoning_content",
                      "historyReplayPolicy": "required_when_tool_calls"
                    }
                  }
                }
                """;
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("content-type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void sendSse(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("content-type", "text/event-stream");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
