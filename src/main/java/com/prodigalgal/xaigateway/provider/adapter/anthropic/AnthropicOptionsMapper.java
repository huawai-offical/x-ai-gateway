package com.prodigalgal.xaigateway.provider.adapter.anthropic;

import com.anthropic.models.messages.Metadata;
import com.anthropic.models.messages.ToolChoice;
import com.anthropic.models.messages.ToolChoiceAny;
import com.anthropic.models.messages.ToolChoiceAuto;
import com.anthropic.models.messages.ToolChoiceNone;
import com.anthropic.models.messages.ToolChoiceTool;
import tools.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolDefinition;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.provider.adapter.SpringAiToolCallbackFactory;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.AnthropicCacheOptions;
import org.springframework.ai.anthropic.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.AnthropicCacheTtl;
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
        AnthropicChatOptions source = baseOptions == null ? AnthropicChatOptions.builder().build() : baseOptions.copy();

        AnthropicCacheOptions cacheOptions = AnthropicCacheOptions.builder()
                .strategy(resolveStrategy(selectionResult))
                .messageTypeTtl(MessageType.SYSTEM, AnthropicCacheTtl.FIVE_MINUTES)
                .messageTypeTtl(MessageType.TOOL, AnthropicCacheTtl.FIVE_MINUTES)
                .messageTypeMinContentLength(MessageType.SYSTEM, 512)
                .messageTypeMinContentLength(MessageType.TOOL, 256)
                .multiBlockSystemCaching(true)
                .build();

        Metadata metadata = Metadata.builder()
                .userId(metadataValue(selectionResult))
                .build();

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

    private ToolChoice mapToolChoice(JsonNode toolChoice) {
        if (toolChoice.isTextual()) {
            return switch (toolChoice.asText().toLowerCase(Locale.ROOT)) {
                case "any", "required" -> ToolChoice.ofAny(ToolChoiceAny.builder().build());
                case "none" -> ToolChoice.ofNone(ToolChoiceNone.builder().build());
                case "auto" -> ToolChoice.ofAuto(ToolChoiceAuto.builder().build());
                default -> ToolChoice.ofAuto(ToolChoiceAuto.builder().build());
            };
        }

        String type = toolChoice.path("type").asText();
        if ("tool".equalsIgnoreCase(type)) {
            return ToolChoice.ofTool(ToolChoiceTool.builder()
                    .name(toolChoice.path("name").asText())
                    .build());
        }
        if ("any".equalsIgnoreCase(type)) {
            return ToolChoice.ofAny(ToolChoiceAny.builder().build());
        }
        if ("none".equalsIgnoreCase(type)) {
            return ToolChoice.ofNone(ToolChoiceNone.builder().build());
        }
        return ToolChoice.ofAuto(ToolChoiceAuto.builder().build());
    }
}
