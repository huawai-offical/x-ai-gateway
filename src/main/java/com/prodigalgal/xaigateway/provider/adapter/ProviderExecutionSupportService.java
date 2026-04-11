package com.prodigalgal.xaigateway.provider.adapter;

import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolDefinition;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.provider.adapter.anthropic.AnthropicOptionsMapper;
import com.prodigalgal.xaigateway.provider.adapter.anthropic.AnthropicUsageNormalizer;
import com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiOptionsMapper;
import com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiUsageNormalizer;
import com.prodigalgal.xaigateway.provider.adapter.openai.OpenAiOptionsMapper;
import com.prodigalgal.xaigateway.provider.adapter.openai.OpenAiUsageNormalizer;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

@Service
public class ProviderExecutionSupportService {

    private final OpenAiOptionsMapper openAiOptionsMapper;
    private final AnthropicOptionsMapper anthropicOptionsMapper;
    private final GeminiOptionsMapper geminiOptionsMapper;
    private final OpenAiUsageNormalizer openAiUsageNormalizer;
    private final AnthropicUsageNormalizer anthropicUsageNormalizer;
    private final GeminiUsageNormalizer geminiUsageNormalizer;

    public ProviderExecutionSupportService(
            OpenAiOptionsMapper openAiOptionsMapper,
            AnthropicOptionsMapper anthropicOptionsMapper,
            GeminiOptionsMapper geminiOptionsMapper,
            OpenAiUsageNormalizer openAiUsageNormalizer,
            AnthropicUsageNormalizer anthropicUsageNormalizer,
            GeminiUsageNormalizer geminiUsageNormalizer) {
        this.openAiOptionsMapper = openAiOptionsMapper;
        this.anthropicOptionsMapper = anthropicOptionsMapper;
        this.geminiOptionsMapper = geminiOptionsMapper;
        this.openAiUsageNormalizer = openAiUsageNormalizer;
        this.anthropicUsageNormalizer = anthropicUsageNormalizer;
        this.geminiUsageNormalizer = geminiUsageNormalizer;
    }

    public PreparedChatExecution<OpenAiChatOptions> prepareOpenAi(
            RouteSelectionResult selectionResult,
            OpenAiChatOptions baseOptions,
            java.util.List<GatewayToolDefinition> tools,
            JsonNode toolChoice) {
        ProviderType providerType = selectionResult.selectedCandidate().candidate().providerType();
        ensureSupported(providerType, ProviderType.OPENAI_DIRECT, ProviderType.OPENAI_COMPATIBLE);
        return new PreparedChatExecution<>(
                providerType,
                selectionResult,
                openAiOptionsMapper.applyPromptCacheKey(baseOptions, selectionResult, tools, toolChoice)
        );
    }

    public PreparedChatExecution<AnthropicChatOptions> prepareAnthropic(RouteSelectionResult selectionResult, AnthropicChatOptions baseOptions) {
        return prepareAnthropic(selectionResult, baseOptions, java.util.List.of(), null);
    }

    public PreparedChatExecution<AnthropicChatOptions> prepareAnthropic(
            RouteSelectionResult selectionResult,
            AnthropicChatOptions baseOptions,
            java.util.List<GatewayToolDefinition> tools,
            JsonNode toolChoice) {
        ProviderType providerType = selectionResult.selectedCandidate().candidate().providerType();
        ensureSupported(providerType, ProviderType.ANTHROPIC_DIRECT);
        return new PreparedChatExecution<>(
                providerType,
                selectionResult,
                anthropicOptionsMapper.applyCacheOptions(baseOptions, selectionResult, tools, toolChoice)
        );
    }

    public PreparedChatExecution<GoogleGenAiChatOptions> prepareGemini(RouteSelectionResult selectionResult, GoogleGenAiChatOptions baseOptions) {
        return prepareGemini(selectionResult, baseOptions, java.util.List.of());
    }

    public PreparedChatExecution<GoogleGenAiChatOptions> prepareGemini(
            RouteSelectionResult selectionResult,
            GoogleGenAiChatOptions baseOptions,
            java.util.List<GatewayToolDefinition> tools) {
        ProviderType providerType = selectionResult.selectedCandidate().candidate().providerType();
        ensureSupported(providerType, ProviderType.GEMINI_DIRECT);
        return new PreparedChatExecution<>(
                providerType,
                selectionResult,
                geminiOptionsMapper.applyCacheOptions(baseOptions, selectionResult, tools)
        );
    }

    public GatewayUsage normalizeUsage(RouteSelectionResult selectionResult, Usage usage) {
        ProviderType providerType = selectionResult.selectedCandidate().candidate().providerType();
        return switch (providerType) {
            case OPENAI_DIRECT, OPENAI_COMPATIBLE -> openAiUsageNormalizer.normalize(usage);
            case ANTHROPIC_DIRECT -> anthropicUsageNormalizer.normalize(usage);
            case GEMINI_DIRECT -> geminiUsageNormalizer.normalize(usage);
            case OLLAMA_DIRECT -> GatewayUsage.empty();
        };
    }

    private void ensureSupported(ProviderType actual, ProviderType... supported) {
        for (ProviderType candidate : supported) {
            if (candidate == actual) {
                return;
            }
        }
        throw new IllegalArgumentException("当前选中的 providerType 与目标执行器不匹配：" + actual);
    }
}
