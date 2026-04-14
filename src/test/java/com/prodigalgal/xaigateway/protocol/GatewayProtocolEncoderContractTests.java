package com.prodigalgal.xaigateway.protocol;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
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
        OpenAiChatCompletionResponse response = encoder.encode(gatewayResponse(
                "req-openai-contract",
                "hello contract",
                usageView(1000, 700, 200, 20, 300, 0, null),
                List.of(),
                null,
                GatewayFinishReason.STOP,
                ProviderType.OPENAI_DIRECT,
                "openai"
        ));

        assertEquals("chat.completion", response.object());
        assertEquals("hello contract", response.choices().get(0).message().content());
        assertEquals(300, response.usage().promptTokensDetails().cachedTokens());
        assertEquals(20, response.usage().completionTokensDetails().reasoningTokens());
    }

    @Test
    void shouldEncodeOpenAiResponsesStreamContract() {
        OpenAiResponsesEncoder encoder = new OpenAiResponsesEncoder(objectMapper);
        GatewayStreamResponse streamResponse = new GatewayStreamResponse(
                "req-responses-contract",
                selectionResult(ProviderType.OPENAI_DIRECT, "responses", "writer-fast"),
                Flux.just(
                        new GatewayStreamEvent(GatewayStreamEventType.REASONING_DELTA, null, "step 1", List.of(), GatewayUsageView.empty(), false, null, null, null, null),
                        new GatewayStreamEvent(GatewayStreamEventType.TEXT_DELTA, "hello", null, List.of(), GatewayUsageView.empty(), false, null, null, null, null),
                        new GatewayStreamEvent(GatewayStreamEventType.TOOL_CALLS, null, null, List.of(new GatewayToolCall("call_1", "function", "lookup_weather", "{\"city\":\"Shanghai\"}")), GatewayUsageView.empty(), false, null, null, null, null),
                        new GatewayStreamEvent(GatewayStreamEventType.COMPLETED, null, null, List.of(), usageView(100, 40, 20, 5, 60, 0, null), true, GatewayFinishReason.TOOL_CALLS, "hello", "step 1", null)
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
        AnthropicMessagesResponse response = encoder.encode(gatewayResponse(
                "req-anthropic-contract",
                "",
                usageView(900, 900, 200, 0, 300, 120, null),
                List.of(new GatewayToolCall("toolu_1", "tool_use", "lookup_weather", "{\"city\":\"Shanghai\"}")),
                null,
                GatewayFinishReason.TOOL_CALLS,
                ProviderType.ANTHROPIC_DIRECT,
                "anthropic_native"
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
        GeminiGenerateContentResponse response = encoder.encode(gatewayResponse(
                "req-gemini-contract",
                "",
                usageView(1000, 720, 350, 40, 280, 0, "cached-content-1"),
                List.of(new GatewayToolCall("call_1", "function", "lookup_weather", "{\"city\":\"Shanghai\"}")),
                null,
                GatewayFinishReason.TOOL_CALLS,
                ProviderType.GEMINI_DIRECT,
                "google_native"
        ));

        assertEquals("lookup_weather", response.candidates().get(0).content().parts().get(0).functionCall().name());
        assertEquals(280, response.usageMetadata().cachedContentTokenCount());
        assertEquals(40, response.usageMetadata().thoughtsTokenCount());
    }

    private GatewayResponse gatewayResponse(
            String requestId,
            String text,
            GatewayUsageView usage,
            List<GatewayToolCall> toolCalls,
            String reasoning,
            GatewayFinishReason finishReason,
            ProviderType providerType,
            String protocol) {
        return new GatewayResponse(
                requestId,
                selectionResult(providerType, protocol, "gpt-4o"),
                text,
                usage,
                toolCalls,
                reasoning,
                finishReason,
                null
        );
    }

    private GatewayUsageView usageView(
            int rawPromptTokens,
            int promptTokens,
            int completionTokens,
            int reasoningTokens,
            int cacheHitTokens,
            int cacheWriteTokens,
            String cachedContentRef) {
        return new GatewayUsageView(
                rawPromptTokens,
                promptTokens,
                completionTokens,
                reasoningTokens,
                cacheHitTokens,
                cacheWriteTokens,
                cacheHitTokens,
                cacheWriteTokens,
                Math.max(rawPromptTokens - promptTokens - cacheWriteTokens, 0),
                cachedContentRef,
                rawPromptTokens + completionTokens,
                GatewayUsageCompleteness.FINAL,
                GatewayUsageSource.DIRECT_RESPONSE,
                null
        );
    }

    private RouteSelectionResult selectionResult(ProviderType providerType, String protocol, String model) {
        CatalogCandidateView candidate = new CatalogCandidateView(
                101L,
                "contract-candidate",
                providerType,
                "https://api.example.com",
                model,
                model,
                List.of(protocol),
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
                protocol,
                "prefix",
                "fingerprint",
                model,
                RouteSelectionSource.PREFIX_AFFINITY,
                routeCandidateView,
                List.of(routeCandidateView)
        );
    }
}
