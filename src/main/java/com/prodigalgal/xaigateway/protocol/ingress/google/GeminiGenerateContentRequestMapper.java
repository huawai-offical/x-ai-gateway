package com.prodigalgal.xaigateway.protocol.ingress.google;

import tools.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest.MessageInput;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolDefinition;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GeminiGenerateContentRequestMapper {

    public ChatExecutionRequest toExecutionRequest(
            AuthenticatedDistributedKey distributedKey,
            String model,
            GeminiGenerateContentRequest request,
            boolean stream) {
        List<MessageInput> messages = toMessages(request.systemInstruction(), request.contents());
        ensureUserMessage(messages);

        Double temperature = request.generationConfig() != null && request.generationConfig().has("temperature")
                ? request.generationConfig().path("temperature").asDouble()
                : null;
        Integer maxTokens = request.generationConfig() != null && request.generationConfig().has("maxOutputTokens")
                ? request.generationConfig().path("maxOutputTokens").asInt()
                : null;

        return new ChatExecutionRequest(
                distributedKey.keyPrefix(),
                "google_native",
                stream
                        ? "/v1beta/models/" + model + ":streamGenerateContent"
                        : "/v1beta/models/" + model + ":generateContent",
                model,
                messages,
                toTools(request.tools()),
                null,
                temperature,
                maxTokens
        );
    }

    private List<MessageInput> toMessages(JsonNode systemInstruction, JsonNode contents) {
        List<MessageInput> result = new ArrayList<>();
        String systemPrompt = extractText(systemInstruction);
        if (StringUtils.hasText(systemPrompt)) {
            result.add(new MessageInput("system", systemPrompt, null, null, List.of()));
        }

        if (contents != null && contents.isArray()) {
            for (JsonNode content : contents) {
                String role = content.path("role").asText();
                JsonNode parts = content.path("parts");
                String text = extractText(parts);
                if (!StringUtils.hasText(text)) {
                    JsonNode functionResponse = parts.findValue("functionResponse");
                    if (functionResponse != null && !functionResponse.isMissingNode()) {
                        String toolName = functionResponse.path("name").asText("tool");
                        String responseText = functionResponse.path("response").toString();
                        result.add(new MessageInput("tool", responseText, null, toolName, List.of()));
                        continue;
                    }
                }
                List<ChatExecutionRequest.MediaInput> media = extractMedia(parts);
                if (!StringUtils.hasText(text) && media.isEmpty()) {
                    continue;
                }
                result.add(new MessageInput("model".equalsIgnoreCase(role) ? "assistant" : "user", text, null, null, media));
            }
        }
        return List.copyOf(result);
    }

    private void ensureUserMessage(List<MessageInput> messages) {
        boolean hasUser = messages.stream()
                .anyMatch(message ->
                        "user".equalsIgnoreCase(message.role())
                                && (StringUtils.hasText(message.content())
                                || (message.media() != null && !message.media().isEmpty()))
                );
        if (!hasUser) {
            throw new IllegalArgumentException("至少需要一条带 text 的 user content。");
        }
    }

    private List<GatewayToolDefinition> toTools(JsonNode toolsNode) {
        List<GatewayToolDefinition> result = new ArrayList<>();
        if (toolsNode == null || !toolsNode.isArray()) {
            return result;
        }

        for (JsonNode toolNode : toolsNode) {
            JsonNode functionDeclarations = toolNode.path("functionDeclarations");
            if (!functionDeclarations.isArray()) {
                continue;
            }

            for (JsonNode function : functionDeclarations) {
                String name = function.path("name").asText();
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                result.add(new GatewayToolDefinition(
                        name,
                        function.path("description").asText(null),
                        function.path("parameters").isMissingNode() ? null : function.path("parameters"),
                        null
                ));
            }
        }

        return List.copyOf(result);
    }

    private String extractText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }

        if (node.isTextual()) {
            return node.asText();
        }

        if (node.has("text")) {
            return node.path("text").asText();
        }

        if (node.has("parts")) {
            return node.path("parts").findValues("text").stream()
                    .map(JsonNode::asText)
                    .reduce((first, second) -> second)
                    .orElse(null);
        }

        if (node.isArray()) {
            return node.findValues("text").stream()
                    .map(JsonNode::asText)
                    .reduce((first, second) -> second)
                    .orElse(null);
        }

        return null;
    }

    private List<ChatExecutionRequest.MediaInput> extractMedia(JsonNode parts) {
        List<ChatExecutionRequest.MediaInput> result = new ArrayList<>();
        if (parts == null || !parts.isArray()) {
            return result;
        }

        for (JsonNode part : parts) {
            JsonNode fileData = part.path("fileData");
            if (!fileData.isMissingNode() && !fileData.isNull()) {
                String fileId = fileData.path("fileId").asText(null);
                if (fileId != null && !fileId.isBlank()) {
                    result.add(new ChatExecutionRequest.MediaInput(
                            fileData.path("mimeType").asText("").startsWith("image/") ? "image" : "file",
                            fileData.path("mimeType").asText("application/octet-stream"),
                            "gateway://" + fileId,
                            null
                    ));
                    continue;
                }
                String uri = fileData.path("fileUri").asText(null);
                if (uri != null && !uri.isBlank()) {
                    result.add(new ChatExecutionRequest.MediaInput(
                            fileData.path("mimeType").asText("").startsWith("image/") ? "image" : "file",
                            fileData.path("mimeType").asText("application/octet-stream"),
                            uri,
                            null
                    ));
                }
            }
        }

        return List.copyOf(result);
    }
}
