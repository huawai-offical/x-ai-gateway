package com.prodigalgal.xaigateway.protocol.ingress.google;

import com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionService;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayResponse;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamEventType;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamResponse;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageCompleteness;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageSource;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageView;
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

@WebFluxTest(controllers = GeminiGenerateContentController.class)
@Import({PermitAllSecurityTestConfig.class, GeminiGenerateContentRequestMapper.class})
class GeminiGenerateContentControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private DistributedKeyAuthenticationService distributedKeyAuthenticationService;

    @MockitoBean
    private GatewayChatExecutionService gatewayChatExecutionService;

    @Test
    void shouldExecuteMinimalGenerateContent() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(gatewayResponse("req-gemini-1", "gemini back", new GatewayUsage(1000, 720, 350, 40, 280, 0, 280, 0, null, 1350, null), List.of()));

        webTestClient.post()
                .uri("/v1beta/models/gemini-2.5-pro:generateContent")
                .header("x-goog-api-key", "sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "systemInstruction": {"text":"you are helpful"},
                          "contents": [
                            {
                              "role":"user",
                              "parts":[{"text":"hello"}]
                            }
                          ],
                          "generationConfig": {
                            "temperature": 0.2,
                            "maxOutputTokens": 256
                          }
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.candidates[0].content.parts[0].text").isEqualTo("gemini back")
                .jsonPath("$.usageMetadata.cachedContentTokenCount").isEqualTo(280)
                .jsonPath("$.usageMetadata.thoughtsTokenCount").isEqualTo(40);
    }

    @Test
    void shouldReturnFunctionCallParts() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(gatewayResponse(
                        "req-gemini-tool-1",
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
                .uri("/v1beta/models/gemini-2.5-pro:generateContent")
                .header("x-goog-api-key", "sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "contents": [
                            {
                              "role":"user",
                              "parts":[{"text":"帮我查天气"}]
                            }
                          ],
                          "tools": [
                            {
                              "functionDeclarations": [
                                {
                                  "name":"lookup_weather",
                                  "description":"Lookup weather",
                                  "parameters":{"type":"object"}
                                }
                              ]
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.candidates[0].content.parts[0].functionCall.name").isEqualTo("lookup_weather")
                .jsonPath("$.candidates[0].content.parts[0].functionCall.args.city").isEqualTo("Shanghai");
    }

    @Test
    void shouldAcceptGeminiFileDataImageParts() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(gatewayResponse("req-gemini-image-1", "image processed", GatewayUsage.empty(), List.of()));

        webTestClient.post()
                .uri("/v1beta/models/gemini-2.5-pro:generateContent")
                .header("x-goog-api-key", "sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "contents": [
                            {
                              "role":"user",
                              "parts":[
                                {"text":"描述图片"},
                                {"fileData":{"mimeType":"image/png","fileUri":"https://example.com/cat.png"}}
                              ]
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.candidates[0].content.parts[0].text").isEqualTo("image processed");
    }

    @Test
    void shouldAcceptGeminiFileDataDocumentParts() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(gatewayResponse("req-gemini-doc-1", "document processed", GatewayUsage.empty(), List.of()));

        webTestClient.post()
                .uri("/v1beta/models/gemini-2.5-pro:generateContent")
                .header("x-goog-api-key", "sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "contents": [
                            {
                              "role":"user",
                              "parts":[
                                {"text":"总结这个文档"},
                                {"fileData":{"mimeType":"application/pdf","fileUri":"https://example.com/doc.pdf"}}
                              ]
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.candidates[0].content.parts[0].text").isEqualTo("document processed");
    }

    @Test
    void shouldAcceptGeminiGatewayFileIdParts() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(gatewayResponse("req-gemini-fileid-1", "gateway file processed", GatewayUsage.empty(), List.of()));

        webTestClient.post()
                .uri("/v1beta/models/gemini-2.5-pro:generateContent")
                .header("x-goog-api-key", "sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "contents": [
                            {
                              "role":"user",
                              "parts":[
                                {"fileData":{"mimeType":"application/pdf","fileId":"file-123"}}
                              ]
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.candidates[0].content.parts[0].text").isEqualTo("gateway file processed");
    }

    @Test
    void shouldAcceptGeminiFileOnlyMessage() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayResponse(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(gatewayResponse("req-gemini-file-only-1", "file only processed", GatewayUsage.empty(), List.of()));

        webTestClient.post()
                .uri("/v1beta/models/gemini-2.5-pro:generateContent")
                .header("x-goog-api-key", "sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "contents": [
                            {
                              "role":"user",
                              "parts":[
                                {"fileData":{"mimeType":"application/pdf","fileUri":"https://example.com/doc.pdf"}}
                              ]
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.candidates[0].content.parts[0].text").isEqualTo("file only processed");
    }

    @Test
    void shouldExecuteMinimalStreamGenerateContent() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayChatExecutionService.executeGatewayStream(Mockito.<ChatExecutionRequest>any()))
                .thenReturn(new GatewayStreamResponse(
                        "req-gemini-stream-1",
                        selectionResult(),
                        Flux.just(
                                textEvent("hello"),
                                textEvent(" world"),
                                completedEvent("hello world")
                        )
                ));

        var result = webTestClient.post()
                .uri("/v1beta/models/gemini-2.5-pro:streamGenerateContent")
                .header("x-goog-api-key", "sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "contents": [
                            {
                              "role":"user",
                              "parts":[{"text":"hello"}]
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class);

        var body = result.getResponseBody().collectList().block();
        assert body != null;
        String joined = String.join("", body);
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("gemini back") || joined.contains("hello"));
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("usageMetadata"));
    }

    @Test
    void shouldRejectGeminiRequestWithoutUserPayload() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));

        webTestClient.post()
                .uri("/v1beta/models/gemini-2.5-pro:generateContent")
                .header("x-goog-api-key", "sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "contents": [
                            {
                              "role":"model",
                              "parts":[{"text":"hello"}]
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_ARGUMENT")
                .jsonPath("$.message").isEqualTo("至少需要一条带 text 的 user content。");
    }

    private RouteSelectionResult selectionResult() {
        CatalogCandidateView candidate = new CatalogCandidateView(
                301L,
                "gemini-primary",
                ProviderType.GEMINI_DIRECT,
                "https://generativelanguage.googleapis.com",
                "gemini-2.5-pro",
                "gemini-2.5-pro",
                List.of("google_native", "openai"),
                true,
                true,
                true,
                true,
                true,
                true,
                ReasoningTransport.GEMINI_THOUGHTS
        );
        RouteCandidateView routeCandidateView = new RouteCandidateView(candidate, 31L, 10, 100);
        return new RouteSelectionResult(
                1L,
                "sk-gw-test",
                "gemini-2.5-pro",
                "gemini-2.5-pro",
                "gemini-2.5-pro",
                "google_native",
                "prefix-hash",
                "fingerprint",
                "gemini-2.5-pro",
                RouteSelectionSource.PREFIX_AFFINITY,
                routeCandidateView,
                List.of(routeCandidateView)
        );
    }

    private GatewayResponse gatewayResponse(String requestId, String text, GatewayUsage usage, List<GatewayToolCall> toolCalls) {
        return new GatewayResponse(
                requestId,
                selectionResult(),
                text,
                new GatewayUsageView(
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
                ),
                toolCalls,
                null,
                toolCalls.isEmpty() ? GatewayFinishReason.STOP : GatewayFinishReason.TOOL_CALLS,
                null
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

    private GatewayStreamEvent completedEvent(String text) {
        return new GatewayStreamEvent(
                GatewayStreamEventType.COMPLETED,
                null,
                null,
                List.of(),
                GatewayUsageView.empty(),
                true,
                GatewayFinishReason.STOP,
                text,
                null,
                null
        );
    }
}
