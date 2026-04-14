package com.prodigalgal.xaigateway.provider.adapter;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolDefinition;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.provider.adapter.anthropic.AnthropicOptionsMapper;
import com.prodigalgal.xaigateway.provider.adapter.anthropic.AnthropicUsageNormalizer;
import com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiCachedContentReferenceService;
import com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiOptionsMapper;
import com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiUsageNormalizer;
import com.prodigalgal.xaigateway.provider.adapter.openai.OpenAiOptionsMapper;
import com.prodigalgal.xaigateway.provider.adapter.openai.OpenAiPromptCacheKeyService;
import com.prodigalgal.xaigateway.provider.adapter.openai.OpenAiToolMapper;
import com.prodigalgal.xaigateway.provider.adapter.openai.OpenAiUsageNormalizer;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.openai.OpenAiChatOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderExecutionSupportServiceTests {

    @Test
    void shouldPrepareOpenAiExecutionUsingPromptCacheKey() {
        SpringAiToolCallbackFactory toolCallbackFactory = new SpringAiToolCallbackFactory(new ObjectMapper());
        ProviderExecutionSupportService service = new ProviderExecutionSupportService(
                new OpenAiOptionsMapper(new OpenAiPromptCacheKeyService(), new OpenAiToolMapper(new ObjectMapper())),
                new AnthropicOptionsMapper(toolCallbackFactory),
                new GeminiOptionsMapper(Mockito.mock(GeminiCachedContentReferenceService.class), toolCallbackFactory),
                new OpenAiUsageNormalizer(),
                new AnthropicUsageNormalizer(),
                new GeminiUsageNormalizer()
        );

        RouteSelectionResult selectionResult = selectionResult(ProviderType.OPENAI_DIRECT);
        PreparedChatExecution<OpenAiChatOptions> prepared = service.prepareOpenAi(
                selectionResult,
                OpenAiChatOptions.builder().model("gpt-4o").build(),
                List.of(new GatewayToolDefinition(
                        "lookup_weather",
                        "Lookup weather",
                        new ObjectMapper().valueToTree(Map.of("type", "object")),
                        true
                )),
                new ObjectMapper().valueToTree("auto")
        );

        assertEquals(ProviderType.OPENAI_DIRECT, prepared.providerType());
        assertEquals("gpt-4o", prepared.options().getModel());
        assertEquals("PREFIX_AFFINITY", prepared.options().getMetadata().get("gateway.selection_source"));
        assertEquals(1, prepared.options().getTools().size());
        assertEquals("auto", prepared.options().getToolChoice());
    }

    private RouteSelectionResult selectionResult(ProviderType providerType) {
        CatalogCandidateView candidate = new CatalogCandidateView(
                101L,
                "provider",
                providerType,
                "https://example.com",
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
        RouteCandidateView selected = new RouteCandidateView(candidate, 11L, 10, 100);
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
                selected,
                List.of(selected)
        );
    }
}
