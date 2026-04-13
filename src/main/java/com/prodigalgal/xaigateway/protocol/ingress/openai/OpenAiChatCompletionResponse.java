package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayResponse;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageView;
import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import java.time.Instant;
import java.util.List;

public record OpenAiChatCompletionResponse(
        String id,
        String object,
        long created,
        String model,
        List<Choice> choices,
        Usage usage
) {

    public record Choice(
            int index,
            Message message,
            @JsonProperty("finish_reason")
            String finishReason
    ) {
    }

    public record Message(
            String role,
            String content,
            @JsonProperty("tool_calls")
            List<ToolCall> toolCalls
    ) {
    }

    public record ToolCall(
            String id,
            String type,
            Function function
    ) {
    }

    public record Function(
            String name,
            String arguments
    ) {
    }

    public record Usage(
            @JsonProperty("prompt_tokens")
            int promptTokens,
            @JsonProperty("completion_tokens")
            int completionTokens,
            @JsonProperty("total_tokens")
            int totalTokens,
            @JsonProperty("prompt_tokens_details")
            PromptTokensDetails promptTokensDetails,
            @JsonProperty("completion_tokens_details")
            CompletionTokensDetails completionTokensDetails
    ) {
    }

    public record PromptTokensDetails(
            @JsonProperty("cached_tokens")
            int cachedTokens
    ) {
    }

    public record CompletionTokensDetails(
            @JsonProperty("reasoning_tokens")
            int reasoningTokens
    ) {
    }

    public static OpenAiChatCompletionResponse from(
            String model,
            String text,
            GatewayUsage usage,
            List<GatewayToolCall> toolCalls) {
        return new OpenAiChatCompletionResponse(
                "chatcmpl-" + Instant.now().toEpochMilli(),
                "chat.completion",
                Instant.now().getEpochSecond(),
                model,
                List.of(new Choice(
                        0,
                        new Message("assistant", text, toToolCalls(toolCalls)),
                        toolCalls != null && !toolCalls.isEmpty() ? "tool_calls" : "stop"
                )),
                new Usage(
                        usage.promptTokens(),
                        usage.completionTokens(),
                        usage.totalTokens(),
                        new PromptTokensDetails(usage.cacheHitTokens()),
                        new CompletionTokensDetails(usage.reasoningTokens())
                )
        );
    }

    public static OpenAiChatCompletionResponse from(GatewayResponse response) {
        return new OpenAiChatCompletionResponse(
                "chatcmpl-" + Instant.now().toEpochMilli(),
                "chat.completion",
                Instant.now().getEpochSecond(),
                response.routeSelection().publicModel(),
                List.of(new Choice(
                        0,
                        new Message("assistant", response.outputText(), toToolCalls(response.toolCalls())),
                        response.toolCalls() != null && !response.toolCalls().isEmpty() ? "tool_calls" : "stop"
                )),
                toUsage(response.usage())
        );
    }

    private static List<ToolCall> toToolCalls(List<GatewayToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return null;
        }

        return toolCalls.stream()
                .map(toolCall -> new ToolCall(
                        toolCall.id(),
                        toolCall.type() == null ? "function" : toolCall.type(),
                        new Function(toolCall.name(), toolCall.arguments())
                ))
                .toList();
    }

    public record Chunk(
            String id,
            String object,
            long created,
            String model,
            List<ChunkChoice> choices
    ) {
    }

    public record ChunkChoice(
            int index,
            Delta delta,
            @JsonProperty("finish_reason")
            String finishReason
    ) {
    }

    public record Delta(
            String role,
            String content,
            @JsonProperty("tool_calls")
            List<ToolCall> toolCalls
    ) {
    }

    public static Chunk roleChunk(String model) {
        return new Chunk(
                "chatcmpl-" + Instant.now().toEpochMilli(),
                "chat.completion.chunk",
                Instant.now().getEpochSecond(),
                model,
                List.of(new ChunkChoice(0, new Delta("assistant", null, null), null))
        );
    }

    public static Chunk contentChunk(String model, String textDelta) {
        return new Chunk(
                "chatcmpl-" + Instant.now().toEpochMilli(),
                "chat.completion.chunk",
                Instant.now().getEpochSecond(),
                model,
                List.of(new ChunkChoice(0, new Delta(null, textDelta, null), null))
        );
    }

    public static Chunk toolCallChunk(String model, List<GatewayToolCall> toolCalls) {
        return new Chunk(
                "chatcmpl-" + Instant.now().toEpochMilli(),
                "chat.completion.chunk",
                Instant.now().getEpochSecond(),
                model,
                List.of(new ChunkChoice(0, new Delta(null, null, toToolCalls(toolCalls)), null))
        );
    }

    public static Chunk finishChunk(String model, String finishReason) {
        return new Chunk(
                "chatcmpl-" + Instant.now().toEpochMilli(),
                "chat.completion.chunk",
                Instant.now().getEpochSecond(),
                model,
                List.of(new ChunkChoice(0, new Delta(null, null, null), finishReason))
        );
    }

    private static Usage toUsage(GatewayUsageView usage) {
        return new Usage(
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens(),
                new PromptTokensDetails(usage.cacheHitTokens()),
                new CompletionTokensDetails(usage.reasoningTokens())
        );
    }
}
