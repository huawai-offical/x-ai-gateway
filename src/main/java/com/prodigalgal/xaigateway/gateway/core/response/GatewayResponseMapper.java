package com.prodigalgal.xaigateway.gateway.core.response;

import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionResponse;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionStreamChunk;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionStreamResponse;
import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class GatewayResponseMapper {

    public GatewayResponse toGatewayResponse(ChatExecutionResponse response) {
        return new GatewayResponse(
                response.requestId(),
                response.routeSelection(),
                response.text(),
                toUsageView(response.usage(), GatewayUsageCompleteness.FINAL, GatewayUsageSource.DIRECT_RESPONSE),
                response.toolCalls(),
                response.reasoning(),
                GatewayFinishReason.fromRaw(response.finishReason()),
                null
        );
    }

    public GatewayStreamResponse toGatewayStreamResponse(ChatExecutionStreamResponse response) {
        AtomicReference<GatewayUsage> lastVisibleUsage = new AtomicReference<>(GatewayUsage.empty());
        AtomicReference<StringBuilder> textBuffer = new AtomicReference<>(new StringBuilder());
        AtomicReference<StringBuilder> reasoningBuffer = new AtomicReference<>(new StringBuilder());
        AtomicBoolean terminalSeen = new AtomicBoolean(false);

        Flux<GatewayStreamEvent> events = response.chunks()
                .concatMap(chunk -> toEvents(
                        chunk,
                        lastVisibleUsage,
                        textBuffer,
                        reasoningBuffer,
                        terminalSeen
                ))
                .concatWith(Mono.defer(() -> terminalSeen.get()
                        ? Mono.empty()
                        : Mono.just(completedEvent(
                        lastVisibleUsage.get(),
                        GatewayUsageSource.LAST_VISIBLE,
                        GatewayFinishReason.STOP,
                        textBuffer.get().toString(),
                        reasoningBuffer.get().toString()
                ))));

        return new GatewayStreamResponse(response.requestId(), response.routeSelection(), events);
    }

    public GatewayUsageView toUsageView(
            GatewayUsage usage,
            GatewayUsageCompleteness completeness,
            GatewayUsageSource source) {
        if (usage == null || usage.isEmpty()) {
            return GatewayUsageView.empty();
        }
        return new GatewayUsageView(
                usage.rawPromptTokens(),
                usage.promptTokens(),
                usage.completionTokens(),
                usage.reasoningTokens(),
                usage.cacheHitTokens(),
                usage.cacheWriteTokens(),
                usage.upstreamCacheHitTokens(),
                usage.upstreamCacheWriteTokens(),
                usage.savedInputTokens(),
                usage.cachedContentRef(),
                usage.totalTokens(),
                completeness,
                source,
                usage.nativeUsagePayload()
        );
    }

    private Flux<GatewayStreamEvent> toEvents(
            ChatExecutionStreamChunk chunk,
            AtomicReference<GatewayUsage> lastVisibleUsage,
            AtomicReference<StringBuilder> textBuffer,
            AtomicReference<StringBuilder> reasoningBuffer,
            AtomicBoolean terminalSeen) {
        GatewayUsage usage = chunk.usage();
        if (usage != null && !usage.isEmpty()) {
            lastVisibleUsage.set(usage);
        }

        Flux<GatewayStreamEvent> emitted = Flux.empty();
        if (chunk.textDelta() != null && !chunk.textDelta().isBlank()) {
            textBuffer.get().append(chunk.textDelta());
            emitted = emitted.concatWithValues(new GatewayStreamEvent(
                    GatewayStreamEventType.TEXT_DELTA,
                    chunk.textDelta(),
                    null,
                    List.of(),
                    GatewayUsageView.empty(),
                    false,
                    null,
                    null,
                    null,
                    null
            ));
        }
        if (chunk.reasoningDelta() != null && !chunk.reasoningDelta().isBlank()) {
            reasoningBuffer.get().append(chunk.reasoningDelta());
            emitted = emitted.concatWithValues(new GatewayStreamEvent(
                    GatewayStreamEventType.REASONING_DELTA,
                    null,
                    chunk.reasoningDelta(),
                    List.of(),
                    GatewayUsageView.empty(),
                    false,
                    null,
                    null,
                    null,
                    null
            ));
        }
        if (chunk.toolCalls() != null && !chunk.toolCalls().isEmpty()) {
            emitted = emitted.concatWithValues(new GatewayStreamEvent(
                    GatewayStreamEventType.TOOL_CALLS,
                    null,
                    null,
                    chunk.toolCalls(),
                    GatewayUsageView.empty(),
                    false,
                    null,
                    null,
                    null,
                    null
            ));
        }

        if (!chunk.terminal()) {
            return emitted;
        }

        terminalSeen.set(true);
        GatewayUsage terminalUsage = usage != null && !usage.isEmpty() ? usage : lastVisibleUsage.get();
        GatewayUsageSource source = usage != null && !usage.isEmpty()
                ? GatewayUsageSource.PROVIDER_FINAL
                : GatewayUsageSource.LAST_VISIBLE;
        GatewayUsageCompleteness completeness = usage != null && !usage.isEmpty()
                ? GatewayUsageCompleteness.FINAL
                : lastVisibleUsage.get() != null && !lastVisibleUsage.get().isEmpty()
                ? GatewayUsageCompleteness.PARTIAL
                : GatewayUsageCompleteness.NONE;

        return emitted.concatWithValues(new GatewayStreamEvent(
                GatewayStreamEventType.COMPLETED,
                null,
                null,
                List.of(),
                toUsageView(terminalUsage, completeness, source),
                true,
                GatewayFinishReason.fromRaw(chunk.finishReason()),
                textBuffer.get().toString(),
                reasoningBuffer.get().toString(),
                null
        ));
    }

    private GatewayStreamEvent completedEvent(
            GatewayUsage usage,
            GatewayUsageSource source,
            GatewayFinishReason finishReason,
            String outputText,
            String reasoning) {
        GatewayUsageCompleteness completeness = usage == null || usage.isEmpty()
                ? GatewayUsageCompleteness.NONE
                : GatewayUsageSource.LAST_VISIBLE == source
                ? GatewayUsageCompleteness.PARTIAL
                : GatewayUsageCompleteness.FINAL;
        return new GatewayStreamEvent(
                GatewayStreamEventType.COMPLETED,
                null,
                null,
                List.of(),
                toUsageView(usage, completeness, source),
                true,
                finishReason,
                outputText,
                reasoning,
                null
        );
    }
}
