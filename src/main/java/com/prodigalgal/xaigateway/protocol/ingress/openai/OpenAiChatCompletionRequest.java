package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record OpenAiChatCompletionRequest(
        @NotBlank(message = "model 不能为空。")
        String model,
        @Valid
        List<Message> messages,
        List<Tool> tools,
        @JsonProperty("tool_choice")
        JsonNode toolChoice,
        JsonNode reasoning,
        @JsonProperty("reasoning_effort")
        String reasoningEffort,
        Double temperature,
        @JsonProperty("max_tokens")
        Integer maxTokens,
        Boolean stream
) {

    public record Message(
            @NotBlank(message = "role 不能为空。")
            String role,
            JsonNode content,
            @JsonProperty("tool_call_id")
            String toolCallId
    ) {
    }

    public record Tool(
            String type,
            @Valid
            Function function
    ) {
    }

    public record Function(
            @NotBlank(message = "tool function name 不能为空。")
            String name,
            String description,
            JsonNode parameters,
            Boolean strict
    ) {
    }
}
