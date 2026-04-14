package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest.MediaInput;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest.MessageInput;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolDefinition;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OpenAiResponsesRequestMapper {

    private final ObjectMapper objectMapper;

    public OpenAiResponsesRequestMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ChatExecutionRequest toExecutionRequest(String distributedKeyPrefix, JsonNode requestBody) {
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
                        : null,
                objectMapper.valueToTree(requestBody)
        );
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
