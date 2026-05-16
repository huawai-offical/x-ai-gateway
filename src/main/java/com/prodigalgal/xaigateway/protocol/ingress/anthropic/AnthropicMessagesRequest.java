package com.prodigalgal.xaigateway.protocol.ingress.anthropic;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record AnthropicMessagesRequest(
        @NotBlank(message = "model 不能为空。")
        String model,
        JsonNode system,
        @Valid
        List<Message> messages,
        List<Tool> tools,
        @JsonProperty("tool_choice")
        JsonNode toolChoice,
        JsonNode thinking,
        Double temperature,
        @JsonProperty("service_tier")
        String serviceTier,
        JsonNode metadata,
        JsonNode container,
        @JsonProperty("context_management")
        JsonNode contextManagement,
        @JsonProperty("mcp_servers")
        JsonNode mcpServers,
        @JsonProperty("x_ai_gateway_mcp_allowlist")
        JsonNode mcpAllowlist,
        @JsonProperty("x_ai_gateway_allow_mcp_servers")
        Boolean allowMcpServers,
        @JsonProperty("max_tokens")
        @jakarta.validation.constraints.NotNull(message = "max_tokens 不能为空。")
        Integer maxTokens,
        Boolean stream
) {

    public record Message(
            @NotBlank(message = "role 不能为空。")
            String role,
            JsonNode content
    ) {
    }

    public record Tool(
            String name,
            String description,
            @JsonProperty("input_schema")
            JsonNode inputSchema
    ) {
    }
}
