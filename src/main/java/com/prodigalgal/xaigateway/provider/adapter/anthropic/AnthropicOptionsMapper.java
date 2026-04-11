package com.prodigalgal.xaigateway.provider.adapter.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolDefinition;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.provider.adapter.SpringAiToolCallbackFactory;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.api.AnthropicCacheOptions;
import org.springframework.ai.anthropic.api.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.api.AnthropicCacheTtl;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Service;

@Service
public class AnthropicOptionsMapper {

    private final SpringAiToolCallbackFactory springAiToolCallbackFactory;

    public AnthropicOptionsMapper(SpringAiToolCallbackFactory springAiToolCallbackFactory) {
        this.springAiToolCallbackFactory = springAiToolCallbackFactory;
    }

    public AnthropicChatOptions applyCacheOptions(
            AnthropicChatOptions baseOptions,
            RouteSelectionResult selectionResult,
            List<GatewayToolDefinition> tools,
            JsonNode toolChoice) {
        AnthropicChatOptions source = baseOptions == null ? AnthropicChatOptions.builder().build() : AnthropicChatOptions.fromOptions(baseOptions);

        AnthropicCacheOptions cacheOptions = AnthropicCacheOptions.builder()
                .strategy(resolveStrategy(selectionResult))
                .messageTypeTtl(MessageType.SYSTEM, AnthropicCacheTtl.FIVE_MINUTES)
                .messageTypeTtl(MessageType.TOOL, AnthropicCacheTtl.FIVE_MINUTES)
                .messageTypeMinContentLength(MessageType.SYSTEM, 512)
                .messageTypeMinContentLength(MessageType.TOOL, 256)
                .multiBlockSystemCaching(true)
                .build();

        AnthropicApi.ChatCompletionRequest.Metadata metadata = new AnthropicApi.ChatCompletionRequest.Metadata(
                metadataValue(selectionResult)
        );

        AnthropicChatOptions.Builder builder = AnthropicChatOptions.builder()
                .model(source.getModel())
                .maxTokens(source.getMaxTokens())
                .temperature(source.getTemperature())
                .topP(source.getTopP())
                .topK(source.getTopK())
                .thinking(source.getThinking())
                .cacheOptions(cacheOptions)
                .metadata(metadata)
                .internalToolExecutionEnabled(false);

        if (tools != null && !tools.isEmpty()) {
            builder.toolCallbacks(springAiToolCallbackFactory.toCallbacks(tools));
        }

        if (toolChoice != null && !toolChoice.isNull() && !toolChoice.isMissingNode()) {
            builder.toolChoice(mapToolChoice(toolChoice));
        } else if (source.getToolChoice() != null) {
            builder.toolChoice(source.getToolChoice());
        }

        return builder.build();
    }

    private AnthropicCacheStrategy resolveStrategy(RouteSelectionResult selectionResult) {
        String protocol = selectionResult.protocol().toLowerCase(Locale.ROOT);
        if ("anthropic_native".equals(protocol)) {
            return AnthropicCacheStrategy.SYSTEM_AND_TOOLS;
        }
        return AnthropicCacheStrategy.SYSTEM_AND_TOOLS;
    }

    private String metadataValue(RouteSelectionResult selectionResult) {
        return "xag-" + selectionResult.distributedKeyId()
                + "|modelGroup=" + selectionResult.modelGroup()
                + ",selectionSource=" + selectionResult.selectionSource().name()
                + ",prefixHash=" + selectionResult.prefixHash();
    }

    private AnthropicApi.ToolChoice mapToolChoice(JsonNode toolChoice) {
        if (toolChoice.isTextual()) {
            return switch (toolChoice.asText().toLowerCase(Locale.ROOT)) {
                case "any", "required" -> new AnthropicApi.ToolChoiceAny();
                case "none" -> new AnthropicApi.ToolChoiceNone();
                case "auto" -> new AnthropicApi.ToolChoiceAuto();
                default -> new AnthropicApi.ToolChoiceAuto();
            };
        }

        String type = toolChoice.path("type").asText();
        if ("tool".equalsIgnoreCase(type)) {
            return new AnthropicApi.ToolChoiceTool(toolChoice.path("name").asText());
        }
        if ("any".equalsIgnoreCase(type)) {
            return new AnthropicApi.ToolChoiceAny();
        }
        if ("none".equalsIgnoreCase(type)) {
            return new AnthropicApi.ToolChoiceNone();
        }
        return new AnthropicApi.ToolChoiceAuto();
    }
}
