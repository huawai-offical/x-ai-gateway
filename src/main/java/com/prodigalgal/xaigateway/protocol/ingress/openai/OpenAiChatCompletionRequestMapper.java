package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalContentPart;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessageRole;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolDefinition;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OpenAiChatCompletionRequestMapper {

    private final ObjectMapper objectMapper;

    public OpenAiChatCompletionRequestMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CanonicalRequest toCanonicalRequest(
            String distributedKeyPrefix,
            OpenAiChatCompletionRequest request) {
        return toCanonicalRequest(new AuthenticatedDistributedKey(null, distributedKeyPrefix, distributedKeyPrefix), request);
    }

    public CanonicalRequest toCanonicalRequest(
            String distributedKeyPrefix,
            JsonNode requestBody) {
        try {
            return toCanonicalRequest(distributedKeyPrefix, objectMapper.treeToValue(requestBody, OpenAiChatCompletionRequest.class));
        } catch (Exception exception) {
            throw new IllegalArgumentException("OpenAI chat 请求体解析失败。", exception);
        }
    }

    public CanonicalRequest toCanonicalRequest(
            AuthenticatedDistributedKey distributedKey,
            OpenAiChatCompletionRequest request) {
        List<CanonicalMessage> messages = toMessages(request.messages());
        ensureUserMessage(messages);
        return new CanonicalRequest(
                distributedKey.keyPrefix(),
                CanonicalIngressProtocol.OPENAI,
                "/v1/chat/completions",
                request.model(),
                messages,
                toTools(request.tools()),
                request.toolChoice(),
                request.temperature(),
                request.maxTokens(),
                buildReasoningConfig(request),
                buildExecutionMetadata(request)
        );
    }

    private List<CanonicalMessage> toMessages(List<OpenAiChatCompletionRequest.Message> messages) {
        List<CanonicalMessage> result = new ArrayList<>();
        if (messages == null) {
            return result;
        }
        for (OpenAiChatCompletionRequest.Message message : messages) {
            ParsedMessageContent parsed = parseMessageContent(message.content(), CanonicalMessageRole.from(message.role()), message.toolCallId());
            if (parsed.parts().isEmpty()) {
                continue;
            }
            result.add(new CanonicalMessage(CanonicalMessageRole.from(message.role()), parsed.parts()));
        }
        return List.copyOf(result);
    }

    private void ensureUserMessage(List<CanonicalMessage> messages) {
        boolean hasUser = messages.stream()
                .anyMatch(message -> message.role() == CanonicalMessageRole.USER
                        && message.parts() != null
                        && !message.parts().isEmpty());
        if (!hasUser) {
            throw new IllegalArgumentException("至少需要一条 user 消息。");
        }
    }

    private List<CanonicalToolDefinition> toTools(List<OpenAiChatCompletionRequest.Tool> tools) {
        List<CanonicalToolDefinition> result = new ArrayList<>();
        if (tools == null) {
            return result;
        }
        for (OpenAiChatCompletionRequest.Tool tool : tools) {
            if (tool == null || tool.function() == null || tool.function().name() == null || tool.function().name().isBlank()) {
                continue;
            }
            result.add(new CanonicalToolDefinition(
                    tool.function().name(),
                    tool.function().description(),
                    tool.function().parameters(),
                    tool.function().strict()
            ));
        }
        return List.copyOf(result);
    }

    private com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalReasoningConfig buildReasoningConfig(OpenAiChatCompletionRequest request) {
        JsonNode reasoning = request.reasoning();
        String effort = request.reasoningEffort();
        if ((reasoning == null || reasoning.isNull()) && (effort == null || effort.isBlank())) {
            return null;
        }
        return new com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalReasoningConfig(reasoning, effort);
    }

    private JsonNode buildExecutionMetadata(OpenAiChatCompletionRequest request) {
        tools.jackson.databind.node.ObjectNode metadata = objectMapper.createObjectNode();
        if (request.reasoning() != null && !request.reasoning().isNull()) {
            metadata.set("reasoning", request.reasoning());
        }
        if (request.reasoningEffort() != null && !request.reasoningEffort().isBlank()) {
            metadata.put("reasoning_effort", request.reasoningEffort());
        }
        return metadata.isEmpty() ? null : metadata;
    }

    private ParsedMessageContent parseMessageContent(JsonNode contentNode, CanonicalMessageRole role, String toolCallId) {
        if (contentNode == null || contentNode.isNull() || contentNode.isMissingNode()) {
            return new ParsedMessageContent(List.of());
        }

        if (role == CanonicalMessageRole.TOOL) {
            String text = contentNode.isTextual() ? contentNode.asText() : contentNode.toString();
            return new ParsedMessageContent(List.of(CanonicalContentPart.toolResult(toolCallId, "tool", text)));
        }

        if (contentNode.isTextual()) {
            return new ParsedMessageContent(List.of(CanonicalContentPart.text(contentNode.asText())));
        }

        if (contentNode.isArray()) {
            List<CanonicalContentPart> parts = new ArrayList<>();
            for (JsonNode item : contentNode) {
                String type = item.path("type").asText();
                if ("text".equalsIgnoreCase(type)) {
                    String text = item.path("text").asText(null);
                    if (text != null && !text.isBlank()) {
                        parts.add(CanonicalContentPart.text(text));
                    }
                }
                if ("image_url".equalsIgnoreCase(type)) {
                    String url = item.path("image_url").path("url").asText(null);
                    if (url != null && !url.isBlank()) {
                        parts.add(CanonicalContentPart.image("image/*", url, null));
                    }
                }
                if ("input_file".equalsIgnoreCase(type)) {
                    JsonNode inputFile = item.path("input_file");
                    String fileId = inputFile.path("file_id").asText(null);
                    if (fileId != null && !fileId.isBlank()) {
                        parts.add(CanonicalContentPart.file(
                                inputFile.path("mime_type").asText("application/octet-stream"),
                                "gateway://" + fileId,
                                inputFile.path("filename").asText(fileId)
                        ));
                        continue;
                    }
                    String url = inputFile.path("url").asText(null);
                    if (url == null || url.isBlank()) {
                        url = inputFile.path("file_url").asText(null);
                    }
                    if (url == null || url.isBlank()) {
                        url = item.path("file_url").asText(null);
                    }
                    if (url != null && !url.isBlank()) {
                        parts.add(CanonicalContentPart.file(
                                inputFile.path("mime_type").asText("application/octet-stream"),
                                url,
                                inputFile.path("filename").asText(null)
                        ));
                    }
                }
            }
            return new ParsedMessageContent(List.copyOf(parts));
        }

        return new ParsedMessageContent(List.of(CanonicalContentPart.text(contentNode.toString())));
    }

    private record ParsedMessageContent(
            List<CanonicalContentPart> parts
    ) {
    }
}
