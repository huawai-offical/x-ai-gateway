package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResponse;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolCall;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalUsage;
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

    public static OpenAiChatCompletionResponse fromCanonical(CanonicalResponse response) {
        return new OpenAiChatCompletionResponse(
                "chatcmpl-" + Instant.now().toEpochMilli(),
                "chat.completion",
                Instant.now().getEpochSecond(),
                response.publicModel(),
                List.of(new Choice(
                        0,
                        new Message("assistant", response.outputText(), toToolCallsCanonical(response.toolCalls())),
                        response.toolCalls() != null && !response.toolCalls().isEmpty() ? "tool_calls" : "stop"
                )),
                toUsage(response.usage())
        );
    }

    private static List<ToolCall> toToolCallsCanonical(List<CanonicalToolCall> toolCalls) {
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
            List<ChunkChoice> choices,
            Usage usage
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
        return roleChunk("chatcmpl-" + Instant.now().toEpochMilli(), Instant.now().getEpochSecond(), model);
    }

    public static Chunk roleChunk(String id, long created, String model) {
        return new Chunk(
                id,
                "chat.completion.chunk",
                created,
                model,
                List.of(new ChunkChoice(0, new Delta("assistant", null, null), null)),
                null
        );
    }

    public static Chunk contentChunk(String model, String textDelta) {
        return contentChunk("chatcmpl-" + Instant.now().toEpochMilli(), Instant.now().getEpochSecond(), model, textDelta);
    }

    public static Chunk contentChunk(String id, long created, String model, String textDelta) {
        return new Chunk(
                id,
                "chat.completion.chunk",
                created,
                model,
                List.of(new ChunkChoice(0, new Delta(null, textDelta, null), null)),
                null
        );
    }

    public static Chunk toolCallChunkCanonical(String model, List<CanonicalToolCall> toolCalls) {
        return toolCallChunkCanonical("chatcmpl-" + Instant.now().toEpochMilli(), Instant.now().getEpochSecond(), model, toolCalls);
    }

    public static Chunk toolCallChunkCanonical(String id, long created, String model, List<CanonicalToolCall> toolCalls) {
        return new Chunk(
                id,
                "chat.completion.chunk",
                created,
                model,
                List.of(new ChunkChoice(0, new Delta(null, null, toToolCallsCanonical(toolCalls)), null)),
                null
        );
    }

    public static Chunk finishChunk(String model, String finishReason) {
        return finishChunk("chatcmpl-" + Instant.now().toEpochMilli(), Instant.now().getEpochSecond(), model, finishReason);
    }

    public static Chunk finishChunk(String id, long created, String model, String finishReason) {
        return new Chunk(
                id,
                "chat.completion.chunk",
                created,
                model,
                List.of(new ChunkChoice(0, new Delta(null, null, null), finishReason)),
                null
        );
    }

    public static Chunk usageChunk(String id, long created, String model, CanonicalUsage usage) {
        return new Chunk(
                id,
                "chat.completion.chunk",
                created,
                model,
                List.of(),
                toUsage(usage)
        );
    }

    private static Usage toUsage(CanonicalUsage usage) {
        if (usage == null || !usage.present()) {
            return null;
        }
        return new Usage(
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens(),
                new PromptTokensDetails(usage.cacheHitTokens()),
                new CompletionTokensDetails(usage.reasoningTokens())
        );
    }
}
