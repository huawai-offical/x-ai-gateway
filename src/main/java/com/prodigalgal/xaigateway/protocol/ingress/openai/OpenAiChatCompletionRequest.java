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
        Boolean store,
        JsonNode metadata,
        @JsonProperty("frequency_penalty")
        Double frequencyPenalty,
        @JsonProperty("logit_bias")
        JsonNode logitBias,
        Boolean logprobs,
        @JsonProperty("top_logprobs")
        Integer topLogprobs,
        Double temperature,
        @JsonProperty("top_p")
        Double topP,
        @JsonProperty("max_tokens")
        Integer maxTokens,
        @JsonProperty("max_completion_tokens")
        Integer maxCompletionTokens,
        Integer n,
        JsonNode modalities,
        JsonNode audio,
        @JsonProperty("presence_penalty")
        Double presencePenalty,
        @JsonProperty("response_format")
        JsonNode responseFormat,
        Integer seed,
        @JsonProperty("service_tier")
        String serviceTier,
        JsonNode stop,
        @JsonProperty("stream_options")
        JsonNode streamOptions,
        @JsonProperty("parallel_tool_calls")
        Boolean parallelToolCalls,
        String user,
        @JsonProperty("web_search_options")
        JsonNode webSearchOptions,
        String verbosity,
        @JsonProperty("prompt_cache_key")
        String promptCacheKey,
        @JsonProperty("safety_identifier")
        String safetyIdentifier,
        JsonNode prediction,
        JsonNode functions,
        @JsonProperty("function_call")
        JsonNode functionCall,
        Boolean stream
) {
    public OpenAiChatCompletionRequest(
            String model,
            List<Message> messages,
            List<Tool> tools,
            JsonNode toolChoice,
            JsonNode reasoning,
            String reasoningEffort,
            Double temperature,
            Integer maxTokens,
            Boolean stream) {
        this(
                model,
                messages,
                tools,
                toolChoice,
                reasoning,
                reasoningEffort,
                null,
                null,
                null,
                null,
                null,
                null,
                temperature,
                null,
                maxTokens,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                stream
        );
    }

    public record Message(
            @NotBlank(message = "role 不能为空。")
            String role,
            JsonNode content,
            @JsonProperty("tool_call_id")
            String toolCallId,
            @JsonProperty("tool_calls")
            JsonNode toolCalls,
            @JsonProperty("reasoning_content")
            String reasoningContent
    ) {
        public Message(String role, JsonNode content, String toolCallId) {
            this(role, content, toolCallId, null, null);
        }
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
