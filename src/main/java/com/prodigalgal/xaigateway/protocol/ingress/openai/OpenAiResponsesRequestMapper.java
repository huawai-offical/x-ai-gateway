package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalChatExecutionRequestAdapter;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalContentPart;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessageRole;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalReasoningConfig;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolDefinition;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OpenAiResponsesRequestMapper {

    private final ObjectMapper objectMapper;
    private final CanonicalChatExecutionRequestAdapter canonicalChatExecutionRequestAdapter = new CanonicalChatExecutionRequestAdapter();

    public OpenAiResponsesRequestMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ChatExecutionRequest toExecutionRequest(String distributedKeyPrefix, JsonNode requestBody) {
        return canonicalChatExecutionRequestAdapter.toExecutionRequest(toCanonicalRequest(distributedKeyPrefix, requestBody));
    }

    private CanonicalRequest toCanonicalRequest(String distributedKeyPrefix, JsonNode requestBody) {
        if (requestBody == null || !requestBody.isObject()) {
            throw new IllegalArgumentException("responses 请求体必须是 JSON object。");
        }

        String model = requestBody.path("model").asText(null);
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("responses 请求缺少 model。");
        }

        List<CanonicalMessage> messages = toMessages(requestBody.path("instructions"), requestBody.path("input"));
        ensureUserMessage(messages);

        return new CanonicalRequest(
                distributedKeyPrefix,
                CanonicalIngressProtocol.RESPONSES,
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
                buildReasoningConfig(requestBody),
                requestBody.deepCopy()
        );
    }

    private List<CanonicalMessage> toMessages(JsonNode instructionsNode, JsonNode inputNode) {
        List<CanonicalMessage> messages = new ArrayList<>();

        String instructions = instructionsNode == null || instructionsNode.isNull() ? null : instructionsNode.asText(null);
        if (instructions != null && !instructions.isBlank()) {
            messages.add(new CanonicalMessage(CanonicalMessageRole.SYSTEM, List.of(CanonicalContentPart.text(instructions))));
        }

        if (inputNode == null || inputNode.isMissingNode() || inputNode.isNull()) {
            return List.copyOf(messages);
        }

        if (inputNode.isTextual()) {
            messages.add(new CanonicalMessage(CanonicalMessageRole.USER, List.of(CanonicalContentPart.text(inputNode.asText()))));
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

    private CanonicalMessage toMessage(JsonNode messageNode) {
        CanonicalMessageRole role = CanonicalMessageRole.from(messageNode.path("role").asText("user"));
        List<CanonicalContentPart> parts = parseContent(messageNode.path("content"), role, messageNode.path("tool_call_id").asText(null), "tool");
        return new CanonicalMessage(role, parts);
    }

    private List<CanonicalMessage> toInputItems(JsonNode inputItemsNode) {
        List<CanonicalMessage> messages = new ArrayList<>();
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

    private List<CanonicalMessage> toConversationItems(JsonNode inputItemsNode) {
        List<CanonicalMessage> messages = new ArrayList<>();
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

    private void handleInputItem(List<CanonicalMessage> messages, ParsedContentAccumulator userAccumulator, JsonNode item) {
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
            messages.add(new CanonicalMessage(
                    CanonicalMessageRole.TOOL,
                    List.of(CanonicalContentPart.toolResult(callId, item.path("name").asText("tool"), output))
            ));
            return;
        }

        List<CanonicalContentPart> parts = parseContent(JsonNodeFactory.instance.arrayNode().add(item), CanonicalMessageRole.USER, null, null);
        userAccumulator.append(parts);
    }

    private List<CanonicalContentPart> parseContent(
            JsonNode contentNode,
            CanonicalMessageRole role,
            String toolCallId,
            String toolName) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return List.of();
        }

        if (role == CanonicalMessageRole.TOOL) {
            String content = contentNode.isTextual() ? contentNode.asText()
                    : contentNode.isObject() || contentNode.isArray() ? contentNode.toString()
                    : "";
            return List.of(CanonicalContentPart.toolResult(toolCallId, toolName == null ? "tool" : toolName, content));
        }

        if (contentNode.isTextual()) {
            return List.of(CanonicalContentPart.text(contentNode.asText()));
        }

        if (!contentNode.isArray()) {
            return List.of(CanonicalContentPart.text(contentNode.toString()));
        }

        List<CanonicalContentPart> parts = new ArrayList<>();
        for (JsonNode item : contentNode) {
            String type = item.path("type").asText();
            if ("input_text".equalsIgnoreCase(type) || "text".equalsIgnoreCase(type)) {
                String text = item.path("text").asText(null);
                if (text != null && !text.isBlank()) {
                    parts.add(CanonicalContentPart.text(text));
                }
                continue;
            }

            if ("input_image".equalsIgnoreCase(type) || "image_url".equalsIgnoreCase(type)) {
                String fileId = item.path("file_id").asText(null);
                if (fileId != null && !fileId.isBlank()) {
                    parts.add(CanonicalContentPart.image(
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
                    parts.add(CanonicalContentPart.image(
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
                    parts.add(CanonicalContentPart.file(
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
                    parts.add(CanonicalContentPart.file(
                            readText(item, "mime_type", "application/octet-stream"),
                            fileUrl,
                            readOptionalText(item, "filename")
                    ));
                }
            }
        }
        return List.copyOf(parts);
    }

    private List<CanonicalToolDefinition> toTools(JsonNode toolsNode) {
        if (toolsNode == null || toolsNode.isMissingNode() || toolsNode.isNull() || !toolsNode.isArray()) {
            return List.of();
        }

        List<CanonicalToolDefinition> tools = new ArrayList<>();
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
            tools.add(new CanonicalToolDefinition(
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

    private CanonicalReasoningConfig buildReasoningConfig(JsonNode requestBody) {
        JsonNode reasoning = requestBody.get("reasoning");
        String reasoningEffort = requestBody.path("reasoning_effort").asText(null);
        if ((reasoning == null || reasoning.isNull()) && (reasoningEffort == null || reasoningEffort.isBlank())) {
            return null;
        }
        return new CanonicalReasoningConfig(reasoning, reasoningEffort);
    }

    private void ensureUserMessage(List<CanonicalMessage> messages) {
        boolean hasUsableConversationInput = messages.stream()
                .anyMatch(message -> message.role() == CanonicalMessageRole.USER || message.role() == CanonicalMessageRole.TOOL);
        if (!hasUsableConversationInput) {
            throw new IllegalArgumentException("至少需要一条 user 输入或 function_call_output。");
        }
    }

    private void flushUserAccumulator(List<CanonicalMessage> messages, ParsedContentAccumulator accumulator) {
        if (accumulator.isEmpty()) {
            return;
        }
        messages.add(new CanonicalMessage(CanonicalMessageRole.USER, accumulator.parts()));
        accumulator.clear();
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

    private static final class ParsedContentAccumulator {
        private final List<CanonicalContentPart> parts = new ArrayList<>();

        private void append(List<CanonicalContentPart> canonicalParts) {
            if (canonicalParts != null && !canonicalParts.isEmpty()) {
                parts.addAll(canonicalParts);
            }
        }

        private boolean isEmpty() {
            return parts.isEmpty();
        }

        private List<CanonicalContentPart> parts() {
            return List.copyOf(parts);
        }

        private void clear() {
            parts.clear();
        }
    }
}
