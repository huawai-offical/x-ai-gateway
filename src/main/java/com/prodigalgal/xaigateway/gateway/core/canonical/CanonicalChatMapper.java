package com.prodigalgal.xaigateway.gateway.core.canonical;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolDefinition;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CanonicalChatMapper {

    private final ObjectMapper objectMapper;

    public CanonicalChatMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CanonicalRequest toCanonicalRequest(ChatExecutionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("ChatExecutionRequest 不能为空。");
        }
        return new CanonicalRequest(
                request.distributedKeyPrefix(),
                CanonicalIngressProtocol.from(request.protocol()),
                request.requestPath(),
                request.requestedModel(),
                toMessages(request.messages()),
                toTools(request.tools()),
                request.toolChoice(),
                request.temperature(),
                request.maxTokens(),
                toReasoningConfig(request.executionMetadata()),
                copyObject(request.executionMetadata())
        );
    }

    private List<CanonicalMessage> toMessages(List<ChatExecutionRequest.MessageInput> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
                .map(this::toMessage)
                .filter(message -> message.parts() != null && !message.parts().isEmpty())
                .toList();
    }

    private CanonicalMessage toMessage(ChatExecutionRequest.MessageInput message) {
        CanonicalMessageRole role = CanonicalMessageRole.from(message.role());
        List<CanonicalContentPart> parts = new ArrayList<>();

        if (role == CanonicalMessageRole.TOOL) {
            parts.add(CanonicalContentPart.toolResult(message.toolCallId(), message.toolName(), message.content() == null ? "" : message.content()));
            return new CanonicalMessage(role, List.copyOf(parts));
        }

        if (message.content() != null && !message.content().isBlank()) {
            parts.add(CanonicalContentPart.text(message.content()));
        }
        if (message.media() != null) {
            for (ChatExecutionRequest.MediaInput media : message.media()) {
                if (media == null || media.url() == null || media.url().isBlank()) {
                    continue;
                }
                if ("file".equalsIgnoreCase(media.kind())) {
                    parts.add(CanonicalContentPart.file(media.mimeType(), media.url(), media.name()));
                } else {
                    parts.add(CanonicalContentPart.image(media.mimeType(), media.url(), media.name()));
                }
            }
        }
        return new CanonicalMessage(role, List.copyOf(parts));
    }

    private List<CanonicalToolDefinition> toTools(List<GatewayToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        return tools.stream()
                .map(tool -> new CanonicalToolDefinition(
                        tool.name(),
                        tool.description(),
                        tool.inputSchema(),
                        tool.strict()
                ))
                .toList();
    }

    private CanonicalReasoningConfig toReasoningConfig(JsonNode executionMetadata) {
        if (executionMetadata == null || !executionMetadata.isObject()) {
            return null;
        }
        JsonNode rawReasoning = executionMetadata.get("reasoning");
        String effort = executionMetadata.path("reasoning_effort").asText(null);
        if ((rawReasoning == null || rawReasoning.isNull()) && (effort == null || effort.isBlank())) {
            return null;
        }
        return new CanonicalReasoningConfig(rawReasoning, effort);
    }

    private JsonNode copyObject(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        JsonNode copied = node.deepCopy();
        return copied instanceof ObjectNode ? copied : objectMapper.createObjectNode();
    }
}
