package com.prodigalgal.xaigateway.protocol;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionResult;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionStreamResult;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResponse;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEventType;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolCall;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalUsage;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.protocol.ingress.anthropic.AnthropicMessagesEncoder;
import com.prodigalgal.xaigateway.protocol.ingress.anthropic.AnthropicMessagesResponse;
import com.prodigalgal.xaigateway.protocol.ingress.google.GeminiGenerateContentEncoder;
import com.prodigalgal.xaigateway.protocol.ingress.google.GeminiGenerateContentResponse;
import com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionEncoder;
import com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionResponse;
import com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesEncoder;
import com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayProtocolEncoderContractTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldEncodeOpenAiChatCompletionContract() {
        OpenAiChatCompletionEncoder encoder = new OpenAiChatCompletionEncoder(objectMapper);
        OpenAiChatCompletionResponse response = encoder.encode(canonicalResult(
                "req-openai-contract",
                ProviderType.OPENAI_DIRECT,
                CanonicalIngressProtocol.OPENAI,
                "gpt-4o",
                "hello contract",
                canonicalUsage(700, 200, 900, 300, 0, 20),
                List.of(),
                null,
                GatewayFinishReason.STOP
        ));

        assertEquals("chat.completion", response.object());
        assertEquals("hello contract", response.choices().get(0).message().content());
        assertEquals(300, response.usage().promptTokensDetails().cachedTokens());
        assertEquals(20, response.usage().completionTokensDetails().reasoningTokens());
    }

    @Test
    void shouldEncodeOpenAiResponsesStreamContract() {
        OpenAiResponsesEncoder encoder = new OpenAiResponsesEncoder(objectMapper);
        CanonicalExecutionStreamResult streamResponse = canonicalStreamResult(
                "req-responses-contract",
                ProviderType.OPENAI_DIRECT,
                CanonicalIngressProtocol.RESPONSES,
                "writer-fast",
                Flux.just(
                        new CanonicalStreamEvent(CanonicalStreamEventType.REASONING_DELTA, null, "step 1", List.of(), CanonicalUsage.empty(), false, null, null, null),
                        new CanonicalStreamEvent(CanonicalStreamEventType.TEXT_DELTA, "hello", null, List.of(), CanonicalUsage.empty(), false, null, null, null),
                        new CanonicalStreamEvent(CanonicalStreamEventType.TOOL_CALLS, null, null, List.of(new CanonicalToolCall("call_1", "function", "lookup_weather", "{\"city\":\"Shanghai\"}")), CanonicalUsage.empty(), false, null, null, null),
                        new CanonicalStreamEvent(CanonicalStreamEventType.COMPLETED, null, null, List.of(), canonicalUsage(40, 20, 60, 10, 0, 5), true, GatewayFinishReason.TOOL_CALLS, "hello", "step 1")
                )
        );

        String joined = String.join("", encoder.encodeStream(streamResponse).collectList().block());
        assertTrue(joined.contains("event: response.created"));
        assertTrue(joined.contains("event: response.reasoning_summary_text.delta"));
        assertTrue(joined.contains("event: response.function_call_arguments.done"));
        assertTrue(joined.contains("event: response.completed"));
    }

    @Test
    void shouldEncodeAnthropicMessageContract() {
        AnthropicMessagesEncoder encoder = new AnthropicMessagesEncoder(objectMapper);
        AnthropicMessagesResponse response = encoder.encode(canonicalResult(
                "req-anthropic-contract",
                ProviderType.ANTHROPIC_DIRECT,
                CanonicalIngressProtocol.ANTHROPIC_NATIVE,
                "claude-sonnet-4",
                "",
                canonicalUsage(900, 200, 1100, 300, 120, 0),
                List.of(new CanonicalToolCall("toolu_1", "tool_use", "lookup_weather", "{\"city\":\"Shanghai\"}")),
                null,
                GatewayFinishReason.TOOL_CALLS
        ));

        assertEquals("tool_use", response.stopReason());
        assertEquals("tool_use", response.content().get(0).type());
        assertEquals("lookup_weather", response.content().get(0).name());
        assertEquals(300, response.usage().cacheReadInputTokens());
        assertEquals(120, response.usage().cacheCreationInputTokens());
    }

    @Test
    void shouldEncodeGeminiContract() {
        GeminiGenerateContentEncoder encoder = new GeminiGenerateContentEncoder(objectMapper);
        GeminiGenerateContentResponse response = encoder.encode(canonicalResult(
                "req-gemini-contract",
                ProviderType.GEMINI_DIRECT,
                CanonicalIngressProtocol.GOOGLE_NATIVE,
                "gemini-2.5-pro",
                "",
                canonicalUsage(720, 350, 1070, 280, 0, 40),
                List.of(new CanonicalToolCall("call_1", "function", "lookup_weather", "{\"city\":\"Shanghai\"}")),
                null,
                GatewayFinishReason.TOOL_CALLS
        ));

        assertEquals("lookup_weather", response.candidates().get(0).content().parts().get(0).functionCall().name());
        assertEquals(280, response.usageMetadata().cachedContentTokenCount());
        assertEquals(40, response.usageMetadata().thoughtsTokenCount());
    }

    private CanonicalExecutionResult canonicalResult(
            String requestId,
            ProviderType providerType,
            CanonicalIngressProtocol protocol,
            String model,
            String text,
            CanonicalUsage usage,
            List<CanonicalToolCall> toolCalls,
            String reasoning,
            GatewayFinishReason finishReason) {
        RouteSelectionResult selectionResult = selectionResult(providerType, protocol, model);
        return new CanonicalExecutionResult(
                requestId,
                selectionResult,
                plan(protocol, model),
                new CanonicalResponse(requestId, selectionResult.publicModel(), text, reasoning, toolCalls, usage, finishReason)
        );
    }

    private CanonicalExecutionStreamResult canonicalStreamResult(
            String requestId,
            ProviderType providerType,
            CanonicalIngressProtocol protocol,
            String model,
            Flux<CanonicalStreamEvent> events) {
        RouteSelectionResult selectionResult = selectionResult(providerType, protocol, model);
        return new CanonicalExecutionStreamResult(requestId, selectionResult, plan(protocol, model), events);
    }

    private CanonicalExecutionPlan plan(CanonicalIngressProtocol protocol, String model) {
        return new CanonicalExecutionPlan(
                true,
                protocol,
                switch (protocol) {
                    case OPENAI -> "/v1/chat/completions";
                    case RESPONSES -> "/v1/responses";
                    case ANTHROPIC_NATIVE -> "/v1/messages";
                    case GOOGLE_NATIVE -> "/v1beta/models/" + model + ":generateContent";
                    case UNKNOWN -> "/unknown";
                },
                model,
                model,
                model,
                protocol == CanonicalIngressProtocol.RESPONSES ? TranslationResourceType.RESPONSE : TranslationResourceType.CHAT,
                protocol == CanonicalIngressProtocol.RESPONSES ? TranslationOperation.RESPONSE_CREATE : TranslationOperation.CHAT_COMPLETION,
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

    private CanonicalUsage canonicalUsage(
            int promptTokens,
            int completionTokens,
            int totalTokens,
            int cacheHitTokens,
            int cacheWriteTokens,
            int reasoningTokens) {
        return new CanonicalUsage(true, promptTokens, completionTokens, totalTokens, cacheHitTokens, cacheWriteTokens, reasoningTokens);
    }

    private RouteSelectionResult selectionResult(ProviderType providerType, CanonicalIngressProtocol protocol, String model) {
        com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView candidate = new com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView(
                101L,
                "contract-candidate",
                providerType,
                "https://api.example.com",
                model,
                model,
                List.of(protocol.name().toLowerCase()),
                true,
                true,
                true,
                true,
                true,
                true,
                ReasoningTransport.OPENAI_CHAT
        );
        RouteCandidateView routeCandidateView = new RouteCandidateView(candidate, 1L, 10, 100);
        return new RouteSelectionResult(
                1L,
                "sk-gw-test",
                model,
                model,
                model,
                protocol.name().toLowerCase(),
                "prefix",
                "fingerprint",
                model,
                RouteSelectionSource.PREFIX_AFFINITY,
                routeCandidateView,
                List.of(routeCandidateView)
        );
    }
}
