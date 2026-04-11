package com.prodigalgal.xaigateway.protocol.ingress.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
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
        JsonNode toolChoice,
        Double temperature,
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
            JsonNode inputSchema
    ) {
    }
}
