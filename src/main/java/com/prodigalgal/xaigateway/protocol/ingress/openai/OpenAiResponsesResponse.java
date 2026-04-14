package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResponse;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolCall;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalUsage;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayResponse;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamResponse;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageView;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionStreamResponse;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionResponse;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall;
import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record OpenAiResponsesResponse(
        String id,
        String object,
        @JsonProperty("created_at")
        long createdAt,
        String status,
        String model,
        List<OutputItem> output,
        @JsonProperty("output_text")
        String outputText,
        Usage usage
) {

    public record OutputItem(
            String id,
            String type,
            String role,
            List<ContentItem> content,
            String name,
            String arguments,
            @JsonProperty("call_id")
            String callId,
            String status,
            List<SummaryItem> summary
    ) {
    }

    public record ContentItem(
            String type,
            String text
    ) {
    }

    public record SummaryItem(
            String type,
            String text
    ) {
    }

    public record Usage(
            @JsonProperty("input_tokens")
            int inputTokens,
            @JsonProperty("output_tokens")
            int outputTokens,
            @JsonProperty("total_tokens")
            int totalTokens,
            @JsonProperty("input_tokens_details")
            InputTokensDetails inputTokensDetails,
            @JsonProperty("output_tokens_details")
            OutputTokensDetails outputTokensDetails
    ) {
    }

    public record InputTokensDetails(
            @JsonProperty("cached_tokens")
            int cachedTokens
    ) {
    }

    public record OutputTokensDetails(
            @JsonProperty("reasoning_tokens")
            int reasoningTokens
    ) {
    }

    public static OpenAiResponsesResponse from(ChatExecutionResponse response) {
        return build(
                "resp-" + response.requestId(),
                "completed",
                response.routeSelection().publicModel(),
                response.text(),
                response.usage(),
                response.toolCalls(),
                response.reasoning()
        );
    }

    public static OpenAiResponsesResponse inProgress(ChatExecutionStreamResponse response) {
        return build(
                "resp-" + response.requestId(),
                "in_progress",
                response.routeSelection().publicModel(),
                null,
                null,
                List.of(),
                null
        );
    }

    public static OpenAiResponsesResponse from(GatewayResponse response) {
        return buildFromUsageView(
                "resp-" + response.requestId(),
                "completed",
                response.routeSelection().publicModel(),
                response.outputText(),
                response.usage(),
                response.toolCalls(),
                response.reasoning()
        );
    }

    public static OpenAiResponsesResponse fromCanonical(CanonicalResponse response) {
        return buildFromCanonical(
                "resp-" + response.requestId(),
                "completed",
                response.publicModel(),
                response.outputText(),
                response.usage(),
                response.toolCalls(),
                response.reasoning()
        );
    }

    public static OpenAiResponsesResponse inProgress(GatewayStreamResponse response) {
        return buildFromUsageView(
                "resp-" + response.requestId(),
                "in_progress",
                response.routeSelection().publicModel(),
                null,
                null,
                List.of(),
                null
        );
    }

    public static OpenAiResponsesResponse completed(
            ChatExecutionStreamResponse response,
            String text,
            GatewayUsage usage,
            String reasoning) {
        return build(
                "resp-" + response.requestId(),
                "completed",
                response.routeSelection().publicModel(),
                text,
                usage,
                List.of(),
                reasoning
        );
    }

    public static OpenAiResponsesResponse completed(
            GatewayStreamResponse response,
            String text,
            GatewayUsageView usage,
            String reasoning) {
        return buildFromUsageView(
                "resp-" + response.requestId(),
                "completed",
                response.routeSelection().publicModel(),
                text,
                usage,
                List.of(),
                reasoning
        );
    }

    public static OpenAiResponsesResponse completedCanonical(
            String requestId,
            String model,
            String text,
            CanonicalUsage usage,
            String reasoning) {
        return buildFromCanonical(
                "resp-" + requestId,
                "completed",
                model,
                text,
                usage,
                List.of(),
                reasoning
        );
    }

    private static OpenAiResponsesResponse build(
            String responseId,
            String status,
            String model,
            String text,
            GatewayUsage usage,
            List<GatewayToolCall> toolCalls,
            String reasoning) {
        Instant now = Instant.now();
        List<OutputItem> output = new ArrayList<>();

        if (reasoning != null && !reasoning.isBlank()) {
            output.add(new OutputItem(
                    "rs_" + now.toEpochMilli(),
                    "reasoning",
                    null,
                    null,
                    null,
                    null,
                    null,
                    status,
                    List.of(new SummaryItem("summary_text", reasoning))
            ));
        }

        if (text != null && !text.isBlank()) {
            output.add(new OutputItem(
                    "msg_" + now.toEpochMilli(),
                    "message",
                    "assistant",
                    List.of(new ContentItem("output_text", text)),
                    null,
                    null,
                    null,
                    status,
                    null
            ));
        }

        for (GatewayToolCall toolCall : toolCalls) {
            output.add(new OutputItem(
                    toolCall.id(),
                    "function_call",
                    null,
                    null,
                    toolCall.name(),
                    toolCall.arguments(),
                    toolCall.id(),
                    status,
                    null
            ));
        }

        return new OpenAiResponsesResponse(
                responseId,
                "response",
                now.getEpochSecond(),
                status,
                model,
                List.copyOf(output),
                text == null || text.isBlank() ? null : text,
                usage == null ? null : new Usage(
                        usage.promptTokens(),
                        usage.completionTokens(),
                        usage.totalTokens(),
                        new InputTokensDetails(usage.cacheHitTokens()),
                        new OutputTokensDetails(usage.reasoningTokens())
                )
        );
    }

    private static OpenAiResponsesResponse buildFromUsageView(
            String responseId,
            String status,
            String model,
            String text,
            GatewayUsageView usage,
            List<GatewayToolCall> toolCalls,
            String reasoning) {
        Instant now = Instant.now();
        List<OutputItem> output = new ArrayList<>();

        if (reasoning != null && !reasoning.isBlank()) {
            output.add(new OutputItem(
                    "rs_" + now.toEpochMilli(),
                    "reasoning",
                    null,
                    null,
                    null,
                    null,
                    null,
                    status,
                    List.of(new SummaryItem("summary_text", reasoning))
            ));
        }

        if (text != null && !text.isBlank()) {
            output.add(new OutputItem(
                    "msg_" + now.toEpochMilli(),
                    "message",
                    "assistant",
                    List.of(new ContentItem("output_text", text)),
                    null,
                    null,
                    null,
                    status,
                    null
            ));
        }

        for (GatewayToolCall toolCall : toolCalls) {
            output.add(new OutputItem(
                    toolCall.id(),
                    "function_call",
                    null,
                    null,
                    toolCall.name(),
                    toolCall.arguments(),
                    toolCall.id(),
                    status,
                    null
            ));
        }

        return new OpenAiResponsesResponse(
                responseId,
                "response",
                now.getEpochSecond(),
                status,
                model,
                List.copyOf(output),
                text == null || text.isBlank() ? null : text,
                usage == null || !usage.present() ? null : new Usage(
                        usage.promptTokens(),
                        usage.completionTokens(),
                        usage.totalTokens(),
                        new InputTokensDetails(usage.cacheHitTokens()),
                        new OutputTokensDetails(usage.reasoningTokens())
                )
        );
    }

    private static OpenAiResponsesResponse buildFromCanonical(
            String responseId,
            String status,
            String model,
            String text,
            CanonicalUsage usage,
            List<CanonicalToolCall> toolCalls,
            String reasoning) {
        Instant now = Instant.now();
        List<OutputItem> output = new ArrayList<>();

        if (reasoning != null && !reasoning.isBlank()) {
            output.add(new OutputItem(
                    "rs_" + now.toEpochMilli(),
                    "reasoning",
                    null,
                    null,
                    null,
                    null,
                    null,
                    status,
                    List.of(new SummaryItem("summary_text", reasoning))
            ));
        }

        if (text != null && !text.isBlank()) {
            output.add(new OutputItem(
                    "msg_" + now.toEpochMilli(),
                    "message",
                    "assistant",
                    List.of(new ContentItem("output_text", text)),
                    null,
                    null,
                    null,
                    status,
                    null
            ));
        }

        for (CanonicalToolCall toolCall : toolCalls) {
            output.add(new OutputItem(
                    toolCall.id(),
                    "function_call",
                    null,
                    null,
                    toolCall.name(),
                    toolCall.arguments(),
                    toolCall.id(),
                    status,
                    null
            ));
        }

        return new OpenAiResponsesResponse(
                responseId,
                "response",
                now.getEpochSecond(),
                status,
                model,
                List.copyOf(output),
                text == null || text.isBlank() ? null : text,
                usage == null || !usage.present() ? null : new Usage(
                        usage.promptTokens(),
                        usage.completionTokens(),
                        usage.totalTokens(),
                        new InputTokensDetails(usage.cacheHitTokens()),
                        new OutputTokensDetails(usage.reasoningTokens())
                )
        );
    }
}
