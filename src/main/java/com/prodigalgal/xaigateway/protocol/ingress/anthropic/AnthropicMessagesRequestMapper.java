package com.prodigalgal.xaigateway.protocol.ingress.anthropic;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalChatExecutionRequestAdapter;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalContentPart;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessageRole;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolDefinition;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AnthropicMessagesRequestMapper {

    private final ObjectMapper objectMapper;
    private final CanonicalChatExecutionRequestAdapter canonicalChatExecutionRequestAdapter = new CanonicalChatExecutionRequestAdapter();

    public AnthropicMessagesRequestMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CanonicalRequest toCanonicalRequest(
            AuthenticatedDistributedKey distributedKey,
            AnthropicMessagesRequest request) {
        List<CanonicalMessage> messages = toMessages(request.system(), request.messages());
        ensureUserMessage(messages);
        return new CanonicalRequest(
                distributedKey.keyPrefix(),
                CanonicalIngressProtocol.ANTHROPIC_NATIVE,
                "/v1/messages",
                request.model(),
                messages,
                toTools(request.tools()),
                request.toolChoice(),
                request.temperature(),
                request.maxTokens(),
                null,
                objectMapper.valueToTree(request)
        );
    }

    public ChatExecutionRequest toExecutionRequest(
            AuthenticatedDistributedKey distributedKey,
            AnthropicMessagesRequest request) {
        return canonicalChatExecutionRequestAdapter.toExecutionRequest(toCanonicalRequest(distributedKey, request));
    }

    private List<CanonicalMessage> toMessages(JsonNode systemNode, List<AnthropicMessagesRequest.Message> messages) {
        List<CanonicalMessage> result = new ArrayList<>();
        String systemPrompt = extractText(systemNode);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            result.add(new CanonicalMessage(CanonicalMessageRole.SYSTEM, List.of(CanonicalContentPart.text(systemPrompt))));
        }

        if (messages != null) {
            for (AnthropicMessagesRequest.Message message : messages) {
                if (isToolResult(message.content())) {
                    String toolUseId = message.content().findValues("tool_use_id").stream()
                            .map(JsonNode::asText)
                            .findFirst()
                            .orElse("tool-use");
                    String toolContent = message.content().findValues("content").stream()
                            .map(JsonNode::asText)
                            .reduce((first, second) -> second)
                            .orElse("");
                    result.add(new CanonicalMessage(
                            CanonicalMessageRole.TOOL,
                            List.of(CanonicalContentPart.toolResult(toolUseId, "tool", toolContent))
                    ));
                    continue;
                }

                List<CanonicalContentPart> parts = extractParts(message.content());
                if (parts.isEmpty()) {
                    continue;
                }
                result.add(new CanonicalMessage(CanonicalMessageRole.from(message.role()), parts));
            }
        }

        return List.copyOf(result);
    }

    private boolean isToolResult(JsonNode contentNode) {
        return contentNode != null
                && contentNode.isArray()
                && contentNode.findValues("type").stream().anyMatch(node -> "tool_result".equalsIgnoreCase(node.asText()));
    }

    private List<CanonicalContentPart> extractParts(JsonNode contentNode) {
        List<CanonicalContentPart> result = new ArrayList<>();
        if (contentNode == null || contentNode.isNull() || contentNode.isMissingNode()) {
            return result;
        }
        if (contentNode.isTextual()) {
            result.add(CanonicalContentPart.text(contentNode.asText()));
            return List.copyOf(result);
        }
        if (!contentNode.isArray()) {
            result.add(CanonicalContentPart.text(contentNode.toString()));
            return List.copyOf(result);
        }

        for (JsonNode block : contentNode) {
            if ("text".equalsIgnoreCase(block.path("type").asText())) {
                String text = block.path("text").asText(null);
                if (text != null && !text.isBlank()) {
                    result.add(CanonicalContentPart.text(text));
                }
                continue;
            }
            if (!"image".equalsIgnoreCase(block.path("type").asText())) {
                if ("document".equalsIgnoreCase(block.path("type").asText())) {
                    JsonNode source = block.path("source");
                    String fileId = source.path("file_id").asText(null);
                    if (fileId != null && !fileId.isBlank()) {
                        result.add(CanonicalContentPart.file(
                                source.path("media_type").asText("application/octet-stream"),
                                "gateway://" + fileId,
                                block.path("title").asText(fileId)
                        ));
                        continue;
                    }
                    String url = source.path("url").asText(null);
                    if (url == null || url.isBlank()) {
                        url = source.path("uri").asText(null);
                    }
                    if (url == null || url.isBlank()) {
                        continue;
                    }
                    result.add(CanonicalContentPart.file(
                            source.path("media_type").asText("application/octet-stream"),
                            url,
                            block.path("title").asText(null)
                    ));
                }
                continue;
            }

            JsonNode source = block.path("source");
            String fileId = source.path("file_id").asText(null);
            if (fileId != null && !fileId.isBlank()) {
                result.add(CanonicalContentPart.image(
                        source.path("media_type").asText("image/*"),
                        "gateway://" + fileId,
                        null
                ));
                continue;
            }
            String url = source.path("url").asText(null);
            if (url == null || url.isBlank()) {
                url = source.path("uri").asText(null);
            }
            if (url == null || url.isBlank()) {
                continue;
            }
            result.add(CanonicalContentPart.image(
                    source.path("media_type").asText("image/*"),
                    url,
                    null
            ));
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

    private List<CanonicalToolDefinition> toTools(List<AnthropicMessagesRequest.Tool> tools) {
        List<CanonicalToolDefinition> result = new ArrayList<>();
        if (tools == null) {
            return result;
        }
        for (AnthropicMessagesRequest.Tool tool : tools) {
            if (tool.name() == null || tool.name().isBlank()) {
                continue;
            }
            result.add(new CanonicalToolDefinition(
                    tool.name(),
                    tool.description(),
                    tool.inputSchema(),
                    null
            ));
        }
        return List.copyOf(result);
    }

    private String extractText(JsonNode contentNode) {
        if (contentNode == null || contentNode.isNull() || contentNode.isMissingNode()) {
            return null;
        }
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            return contentNode.findValues("text").stream()
                    .map(JsonNode::asText)
                    .reduce((first, second) -> second)
                    .orElse(null);
        }
        if (contentNode.has("text")) {
            return contentNode.path("text").asText();
        }
        return contentNode.toString();
    }
}
