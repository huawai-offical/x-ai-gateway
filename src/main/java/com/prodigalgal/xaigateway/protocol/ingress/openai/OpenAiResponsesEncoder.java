package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionResult;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionStreamResult;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResponse;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEventType;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolCall;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class OpenAiResponsesEncoder {

    private final ObjectMapper objectMapper;

    public OpenAiResponsesEncoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OpenAiResponsesResponse encode(CanonicalExecutionResult response) {
        return OpenAiResponsesResponse.fromCanonical(response.response());
    }

    public JsonNode encodeJson(CanonicalExecutionResult response) {
        JsonNode raw = response == null || response.response() == null ? null : response.response().rawResponse();
        if (raw != null && raw.isObject()) {
            ObjectNode copy = ((ObjectNode) raw).deepCopy();
            String publicModel = response.routeSelection() == null ? response.response().publicModel() : response.routeSelection().publicModel();
            if (publicModel != null && !publicModel.isBlank()) {
                copy.put("model", publicModel);
            }
            return copy;
        }
        return objectMapper.valueToTree(encode(response));
    }

    public Flux<String> encodeStream(CanonicalExecutionStreamResult response) {
        return encodeStream(response, null);
    }

    public Flux<String> encodeStream(CanonicalExecutionStreamResult response, JsonNode streamOptions) {
        Flux<CanonicalStreamEvent> events = response.events() == null ? Flux.empty() : response.events();
        return events.switchOnFirst((signal, stream) -> {
            if (signal.hasValue() && isRawSseEvent(signal.get())) {
                return stream
                        .map(CanonicalStreamEvent::rawSsePayload)
                        .filter(Objects::nonNull);
            }
            return encodeCanonicalStream(response, streamOptions, stream);
        });
    }

    private Flux<String> encodeCanonicalStream(
            CanonicalExecutionStreamResult response,
            JsonNode streamOptions,
            Flux<CanonicalStreamEvent> events) {
        String itemId = "msg_" + response.requestId();
        String reasoningItemId = "rs_" + response.requestId();
        AtomicBoolean reasoningStarted = new AtomicBoolean(false);
        AtomicBoolean messageOpened = new AtomicBoolean(true);
        AtomicInteger sequenceNumber = new AtomicInteger();
        boolean includeObfuscation = includeObfuscation(streamOptions);

        return Flux.concat(
                Flux.just(
                        encodeEvent(sequenceNumber, "response.created", Map.of(
                                "type", "response.created",
                                "response", OpenAiResponsesResponse.completedCanonical(
                                        response.requestId(),
                                        response.routeSelection().publicModel(),
                                        null,
                                        com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalUsage.empty(),
                                        null
                                )
                        )),
                        encodeEvent(sequenceNumber, "response.in_progress", Map.of(
                                "type", "response.in_progress",
                                "response", OpenAiResponsesResponse.completedCanonical(
                                        response.requestId(),
                                        response.routeSelection().publicModel(),
                                        null,
                                        com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalUsage.empty(),
                                        null
                                )
                        )),
                        encodeEvent(sequenceNumber, "response.output_item.added", outputItemAddedEvent(itemId)),
                        encodeEvent(sequenceNumber, "response.content_part.added", contentPartAddedEvent(itemId))
                ),
                response.events().concatMap(event -> encodeCanonicalEvent(
                        response.requestId(),
                        response.routeSelection().publicModel(),
                        event,
                        itemId,
                        reasoningItemId,
                        reasoningStarted,
                        messageOpened,
                        sequenceNumber,
                        includeObfuscation
                ))
        );
    }

    private boolean isRawSseEvent(CanonicalStreamEvent event) {
        return event != null
                && event.type() == CanonicalStreamEventType.RAW_SSE
                && event.rawSsePayload() != null;
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

    private Map<String, Object> reasoningSummaryTextDeltaEvent(String itemId, String delta, boolean includeObfuscation) {
        return withObfuscation(Map.of(
                "type", "response.reasoning_summary_text.delta",
                "item_id", itemId,
                "output_index", 1,
                "summary_index", 0,
                "delta", delta
        ), includeObfuscation);
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

    private Map<String, Object> outputTextDeltaEvent(String itemId, String delta, boolean includeObfuscation) {
        return withObfuscation(Map.of(
                "type", "response.output_text.delta",
                "item_id", itemId,
                "output_index", 0,
                "content_index", 0,
                "delta", delta
        ), includeObfuscation);
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

    private List<String> encodeToolCallEvents(
            CanonicalToolCall toolCall,
            int outputIndex,
            AtomicInteger sequenceNumber,
            boolean includeObfuscation) {
        String itemId = toolCall.id() == null || toolCall.id().isBlank() ? "fc_" + outputIndex : toolCall.id();
        List<String> events = new ArrayList<>();
        events.add(encodeEvent(sequenceNumber, "response.output_item.added", functionCallAddedEvent(itemId, outputIndex, toolCall)));
        if (toolCall.arguments() != null && !toolCall.arguments().isBlank()) {
            events.add(encodeEvent(sequenceNumber, "response.function_call_arguments.delta", functionCallArgumentsDeltaEvent(itemId, outputIndex, toolCall.arguments(), includeObfuscation)));
            events.add(encodeEvent(sequenceNumber, "response.function_call_arguments.done", functionCallArgumentsDoneEvent(itemId, outputIndex, toolCall.arguments())));
        }
        events.add(encodeEvent(sequenceNumber, "response.output_item.done", functionCallDoneEvent(itemId, outputIndex, toolCall)));
        return events;
    }

    private Map<String, Object> functionCallAddedEvent(String itemId, int outputIndex, CanonicalToolCall toolCall) {
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

    private Map<String, Object> functionCallArgumentsDeltaEvent(String itemId, int outputIndex, String arguments, boolean includeObfuscation) {
        return withObfuscation(
                Map.of("type", "response.function_call_arguments.delta", "item_id", itemId, "output_index", outputIndex, "delta", arguments),
                includeObfuscation
        );
    }

    private Map<String, Object> functionCallArgumentsDoneEvent(String itemId, int outputIndex, String arguments) {
        return Map.of("type", "response.function_call_arguments.done", "item_id", itemId, "output_index", outputIndex, "arguments", arguments);
    }

    private Map<String, Object> functionCallDoneEvent(String itemId, int outputIndex, CanonicalToolCall toolCall) {
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

    private Map<String, Object> completedEvent(String requestId, String publicModel, CanonicalStreamEvent event) {
        return Map.of(
                "type", "response.completed",
                "response", OpenAiResponsesResponse.completedCanonical(
                        requestId,
                        publicModel,
                        event.outputText(),
                        event.usage(),
                        event.reasoning()
                )
        );
    }

    private Flux<String> encodeCanonicalEvent(
            String requestId,
            String publicModel,
            CanonicalStreamEvent canonicalEvent,
            String itemId,
            String reasoningItemId,
            AtomicBoolean reasoningStarted,
            AtomicBoolean messageOpened,
            AtomicInteger sequenceNumber,
            boolean includeObfuscation) {
        List<String> events = new ArrayList<>();

        if (canonicalEvent.type() == CanonicalStreamEventType.REASONING_DELTA && canonicalEvent.reasoningDelta() != null && !canonicalEvent.reasoningDelta().isBlank()) {
            if (reasoningStarted.compareAndSet(false, true)) {
                events.add(encodeEvent(sequenceNumber, "response.output_item.added", reasoningItemAddedEvent(reasoningItemId)));
                events.add(encodeEvent(sequenceNumber, "response.reasoning_summary_part.added", reasoningSummaryPartAddedEvent(reasoningItemId)));
            }
            events.add(encodeEvent(sequenceNumber, "response.reasoning_summary_text.delta", reasoningSummaryTextDeltaEvent(reasoningItemId, canonicalEvent.reasoningDelta(), includeObfuscation)));
        }

        if (canonicalEvent.type() == CanonicalStreamEventType.TOOL_CALLS && canonicalEvent.toolCalls() != null && !canonicalEvent.toolCalls().isEmpty()) {
            int outputIndexBase = reasoningStarted.get() ? 2 : 1;
            for (int index = 0; index < canonicalEvent.toolCalls().size(); index++) {
                events.addAll(encodeToolCallEvents(canonicalEvent.toolCalls().get(index), outputIndexBase + index, sequenceNumber, includeObfuscation));
            }
        }

        if (canonicalEvent.type() == CanonicalStreamEventType.TEXT_DELTA && canonicalEvent.textDelta() != null && !canonicalEvent.textDelta().isBlank()) {
            events.add(encodeEvent(sequenceNumber, "response.output_text.delta", outputTextDeltaEvent(itemId, canonicalEvent.textDelta(), includeObfuscation)));
        }

        if (canonicalEvent.type() != CanonicalStreamEventType.COMPLETED) {
            return events.isEmpty() ? Flux.empty() : Flux.fromIterable(events);
        }

        String finalReasoning = canonicalEvent.reasoning();
        String finalText = canonicalEvent.outputText();
        if (reasoningStarted.get()) {
            events.add(encodeEvent(sequenceNumber, "response.reasoning_summary_text.done", reasoningSummaryTextDoneEvent(reasoningItemId, finalReasoning == null ? "" : finalReasoning)));
            events.add(encodeEvent(sequenceNumber, "response.reasoning_summary_part.done", reasoningSummaryPartDoneEvent(reasoningItemId, finalReasoning == null ? "" : finalReasoning)));
            events.add(encodeEvent(sequenceNumber, "response.output_item.done", reasoningItemDoneEvent(reasoningItemId, finalReasoning == null ? "" : finalReasoning)));
        }
        if (messageOpened.get()) {
            events.add(encodeEvent(sequenceNumber, "response.output_text.done", outputTextDoneEvent(itemId, finalText == null ? "" : finalText)));
            events.add(encodeEvent(sequenceNumber, "response.content_part.done", contentPartDoneEvent(itemId, finalText == null ? "" : finalText)));
            events.add(encodeEvent(sequenceNumber, "response.output_item.done", outputItemDoneEvent(itemId, finalText == null ? "" : finalText)));
        }
        events.add(encodeEvent(sequenceNumber, "response.completed", Map.of(
                "type", "response.completed",
                "response", OpenAiResponsesResponse.completedCanonical(requestId, publicModel, finalText, canonicalEvent.usage(), finalReasoning)
        )));
        return Flux.fromIterable(events);
    }

    private boolean includeObfuscation(JsonNode streamOptions) {
        if (streamOptions == null || streamOptions.isMissingNode() || streamOptions.isNull()) {
            return true;
        }
        JsonNode value = streamOptions.path("include_obfuscation");
        return value.isMissingNode() || value.isNull() || value.asBoolean(true);
    }

    private Map<String, Object> withObfuscation(Map<String, Object> payload, boolean includeObfuscation) {
        if (!includeObfuscation) {
            return payload;
        }
        Map<String, Object> result = new LinkedHashMap<>(payload);
        result.put("obfuscation", UUID.randomUUID().toString().replace("-", ""));
        return result;
    }

    private String encodeEvent(AtomicInteger sequenceNumber, String eventName, Object payload) {
        try {
            return "event: " + eventName + "\n" + "data: " + objectMapper.writeValueAsString(withSequenceNumber(eventName, payload, sequenceNumber.getAndIncrement())) + "\n\n";
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法序列化 Responses stream 响应。", exception);
        }
    }

    private Object withSequenceNumber(String eventName, Object payload, int sequenceNumber) {
        if (!(payload instanceof Map<?, ?> payloadMap)) {
            return payload;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : payloadMap.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        result.putIfAbsent("type", eventName);
        result.put("sequence_number", sequenceNumber);
        return result;
    }
}
