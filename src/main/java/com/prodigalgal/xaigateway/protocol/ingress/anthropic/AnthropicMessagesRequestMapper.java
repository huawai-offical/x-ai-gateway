package com.prodigalgal.xaigateway.protocol.ingress.anthropic;

import tools.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest.MessageInput;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolDefinition;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AnthropicMessagesRequestMapper {

    public ChatExecutionRequest toExecutionRequest(
            AuthenticatedDistributedKey distributedKey,
            AnthropicMessagesRequest request) {
        List<MessageInput> messages = toMessages(request.system(), request.messages());
        ensureUserMessage(messages);

        return new ChatExecutionRequest(
                distributedKey.keyPrefix(),
                "anthropic_native",
                "/v1/messages",
                request.model(),
                messages,
                toTools(request.tools()),
                request.toolChoice(),
                request.temperature(),
                request.maxTokens()
        );
    }

    private List<MessageInput> toMessages(JsonNode systemNode, List<AnthropicMessagesRequest.Message> messages) {
        List<MessageInput> result = new ArrayList<>();
        String systemPrompt = extractText(systemNode);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            result.add(new MessageInput("system", systemPrompt, null, null, List.of()));
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
                    result.add(new MessageInput("tool", toolContent, toolUseId, "tool", List.of()));
                    continue;
                }

                String text = extractText(message.content());
                List<ChatExecutionRequest.MediaInput> media = extractMedia(message.content());
                if ((text == null || text.isBlank()) && media.isEmpty()) {
                    continue;
                }
                result.add(new MessageInput(message.role(), text, null, null, media));
            }
        }

        return List.copyOf(result);
    }

    private boolean isToolResult(JsonNode contentNode) {
        return contentNode != null
                && contentNode.isArray()
                && contentNode.findValues("type").stream().anyMatch(node -> "tool_result".equalsIgnoreCase(node.asText()));
    }

    private List<ChatExecutionRequest.MediaInput> extractMedia(JsonNode contentNode) {
        List<ChatExecutionRequest.MediaInput> result = new ArrayList<>();
        if (contentNode == null || !contentNode.isArray()) {
            return result;
        }

        for (JsonNode block : contentNode) {
            if (!"image".equalsIgnoreCase(block.path("type").asText())) {
                if ("document".equalsIgnoreCase(block.path("type").asText())) {
                    JsonNode source = block.path("source");
                    String fileId = source.path("file_id").asText(null);
                    if (fileId != null && !fileId.isBlank()) {
                        result.add(new ChatExecutionRequest.MediaInput(
                                "file",
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
                    String mimeType = source.path("media_type").asText("application/octet-stream");
                    result.add(new ChatExecutionRequest.MediaInput(
                            "file",
                            mimeType,
                            url,
                            block.path("title").asText(null)
                    ));
                }
                continue;
            }

            JsonNode source = block.path("source");
            String fileId = source.path("file_id").asText(null);
            if (fileId != null && !fileId.isBlank()) {
                String mimeType = source.path("media_type").asText("image/*");
                result.add(new ChatExecutionRequest.MediaInput("image", mimeType, "gateway://" + fileId, null));
                continue;
            }
            String url = source.path("url").asText(null);
            if (url == null || url.isBlank()) {
                url = source.path("uri").asText(null);
            }
            if (url == null || url.isBlank()) {
                continue;
            }

            String mimeType = source.path("media_type").asText("image/*");
            result.add(new ChatExecutionRequest.MediaInput("image", mimeType, url, null));
        }

        return List.copyOf(result);
    }

    private void ensureUserMessage(List<MessageInput> messages) {
        boolean hasUser = messages.stream()
                .anyMatch(message ->
                        "user".equalsIgnoreCase(message.role())
                                && ((message.content() != null && !message.content().isBlank())
                                || (message.media() != null && !message.media().isEmpty()))
                );
        if (!hasUser) {
            throw new IllegalArgumentException("至少需要一条 user 消息。");
        }
    }

    private List<GatewayToolDefinition> toTools(List<AnthropicMessagesRequest.Tool> tools) {
        List<GatewayToolDefinition> result = new ArrayList<>();
        if (tools == null) {
            return result;
        }
        for (AnthropicMessagesRequest.Tool tool : tools) {
            if (tool.name() == null || tool.name().isBlank()) {
                continue;
            }
            result.add(new GatewayToolDefinition(
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
