package com.prodigalgal.xaigateway.provider.adapter;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolDefinition;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;

@Service
public class SpringAiToolCallbackFactory {

    private final ObjectMapper objectMapper;

    public SpringAiToolCallbackFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ToolCallback> toCallbacks(List<GatewayToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }

        return tools.stream()
                .map(this::toCallback)
                .toList();
    }

    private ToolCallback toCallback(GatewayToolDefinition tool) {
        String schema = tool.inputSchema() == null
                ? "{\"type\":\"object\"}"
                : tool.inputSchema().toString();

        return FunctionToolCallback.<Map<String, Object>, String>builder(
                        tool.name(),
                        input -> "{}"
                )
                .description(tool.description())
                .inputSchema(schema)
                .inputType(Map.class)
                .build();
    }
}
