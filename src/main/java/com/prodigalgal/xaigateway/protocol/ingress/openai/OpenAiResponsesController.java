package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionService;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest.MediaInput;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest.MessageInput;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionResponse;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionStreamChunk;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionStreamResponse;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolDefinition;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceService;
import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Validated
@RestController
@RequestMapping("/v1/responses")
public class OpenAiResponsesController {

    private final DistributedKeyAuthenticationService distributedKeyAuthenticationService;
    private final GatewayChatExecutionService gatewayChatExecutionService;
    private final GatewayAsyncResourceService gatewayAsyncResourceService;
    private final ObjectMapper objectMapper;

    public OpenAiResponsesController(
            DistributedKeyAuthenticationService distributedKeyAuthenticationService,
            GatewayChatExecutionService gatewayChatExecutionService,
            GatewayAsyncResourceService gatewayAsyncResourceService,
            ObjectMapper objectMapper) {
        this.distributedKeyAuthenticationService = distributedKeyAuthenticationService;
        this.gatewayChatExecutionService = gatewayChatExecutionService;
        this.gatewayAsyncResourceService = gatewayAsyncResourceService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<?> createResponse(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        ChatExecutionRequest executionRequest = toExecutionRequest(distributedKey.keyPrefix(), requestBody);

        if (requestBody.path("stream").asBoolean(false)) {
            ChatExecutionStreamResponse streamResponse = gatewayChatExecutionService.executeStream(executionRequest);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(encodeStream(streamResponse));
        }

        ChatExecutionResponse response = gatewayChatExecutionService.execute(executionRequest);
        OpenAiResponsesResponse payload = OpenAiResponsesResponse.from(response);
        if (requestBody.path("store").asBoolean(false)) {
            return ResponseEntity.ok(gatewayAsyncResourceService.storeResponse(
                    distributedKey.id(),
                    executionRequest.requestedModel(),
                    requestBody,
                    objectMapper.valueToTree(payload)
            ));
        }
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/{responseId}")
    public JsonNode getStoredResponse(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String responseId) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.getResponse(responseId, distributedKey.id());
    }

    @DeleteMapping("/{responseId}")
    public JsonNode deleteStoredResponse(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String responseId) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.deleteResponse(responseId, distributedKey.id());
    }

    private ChatExecutionRequest toExecutionRequest(String distributedKeyPrefix, JsonNode requestBody) {
        if (requestBody == null || !requestBody.isObject()) {
            throw new IllegalArgumentException("responses 请求体必须是 JSON object。");
        }

        String model = requestBody.path("model").asText(null);
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("responses 请求缺少 model。");
        }

        List<MessageInput> messages = toMessages(requestBody.path("instructions"), requestBody.path("input"));
        ensureUserMessage(messages);

        return new ChatExecutionRequest(
                distributedKeyPrefix,
                "responses",
                "/v1/responses",
                model,
                messages,
                toTools(requestBody.path("tools")),
                requestBody.has("tool_choice") ? requestBody.get("tool_choice") : null,
                requestBody.has("temperature") && !requestBody.get("temperature").isNull()
                        ? requestBody.get("temperature").asDouble()
                        : null,
                requestBody.has("max_output_tokens") && !requestBody.get("max_output_tokens").isNull()
                        ? requestBody.get("max_output_tokens").asInt()
                        : null
        );
    }

    private Flux<String> encodeStream(ChatExecutionStreamResponse response) {
        AtomicReference<StringBuilder> textAccumulator = new AtomicReference<>(new StringBuilder());
        AtomicReference<GatewayUsage> latestUsage = new AtomicReference<>(GatewayUsage.empty());
        String itemId = "msg_" + response.requestId();

        return Flux.concat(
                Flux.just(
                        encodeEvent("response.created", createdEvent(response)),
                        encodeEvent("response.in_progress", inProgressEvent(response)),
                        encodeEvent("response.output_item.added", outputItemAddedEvent(itemId)),
                        encodeEvent("response.content_part.added", contentPartAddedEvent(itemId))
                ),
                response.chunks().flatMap(chunk -> encodeChunk(response, chunk, itemId, textAccumulator, latestUsage))
        );
    }

    private Flux<String> encodeChunk(
            ChatExecutionStreamResponse response,
            ChatExecutionStreamChunk chunk,
            String itemId,
            AtomicReference<StringBuilder> textAccumulator,
            AtomicReference<GatewayUsage> latestUsage) {
        List<String> toolEvents = new ArrayList<>();
        if (chunk.terminal()) {
            String finalText = textAccumulator.get().toString();
            GatewayUsage usage = latestUsage.get();
            return Flux.just(
                    encodeEvent("response.output_text.done", outputTextDoneEvent(itemId, finalText)),
                    encodeEvent("response.content_part.done", contentPartDoneEvent(itemId, finalText)),
                    encodeEvent("response.output_item.done", outputItemDoneEvent(itemId, finalText)),
                    encodeEvent("response.completed", completedEvent(response, finalText, usage))
            );
        }

        if (chunk.usage() != null) {
            latestUsage.set(chunk.usage());
        }
        if (chunk.toolCalls() != null && !chunk.toolCalls().isEmpty()) {
            for (int index = 0; index < chunk.toolCalls().size(); index++) {
                toolEvents.addAll(encodeToolCallEvents(chunk.toolCalls().get(index), index));
            }
        }
        if (chunk.textDelta() == null || chunk.textDelta().isBlank()) {
            return toolEvents.isEmpty() ? Flux.empty() : Flux.fromIterable(toolEvents);
        }

        textAccumulator.get().append(chunk.textDelta());
        List<String> events = new ArrayList<>(toolEvents);
        events.add(encodeEvent("response.output_text.delta", outputTextDeltaEvent(itemId, chunk.textDelta())));
        return Flux.fromIterable(events);
    }

    private Map<String, Object> createdEvent(ChatExecutionStreamResponse response) {
        return Map.of(
                "type", "response.created",
                "response", OpenAiResponsesResponse.inProgress(response)
        );
    }

    private Map<String, Object> inProgressEvent(ChatExecutionStreamResponse response) {
        return Map.of(
                "type", "response.in_progress",
                "response", OpenAiResponsesResponse.inProgress(response)
        );
    }

    private Map<String, Object> outputItemAddedEvent(String itemId) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", itemId);
        item.put("type", "message");
        item.put("status", "in_progress");
        item.put("role", "assistant");
        item.put("content", List.of());

        return Map.of(
                "type", "response.output_item.added",
                "output_index", 0,
                "item", item
        );
    }

    private Map<String, Object> contentPartAddedEvent(String itemId) {
        return Map.of(
                "type", "response.content_part.added",
                "item_id", itemId,
                "output_index", 0,
                "content_index", 0,
                "part", Map.of(
                        "type", "output_text",
                        "text", "",
                        "annotations", List.of()
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
                "part", Map.of(
                        "type", "output_text",
                        "text", text,
                        "annotations", List.of()
                )
        );
    }

    private Map<String, Object> outputItemDoneEvent(String itemId, String text) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", itemId);
        item.put("type", "message");
        item.put("status", "completed");
        item.put("role", "assistant");
        item.put("content", List.of(Map.of(
                "type", "output_text",
                "text", text,
                "annotations", List.of()
        )));

        return Map.of(
                "type", "response.output_item.done",
                "output_index", 0,
                "item", item
        );
    }

    private List<String> encodeToolCallEvents(com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall toolCall, int outputIndex) {
        String itemId = toolCall.id() == null || toolCall.id().isBlank() ? "fc_" + outputIndex : toolCall.id();
        List<String> events = new ArrayList<>();
        events.add(encodeEvent("response.output_item.added", functionCallAddedEvent(itemId, outputIndex, toolCall)));
        if (toolCall.arguments() != null && !toolCall.arguments().isBlank()) {
            events.add(encodeEvent("response.function_call_arguments.delta",
                    functionCallArgumentsDeltaEvent(itemId, outputIndex, toolCall.arguments())));
            events.add(encodeEvent("response.function_call_arguments.done",
                    functionCallArgumentsDoneEvent(itemId, outputIndex, toolCall.arguments())));
        }
        events.add(encodeEvent("response.output_item.done", functionCallDoneEvent(itemId, outputIndex, toolCall)));
        return events;
    }

    private Map<String, Object> functionCallAddedEvent(
            String itemId,
            int outputIndex,
            com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall toolCall) {
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
        return Map.of(
                "type", "response.function_call_arguments.delta",
                "item_id", itemId,
                "output_index", outputIndex,
                "delta", arguments
        );
    }

    private Map<String, Object> functionCallArgumentsDoneEvent(String itemId, int outputIndex, String arguments) {
        return Map.of(
                "type", "response.function_call_arguments.done",
                "item_id", itemId,
                "output_index", outputIndex,
                "arguments", arguments
        );
    }

    private Map<String, Object> functionCallDoneEvent(
            String itemId,
            int outputIndex,
            com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall toolCall) {
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

    private Map<String, Object> completedEvent(
            ChatExecutionStreamResponse response,
            String text,
            GatewayUsage usage) {
        return Map.of(
                "type", "response.completed",
                "response", OpenAiResponsesResponse.completed(response, text, usage)
        );
    }

    private String encodeEvent(String eventName, Object payload) {
        try {
            return "event: " + eventName + "\n" + "data: " + objectMapper.writeValueAsString(payload) + "\n\n";
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化 Responses stream 响应。", exception);
        }
    }

    private List<MessageInput> toMessages(JsonNode instructionsNode, JsonNode inputNode) {
        List<MessageInput> messages = new ArrayList<>();

        String instructions = instructionsNode == null || instructionsNode.isNull() ? null : instructionsNode.asText(null);
        if (instructions != null && !instructions.isBlank()) {
            messages.add(new MessageInput("system", instructions, null, null, List.of()));
        }

        if (inputNode == null || inputNode.isMissingNode() || inputNode.isNull()) {
            return List.copyOf(messages);
        }

        if (inputNode.isTextual()) {
            messages.add(new MessageInput("user", inputNode.asText(), null, null, List.of()));
            return List.copyOf(messages);
        }

        if (inputNode.isObject()) {
            if (!inputNode.has("role") && inputNode.has("type")) {
                messages.addAll(toInputItems(inputNode));
                return List.copyOf(messages);
            }
            messages.add(toMessage(inputNode));
            return List.copyOf(messages);
        }

        if (inputNode.isArray()) {
            if (inputNode.isEmpty()) {
                return List.copyOf(messages);
            }
            messages.addAll(toConversationItems(inputNode));
            return List.copyOf(messages);
        }

        throw new IllegalArgumentException("responses input 格式不受支持。");
    }

    private MessageInput toMessage(JsonNode messageNode) {
        String role = messageNode.path("role").asText("user");
        ParsedContent parsed = parseContent(messageNode.path("content"));
        String toolCallId = messageNode.path("tool_call_id").asText(null);
        return new MessageInput(role, parsed.text(), toolCallId, "tool".equalsIgnoreCase(role) ? "tool" : null, parsed.media());
    }

    private List<MessageInput> toInputItems(JsonNode inputItemsNode) {
        List<MessageInput> messages = new ArrayList<>();
        ParsedContentAccumulator userAccumulator = new ParsedContentAccumulator();

        if (inputItemsNode.isArray()) {
            for (JsonNode item : inputItemsNode) {
                handleInputItem(messages, userAccumulator, item);
            }
        } else {
            handleInputItem(messages, userAccumulator, inputItemsNode);
        }

        flushUserAccumulator(messages, userAccumulator);
        return List.copyOf(messages);
    }

    private List<MessageInput> toConversationItems(JsonNode inputItemsNode) {
        List<MessageInput> messages = new ArrayList<>();
        ParsedContentAccumulator userAccumulator = new ParsedContentAccumulator();

        for (JsonNode item : inputItemsNode) {
            if (item != null && item.isObject() && item.has("role")) {
                flushUserAccumulator(messages, userAccumulator);
                messages.add(toMessage(item));
                continue;
            }
            handleInputItem(messages, userAccumulator, item);
        }

        flushUserAccumulator(messages, userAccumulator);
        return List.copyOf(messages);
    }

    private void handleInputItem(List<MessageInput> messages, ParsedContentAccumulator userAccumulator, JsonNode item) {
        String type = item.path("type").asText();
        if ("function_call_output".equalsIgnoreCase(type)) {
            flushUserAccumulator(messages, userAccumulator);
            String callId = item.path("call_id").asText(null);
            if (callId == null || callId.isBlank()) {
                throw new IllegalArgumentException("function_call_output 缺少 call_id。");
            }
            JsonNode outputNode = item.path("output");
            String output = outputNode.isMissingNode() || outputNode.isNull()
                    ? ""
                    : outputNode.isTextual() ? outputNode.asText() : outputNode.toString();
            messages.add(new MessageInput(
                    "tool",
                    output,
                    callId,
                    item.path("name").asText("tool"),
                    List.of()
            ));
            return;
        }

        ParsedContent parsed = parseContent(JsonNodeFactory.instance.arrayNode().add(item));
        userAccumulator.append(parsed);
    }

    private ParsedContent parseContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return new ParsedContent("", List.of());
        }

        if (contentNode.isTextual()) {
            return new ParsedContent(contentNode.asText(), List.of());
        }

        if (!contentNode.isArray()) {
            return new ParsedContent(contentNode.toString(), List.of());
        }

        StringBuilder text = new StringBuilder();
        List<MediaInput> media = new ArrayList<>();
        for (JsonNode item : contentNode) {
            String type = item.path("type").asText();
            if ("input_text".equalsIgnoreCase(type) || "text".equalsIgnoreCase(type)) {
                appendText(text, item.path("text").asText(""));
                continue;
            }

            if ("input_image".equalsIgnoreCase(type) || "image_url".equalsIgnoreCase(type)) {
                String fileId = item.path("file_id").asText(null);
                if (fileId != null && !fileId.isBlank()) {
                    media.add(new MediaInput(
                            "image",
                            readText(item, "mime_type", "image/*"),
                            "gateway://" + fileId,
                            readOptionalText(item, "filename")
                    ));
                    continue;
                }

                String url = item.path("image_url").asText(null);
                if ((url == null || url.isBlank()) && item.path("image_url").isObject()) {
                    url = item.path("image_url").path("url").asText(null);
                }
                if (url == null || url.isBlank()) {
                    url = item.path("file_url").asText(null);
                }
                if (url != null && !url.isBlank()) {
                    media.add(new MediaInput(
                            "image",
                            readText(item, "mime_type", "image/*"),
                            url,
                            readOptionalText(item, "filename")
                    ));
                }
                continue;
            }

            if ("input_file".equalsIgnoreCase(type)) {
                String fileId = item.path("file_id").asText(null);
                if ((fileId == null || fileId.isBlank()) && item.path("input_file").isObject()) {
                    fileId = item.path("input_file").path("file_id").asText(null);
                }
                if (fileId != null && !fileId.isBlank()) {
                    media.add(new MediaInput(
                            "file",
                            readText(item, "mime_type", "application/octet-stream"),
                            "gateway://" + fileId,
                            readOptionalText(item, "filename")
                    ));
                    continue;
                }

                String fileUrl = item.path("file_url").asText(null);
                if ((fileUrl == null || fileUrl.isBlank()) && item.path("input_file").isObject()) {
                    JsonNode inputFile = item.path("input_file");
                    fileUrl = inputFile.path("url").asText(null);
                    if (fileUrl == null || fileUrl.isBlank()) {
                        fileUrl = inputFile.path("file_url").asText(null);
                    }
                }
                if (fileUrl != null && !fileUrl.isBlank()) {
                    media.add(new MediaInput(
                            "file",
                            readText(item, "mime_type", "application/octet-stream"),
                            fileUrl,
                            readOptionalText(item, "filename")
                    ));
                }
            }
        }
        return new ParsedContent(text.toString(), List.copyOf(media));
    }

    private List<GatewayToolDefinition> toTools(JsonNode toolsNode) {
        if (toolsNode == null || toolsNode.isMissingNode() || toolsNode.isNull() || !toolsNode.isArray()) {
            return List.of();
        }

        List<GatewayToolDefinition> tools = new ArrayList<>();
        for (JsonNode tool : toolsNode) {
            JsonNode definition = tool.path("function").isObject() ? tool.path("function") : tool;
            String type = tool.path("type").asText("function");
            if (!"function".equalsIgnoreCase(type)) {
                continue;
            }
            String name = definition.path("name").asText(null);
            if (name == null || name.isBlank()) {
                continue;
            }
            tools.add(new GatewayToolDefinition(
                    name,
                    definition.path("description").asText(null),
                    definition.has("parameters") ? definition.get("parameters") : null,
                    definition.has("strict") && !definition.get("strict").isNull()
                            ? definition.get("strict").asBoolean()
                            : null
            ));
        }
        return List.copyOf(tools);
    }

    private void ensureUserMessage(List<MessageInput> messages) {
        boolean hasUsableConversationInput = messages.stream()
                .anyMatch(message -> {
                    boolean hasPayload = (message.content() != null && !message.content().isBlank())
                            || (message.media() != null && !message.media().isEmpty());
                    return hasPayload && ("user".equalsIgnoreCase(message.role()) || "tool".equalsIgnoreCase(message.role()));
                });
        if (!hasUsableConversationInput) {
            throw new IllegalArgumentException("至少需要一条 user 输入或 function_call_output。");
        }
    }

    private void flushUserAccumulator(List<MessageInput> messages, ParsedContentAccumulator accumulator) {
        if (accumulator.isEmpty()) {
            return;
        }
        messages.add(new MessageInput("user", accumulator.text(), null, null, accumulator.media()));
        accumulator.clear();
    }

    private void appendText(StringBuilder text, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!text.isEmpty()) {
            text.append('\n');
        }
        text.append(value);
    }

    private String readText(JsonNode item, String field, String defaultValue) {
        String direct = item.path(field).asText(null);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        if (item.path("input_file").isObject()) {
            String nested = item.path("input_file").path(field).asText(null);
            if (nested != null && !nested.isBlank()) {
                return nested;
            }
        }
        return defaultValue;
    }

    private String readOptionalText(JsonNode item, String field) {
        String direct = item.path(field).asText(null);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        if (item.path("input_file").isObject()) {
            String nested = item.path("input_file").path(field).asText(null);
            if (nested != null && !nested.isBlank()) {
                return nested;
            }
        }
        return null;
    }

    private record ParsedContent(
            String text,
            List<MediaInput> media
    ) {
    }

    private static final class ParsedContentAccumulator {
        private final StringBuilder text = new StringBuilder();
        private final List<MediaInput> media = new ArrayList<>();

        private void append(ParsedContent parsedContent) {
            if (parsedContent == null) {
                return;
            }
            if (parsedContent.text() != null && !parsedContent.text().isBlank()) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(parsedContent.text());
            }
            if (parsedContent.media() != null && !parsedContent.media().isEmpty()) {
                media.addAll(parsedContent.media());
            }
        }

        private boolean isEmpty() {
            return text.isEmpty() && media.isEmpty();
        }

        private String text() {
            return text.toString();
        }

        private List<MediaInput> media() {
            return List.copyOf(media);
        }

        private void clear() {
            text.setLength(0);
            media.clear();
        }
    }
}
