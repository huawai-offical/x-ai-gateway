package com.prodigalgal.xaigateway.provider.adapter.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolDefinition;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

@Service
public class OpenAiOptionsMapper {

    private final OpenAiPromptCacheKeyService promptCacheKeyService;
    private final OpenAiToolMapper openAiToolMapper;

    public OpenAiOptionsMapper(
            OpenAiPromptCacheKeyService promptCacheKeyService,
            OpenAiToolMapper openAiToolMapper) {
        this.promptCacheKeyService = promptCacheKeyService;
        this.openAiToolMapper = openAiToolMapper;
    }

    public OpenAiChatOptions applyPromptCacheKey(
            OpenAiChatOptions baseOptions,
            RouteSelectionResult selectionResult,
            List<GatewayToolDefinition> tools,
            JsonNode toolChoice) {
        OpenAiChatOptions source = baseOptions == null ? OpenAiChatOptions.builder().build() : OpenAiChatOptions.fromOptions(baseOptions);
        OpenAiChatOptions.Builder builder = new OpenAiChatOptions.Builder(source);

        String promptCacheKey = promptCacheKeyService.build(selectionResult);
        builder.promptCacheKey(promptCacheKey);

        Map<String, String> metadata = new LinkedHashMap<>();
        if (source.getMetadata() != null) {
            metadata.putAll(source.getMetadata());
        }
        metadata.put("gateway.distributed_key_id", String.valueOf(selectionResult.distributedKeyId()));
        metadata.put("gateway.model_group", selectionResult.modelGroup());
        metadata.put("gateway.selection_source", selectionResult.selectionSource().name());
        builder.metadata(metadata);

        if (tools != null && !tools.isEmpty()) {
            builder.tools(openAiToolMapper.toFunctionTools(tools));
        }

        Object mappedToolChoice = openAiToolMapper.toToolChoice(toolChoice);
        if (mappedToolChoice != null) {
            builder.toolChoice(mappedToolChoice);
        }

        return builder.build();
    }
}
