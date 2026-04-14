package com.prodigalgal.xaigateway.gateway.core.canonical;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolDefinition;
import java.util.ArrayList;
import java.util.List;

public class CanonicalChatExecutionRequestAdapter {

    public ChatExecutionRequest toExecutionRequest(CanonicalRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("CanonicalRequest 不能为空。");
        }
        return new ChatExecutionRequest(
                request.distributedKeyPrefix(),
                toProtocol(request.ingressProtocol()),
                request.requestPath(),
                request.requestedModel(),
                toMessages(request.messages()),
                toTools(request.tools()),
                request.toolChoice(),
                request.temperature(),
                request.maxTokens(),
                buildExecutionMetadata(request)
        );
    }

    private List<ChatExecutionRequest.MessageInput> toMessages(List<CanonicalMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<ChatExecutionRequest.MessageInput> result = new ArrayList<>();
        for (CanonicalMessage message : messages) {
            if (message == null || message.parts() == null || message.parts().isEmpty()) {
                continue;
            }
            CanonicalMessageRole role = message.role() == null ? CanonicalMessageRole.USER : message.role();
            if (role == CanonicalMessageRole.TOOL) {
                CanonicalContentPart toolResult = message.parts().stream()
                        .filter(part -> part.type() == CanonicalPartType.TOOL_RESULT)
                        .findFirst()
                        .orElse(null);
                if (toolResult == null) {
                    continue;
                }
                result.add(new ChatExecutionRequest.MessageInput(
                        "tool",
                        toolResult.text(),
                        toolResult.toolCallId(),
                        toolResult.toolName(),
                        List.of()
                ));
                continue;
            }

            String text = message.parts().stream()
                    .filter(part -> part.type() == CanonicalPartType.TEXT)
                    .map(CanonicalContentPart::text)
                    .filter(value -> value != null && !value.isBlank())
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse(null);
            List<ChatExecutionRequest.MediaInput> media = message.parts().stream()
                    .filter(part -> part.type() == CanonicalPartType.IMAGE || part.type() == CanonicalPartType.FILE)
                    .map(part -> new ChatExecutionRequest.MediaInput(
                            part.type() == CanonicalPartType.FILE ? "file" : "image",
                            part.mimeType(),
                            part.uri(),
                            part.name()
                    ))
                    .toList();
            result.add(new ChatExecutionRequest.MessageInput(
                    normalizeRole(role),
                    text,
                    null,
                    null,
                    media
            ));
        }
        return List.copyOf(result);
    }

    private List<GatewayToolDefinition> toTools(List<CanonicalToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        return tools.stream()
                .map(tool -> new GatewayToolDefinition(
                        tool.name(),
                        tool.description(),
                        tool.inputSchema(),
                        tool.strict()
                ))
                .toList();
    }

    private JsonNode buildExecutionMetadata(CanonicalRequest request) {
        ObjectNode metadata = request.providerExtensions() instanceof ObjectNode objectNode
                ? objectNode.deepCopy()
                : JsonNodeFactory.instance.objectNode();
        if (request.reasoning() != null) {
            if (request.reasoning().rawSettings() != null && !request.reasoning().rawSettings().isNull()) {
                metadata.set("reasoning", request.reasoning().rawSettings());
            }
            if (request.reasoning().effort() != null && !request.reasoning().effort().isBlank()) {
                metadata.put("reasoning_effort", request.reasoning().effort());
            }
        }
        return metadata.isEmpty() ? null : metadata;
    }

    private String toProtocol(CanonicalIngressProtocol protocol) {
        if (protocol == null) {
            return "openai";
        }
        return switch (protocol) {
            case OPENAI -> "openai";
            case RESPONSES -> "responses";
            case ANTHROPIC_NATIVE -> "anthropic_native";
            case GOOGLE_NATIVE -> "google_native";
            case UNKNOWN -> "openai";
        };
    }

    private String normalizeRole(CanonicalMessageRole role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
        };
    }
}
