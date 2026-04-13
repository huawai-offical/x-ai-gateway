package com.prodigalgal.xaigateway.protocol.ingress.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayResponse;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageView;
import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import java.time.Instant;
import java.util.List;

public record AnthropicMessagesResponse(
        String id,
        String type,
        String role,
        String model,
        List<ContentBlock> content,
        @JsonProperty("stop_reason")
        String stopReason,
        @JsonProperty("stop_sequence")
        String stopSequence,
        Usage usage
) {

    public record ContentBlock(
            String type,
            String text,
            String id,
            String name,
            JsonNode input
    ) {
    }

    public record Usage(
            @JsonProperty("input_tokens")
            int inputTokens,
            @JsonProperty("output_tokens")
            int outputTokens,
            @JsonProperty("cache_creation_input_tokens")
            int cacheCreationInputTokens,
            @JsonProperty("cache_read_input_tokens")
            int cacheReadInputTokens
    ) {
    }

    public static AnthropicMessagesResponse from(
            String model,
            String text,
            GatewayUsage usage,
            List<GatewayToolCall> toolCalls) {
        return new AnthropicMessagesResponse(
                "msg_" + Instant.now().toEpochMilli(),
                "message",
                "assistant",
                model,
                toContentBlocks(text, toolCalls),
                toolCalls != null && !toolCalls.isEmpty() ? "tool_use" : "end_turn",
                null,
                new Usage(
                        usage.promptTokens(),
                        usage.completionTokens(),
                        usage.cacheWriteTokens(),
                        usage.cacheHitTokens()
                )
        );
    }

    public static AnthropicMessagesResponse from(GatewayResponse response) {
        return new AnthropicMessagesResponse(
                "msg_" + Instant.now().toEpochMilli(),
                "message",
                "assistant",
                response.routeSelection().publicModel(),
                toContentBlocks(response.outputText(), response.toolCalls()),
                response.toolCalls() != null && !response.toolCalls().isEmpty() ? "tool_use" : "end_turn",
                null,
                new Usage(
                        response.usage().promptTokens(),
                        response.usage().completionTokens(),
                        response.usage().cacheWriteTokens(),
                        response.usage().cacheHitTokens()
                )
        );
    }

    private static List<ContentBlock> toContentBlocks(String text, List<GatewayToolCall> toolCalls) {
        if (toolCalls != null && !toolCalls.isEmpty()) {
            ObjectMapper mapper = new ObjectMapper();
            return toolCalls.stream()
                    .map(toolCall -> new ContentBlock(
                            "tool_use",
                            null,
                            toolCall.id(),
                            toolCall.name(),
                            parseArguments(mapper, toolCall.arguments())
                    ))
                    .toList();
        }

        return List.of(new ContentBlock("text", text, null, null, null));
    }

    private static JsonNode parseArguments(ObjectMapper mapper, String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return mapper.createObjectNode();
        }

        try {
            return mapper.readTree(arguments);
        } catch (Exception ignored) {
            return mapper.createObjectNode().put("raw", arguments);
        }
    }

    public record MessageStart(
            String type,
            Message message
    ) {
    }

    public record Message(
            String id,
            String type,
            String role,
            String model,
            List<ContentBlock> content,
            @JsonProperty("stop_reason")
            String stopReason,
            @JsonProperty("stop_sequence")
            String stopSequence,
            Usage usage
    ) {
    }

    public record ContentBlockStart(
            String type,
            int index,
            @JsonProperty("content_block")
            ContentBlock contentBlock
    ) {
    }

    public record ContentBlockDelta(
            String type,
            int index,
            Delta delta
    ) {
    }

    public record Delta(
            String type,
            String text
    ) {
    }

    public record ContentBlockStop(
            String type,
            int index
    ) {
    }

    public record MessageDelta(
            String type,
            MessageDeltaContent delta,
            Usage usage
    ) {
    }

    public record MessageDeltaContent(
            @JsonProperty("stop_reason")
            String stopReason,
            @JsonProperty("stop_sequence")
            String stopSequence
    ) {
    }

    public record MessageStop(
            String type
    ) {
    }

    public static MessageStart messageStart(String model, GatewayUsage usage) {
        return new MessageStart(
                "message_start",
                new Message(
                        "msg_" + Instant.now().toEpochMilli(),
                        "message",
                        "assistant",
                        model,
                        List.of(),
                        null,
                        null,
                        new Usage(
                                usage.promptTokens(),
                                0,
                                usage.cacheWriteTokens(),
                                usage.cacheHitTokens()
                        )
                )
        );
    }

    public static ContentBlockStart contentBlockStart() {
        return new ContentBlockStart("content_block_start", 0, new ContentBlock("text", "", null, null, null));
    }

    public static ContentBlockDelta contentBlockDelta(String text) {
        return new ContentBlockDelta("content_block_delta", 0, new Delta("text_delta", text));
    }

    public static ContentBlockStop contentBlockStop() {
        return new ContentBlockStop("content_block_stop", 0);
    }

    public static MessageDelta messageDelta(GatewayUsage usage, String stopReason) {
        return new MessageDelta(
                "message_delta",
                new MessageDeltaContent(stopReason, null),
                new Usage(
                        usage.promptTokens(),
                        usage.completionTokens(),
                        usage.cacheWriteTokens(),
                        usage.cacheHitTokens()
                )
        );
    }

    public static MessageDelta messageDelta(GatewayUsageView usage, String stopReason) {
        return new MessageDelta(
                "message_delta",
                new MessageDeltaContent(stopReason, null),
                new Usage(
                        usage.promptTokens(),
                        usage.completionTokens(),
                        usage.cacheWriteTokens(),
                        usage.cacheHitTokens()
                )
        );
    }

    public static MessageStop messageStop() {
        return new MessageStop("message_stop");
    }
}
