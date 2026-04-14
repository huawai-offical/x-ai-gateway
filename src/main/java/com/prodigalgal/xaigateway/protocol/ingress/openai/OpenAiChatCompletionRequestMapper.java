package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest.MessageInput;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolDefinition;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OpenAiChatCompletionRequestMapper {

    private final ObjectMapper objectMapper;

    public OpenAiChatCompletionRequestMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ChatExecutionRequest toExecutionRequest(
            AuthenticatedDistributedKey distributedKey,
            OpenAiChatCompletionRequest request) {
        List<MessageInput> messages = toMessages(request.messages());
        ensureUserMessage(messages);

        return new ChatExecutionRequest(
                distributedKey.keyPrefix(),
                "openai",
                "/v1/chat/completions",
                request.model(),
                messages,
                toTools(request.tools()),
                request.toolChoice(),
                request.temperature(),
                request.maxTokens(),
                buildExecutionMetadata(request)
        );
    }

    private List<MessageInput> toMessages(List<OpenAiChatCompletionRequest.Message> messages) {
        List<MessageInput> result = new ArrayList<>();
        if (messages == null) {
            return result;
        }
        for (OpenAiChatCompletionRequest.Message message : messages) {
            ParsedMessageContent parsed = parseMessageContent(message.content());
            if ((parsed.text() == null || parsed.text().isBlank()) && parsed.media().isEmpty()) {
                continue;
            }
            String toolName = null;
            if ("tool".equalsIgnoreCase(message.role()) && message.toolCallId() != null) {
                toolName = "tool";
            }
            result.add(new MessageInput(message.role(), parsed.text(), message.toolCallId(), toolName, parsed.media()));
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

    private List<GatewayToolDefinition> toTools(List<OpenAiChatCompletionRequest.Tool> tools) {
        List<GatewayToolDefinition> result = new ArrayList<>();
        if (tools == null) {
            return result;
        }
        for (OpenAiChatCompletionRequest.Tool tool : tools) {
            if (tool == null || tool.function() == null || tool.function().name() == null || tool.function().name().isBlank()) {
                continue;
            }
            result.add(new GatewayToolDefinition(
                    tool.function().name(),
                    tool.function().description(),
                    tool.function().parameters(),
                    tool.function().strict()
            ));
        }
        return List.copyOf(result);
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

    private ParsedMessageContent parseMessageContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isNull() || contentNode.isMissingNode()) {
            return new ParsedMessageContent("", List.of());
        }

        if (contentNode.isTextual()) {
            return new ParsedMessageContent(contentNode.asText(), List.of());
        }

        if (contentNode.isArray()) {
            String text = "";
            List<ChatExecutionRequest.MediaInput> media = new ArrayList<>();
            for (JsonNode item : contentNode) {
                String type = item.path("type").asText();
                if ("text".equalsIgnoreCase(type)) {
                    text = item.path("text").asText(text);
                }
                if ("image_url".equalsIgnoreCase(type)) {
                    String url = item.path("image_url").path("url").asText();
                    if (url != null && !url.isBlank()) {
                        media.add(new ChatExecutionRequest.MediaInput("image", "image/*", url, null));
                    }
                }
                if ("input_file".equalsIgnoreCase(type)) {
                    JsonNode inputFile = item.path("input_file");
                    String fileId = inputFile.path("file_id").asText(null);
                    if (fileId != null && !fileId.isBlank()) {
                        media.add(new ChatExecutionRequest.MediaInput(
                                "file",
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
                        media.add(new ChatExecutionRequest.MediaInput(
                                "file",
                                inputFile.path("mime_type").asText("application/octet-stream"),
                                url,
                                inputFile.path("filename").asText(null)
                        ));
                    }
                }
            }
            return new ParsedMessageContent(text, List.copyOf(media));
        }

        return new ParsedMessageContent(contentNode.toString(), List.of());
    }

    private record ParsedMessageContent(
            String text,
            List<ChatExecutionRequest.MediaInput> media
    ) {
    }
}
