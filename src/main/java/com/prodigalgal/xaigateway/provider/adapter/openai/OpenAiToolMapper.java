package com.prodigalgal.xaigateway.provider.adapter.openai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolDefinition;
import java.util.List;
import java.util.Map;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;

@Service
public class OpenAiToolMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public OpenAiToolMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<OpenAiApi.FunctionTool> toFunctionTools(List<GatewayToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }

        return tools.stream()
                .map(this::toFunctionTool)
                .toList();
    }

    public Object toToolChoice(JsonNode toolChoice) {
        if (toolChoice == null || toolChoice.isNull() || toolChoice.isMissingNode()) {
            return null;
        }

        if (toolChoice.isTextual()) {
            return toolChoice.asText();
        }

        return objectMapper.convertValue(toolChoice, MAP_TYPE);
    }

    private OpenAiApi.FunctionTool toFunctionTool(GatewayToolDefinition tool) {
        Map<String, Object> parameters = tool.inputSchema() == null
                ? Map.of("type", "object")
                : objectMapper.convertValue(tool.inputSchema(), MAP_TYPE);

        OpenAiApi.FunctionTool.Function function = new OpenAiApi.FunctionTool.Function(
                tool.name(),
                tool.description(),
                parameters,
                tool.strict()
        );
        return new OpenAiApi.FunctionTool(function);
    }
}
