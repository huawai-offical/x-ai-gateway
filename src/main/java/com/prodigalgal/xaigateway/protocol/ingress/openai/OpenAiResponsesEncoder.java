package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayResponse;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamEventType;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class OpenAiResponsesEncoder {

    private final ObjectMapper objectMapper;

    public OpenAiResponsesEncoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OpenAiResponsesResponse encode(GatewayResponse response) {
        return OpenAiResponsesResponse.from(response);
    }

    public Flux<String> encodeStream(GatewayStreamResponse response) {
        String itemId = "msg_" + response.requestId();
        String reasoningItemId = "rs_" + response.requestId();
        AtomicBoolean reasoningStarted = new AtomicBoolean(false);
        AtomicBoolean messageOpened = new AtomicBoolean(true);

        return Flux.concat(
                Flux.just(
                        encodeEvent("response.created", createdEvent(response)),
                        encodeEvent("response.in_progress", inProgressEvent(response)),
                        encodeEvent("response.output_item.added", outputItemAddedEvent(itemId)),
                        encodeEvent("response.content_part.added", contentPartAddedEvent(itemId))
                ),
                response.events().concatMap(event -> encodeEvent(response, event, itemId, reasoningItemId, reasoningStarted, messageOpened))
        );
    }

    private Flux<String> encodeEvent(
            GatewayStreamResponse response,
            GatewayStreamEvent event,
            String itemId,
            String reasoningItemId,
            AtomicBoolean reasoningStarted,
            AtomicBoolean messageOpened) {
        List<String> events = new ArrayList<>();

        if (event.type() == GatewayStreamEventType.REASONING_DELTA && event.reasoningDelta() != null && !event.reasoningDelta().isBlank()) {
            if (reasoningStarted.compareAndSet(false, true)) {
                events.add(encodeEvent("response.output_item.added", reasoningItemAddedEvent(reasoningItemId)));
                events.add(encodeEvent("response.reasoning_summary_part.added", reasoningSummaryPartAddedEvent(reasoningItemId)));
            }
            events.add(encodeEvent("response.reasoning_summary_text.delta", reasoningSummaryTextDeltaEvent(reasoningItemId, event.reasoningDelta())));
        }

        if (event.type() == GatewayStreamEventType.TOOL_CALLS && event.toolCalls() != null && !event.toolCalls().isEmpty()) {
            int outputIndexBase = reasoningStarted.get() ? 2 : 1;
            for (int index = 0; index < event.toolCalls().size(); index++) {
                events.addAll(encodeToolCallEvents(event.toolCalls().get(index), outputIndexBase + index));
            }
        }

        if (event.type() == GatewayStreamEventType.TEXT_DELTA && event.textDelta() != null && !event.textDelta().isBlank()) {
            events.add(encodeEvent("response.output_text.delta", outputTextDeltaEvent(itemId, event.textDelta())));
        }

        if (event.type() != GatewayStreamEventType.COMPLETED) {
            return events.isEmpty() ? Flux.empty() : Flux.fromIterable(events);
        }

        String finalReasoning = event.reasoning();
        String finalText = event.outputText();
        if (reasoningStarted.get()) {
            events.add(encodeEvent("response.reasoning_summary_text.done", reasoningSummaryTextDoneEvent(reasoningItemId, finalReasoning == null ? "" : finalReasoning)));
            events.add(encodeEvent("response.reasoning_summary_part.done", reasoningSummaryPartDoneEvent(reasoningItemId, finalReasoning == null ? "" : finalReasoning)));
            events.add(encodeEvent("response.output_item.done", reasoningItemDoneEvent(reasoningItemId, finalReasoning == null ? "" : finalReasoning)));
        }
        if (messageOpened.get()) {
            events.add(encodeEvent("response.output_text.done", outputTextDoneEvent(itemId, finalText == null ? "" : finalText)));
            events.add(encodeEvent("response.content_part.done", contentPartDoneEvent(itemId, finalText == null ? "" : finalText)));
            events.add(encodeEvent("response.output_item.done", outputItemDoneEvent(itemId, finalText == null ? "" : finalText)));
        }
        events.add(encodeEvent("response.completed", completedEvent(response, finalText, event)));
        return Flux.fromIterable(events);
    }

    private Map<String, Object> createdEvent(GatewayStreamResponse response) {
        return Map.of("type", "response.created", "response", OpenAiResponsesResponse.inProgress(response));
    }

    private Map<String, Object> inProgressEvent(GatewayStreamResponse response) {
        return Map.of("type", "response.in_progress", "response", OpenAiResponsesResponse.inProgress(response));
    }

    private Map<String, Object> outputItemAddedEvent(String itemId) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", itemId);
        item.put("type", "message");
        item.put("status", "in_progress");
        item.put("role", "assistant");
        item.put("content", List.of());
        return Map.of("type", "response.output_item.added", "output_index", 0, "item", item);
    }

    private Map<String, Object> contentPartAddedEvent(String itemId) {
        return Map.of(
                "type", "response.content_part.added",
                "item_id", itemId,
                "output_index", 0,
                "content_index", 0,
                "part", Map.of("type", "output_text", "text", "", "annotations", List.of())
        );
    }

    private Map<String, Object> reasoningItemAddedEvent(String itemId) {
        return Map.of(
                "type", "response.output_item.added",
                "output_index", 1,
                "item", Map.of("id", itemId, "type", "reasoning", "status", "in_progress", "summary", List.of())
        );
    }

    private Map<String, Object> reasoningSummaryPartAddedEvent(String itemId) {
        return Map.of(
                "type", "response.reasoning_summary_part.added",
                "item_id", itemId,
                "output_index", 1,
                "summary_index", 0,
                "part", Map.of("type", "summary_text", "text", "")
        );
    }

    private Map<String, Object> reasoningSummaryTextDeltaEvent(String itemId, String delta) {
        return Map.of(
                "type", "response.reasoning_summary_text.delta",
                "item_id", itemId,
                "output_index", 1,
                "summary_index", 0,
                "delta", delta
        );
    }

    private Map<String, Object> reasoningSummaryTextDoneEvent(String itemId, String text) {
        return Map.of(
                "type", "response.reasoning_summary_text.done",
                "item_id", itemId,
                "output_index", 1,
                "summary_index", 0,
                "text", text
        );
    }

    private Map<String, Object> reasoningSummaryPartDoneEvent(String itemId, String text) {
        return Map.of(
                "type", "response.reasoning_summary_part.done",
                "item_id", itemId,
                "output_index", 1,
                "summary_index", 0,
                "part", Map.of("type", "summary_text", "text", text)
        );
    }

    private Map<String, Object> reasoningItemDoneEvent(String itemId, String text) {
        return Map.of(
                "type", "response.output_item.done",
                "output_index", 1,
                "item", Map.of(
                        "id", itemId,
                        "type", "reasoning",
                        "status", "completed",
                        "summary", List.of(Map.of("type", "summary_text", "text", text))
                )
        );
    }

    private Map<String, Object> outputTextDeltaEvent(String itemId, String delta) {
        return Map.of(
                "type", "response.output_text.delta",
                "item_id", itemId,
                "output_index", 0,
                "content_index", 0,
                "delta", delta
        );
    }

    private Map<String, Object> outputTextDoneEvent(String itemId, String text) {
        return Map.of(
                "type", "response.output_text.done",
                "item_id", itemId,
                "output_index", 0,
                "content_index", 0,
                "text", text
        );
    }

    private Map<String, Object> contentPartDoneEvent(String itemId, String text) {
        return Map.of(
                "type", "response.content_part.done",
                "item_id", itemId,
                "output_index", 0,
                "content_index", 0,
                "part", Map.of("type", "output_text", "text", text, "annotations", List.of())
        );
    }

    private Map<String, Object> outputItemDoneEvent(String itemId, String text) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", itemId);
        item.put("type", "message");
        item.put("status", "completed");
        item.put("role", "assistant");
        item.put("content", List.of(Map.of("type", "output_text", "text", text, "annotations", List.of())));
        return Map.of("type", "response.output_item.done", "output_index", 0, "item", item);
    }

    private List<String> encodeToolCallEvents(GatewayToolCall toolCall, int outputIndex) {
        String itemId = toolCall.id() == null || toolCall.id().isBlank() ? "fc_" + outputIndex : toolCall.id();
        List<String> events = new ArrayList<>();
        events.add(encodeEvent("response.output_item.added", functionCallAddedEvent(itemId, outputIndex, toolCall)));
        if (toolCall.arguments() != null && !toolCall.arguments().isBlank()) {
            events.add(encodeEvent("response.function_call_arguments.delta", functionCallArgumentsDeltaEvent(itemId, outputIndex, toolCall.arguments())));
            events.add(encodeEvent("response.function_call_arguments.done", functionCallArgumentsDoneEvent(itemId, outputIndex, toolCall.arguments())));
        }
        events.add(encodeEvent("response.output_item.done", functionCallDoneEvent(itemId, outputIndex, toolCall)));
        return events;
    }

    private Map<String, Object> functionCallAddedEvent(String itemId, int outputIndex, GatewayToolCall toolCall) {
        return Map.of(
                "type", "response.output_item.added",
                "output_index", outputIndex,
                "item", Map.of(
                        "id", itemId,
                        "type", "function_call",
                        "call_id", itemId,
                        "name", toolCall.name(),
                        "arguments", "",
                        "status", "in_progress"
                )
        );
    }

    private Map<String, Object> functionCallArgumentsDeltaEvent(String itemId, int outputIndex, String arguments) {
        return Map.of("type", "response.function_call_arguments.delta", "item_id", itemId, "output_index", outputIndex, "delta", arguments);
    }

    private Map<String, Object> functionCallArgumentsDoneEvent(String itemId, int outputIndex, String arguments) {
        return Map.of("type", "response.function_call_arguments.done", "item_id", itemId, "output_index", outputIndex, "arguments", arguments);
    }

    private Map<String, Object> functionCallDoneEvent(String itemId, int outputIndex, GatewayToolCall toolCall) {
        return Map.of(
                "type", "response.output_item.done",
                "output_index", outputIndex,
                "item", Map.of(
                        "id", itemId,
                        "type", "function_call",
                        "call_id", itemId,
                        "name", toolCall.name(),
                        "arguments", toolCall.arguments() == null ? "" : toolCall.arguments(),
                        "status", "completed"
                )
        );
    }

    private Map<String, Object> completedEvent(GatewayStreamResponse response, String text, GatewayStreamEvent event) {
        return Map.of(
                "type", "response.completed",
                "response", OpenAiResponsesResponse.completed(response, text, event.usage(), event.reasoning())
        );
    }

    private String encodeEvent(String eventName, Object payload) {
        try {
            return "event: " + eventName + "\n" + "data: " + objectMapper.writeValueAsString(payload) + "\n\n";
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法序列化 Responses stream 响应。", exception);
        }
    }
}
