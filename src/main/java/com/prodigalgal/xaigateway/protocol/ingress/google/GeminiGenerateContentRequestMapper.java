package com.prodigalgal.xaigateway.protocol.ingress.google;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalContentPart;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessageRole;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalReasoningConfig;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolDefinition;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GeminiGenerateContentRequestMapper {

    private final ObjectMapper objectMapper;

    public GeminiGenerateContentRequestMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CanonicalRequest toCanonicalRequest(
            String distributedKeyPrefix,
            String model,
            GeminiGenerateContentRequest request,
            boolean stream) {
        return toCanonicalRequest(new AuthenticatedDistributedKey(null, distributedKeyPrefix, distributedKeyPrefix), model, request, stream);
    }

    public CanonicalRequest toCanonicalRequest(
            String distributedKeyPrefix,
            String model,
            JsonNode requestBody,
            boolean stream) {
        try {
            return toCanonicalRequest(
                    distributedKeyPrefix,
                    model,
                    objectMapper.treeToValue(requestBody, GeminiGenerateContentRequest.class),
                    stream
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException("Gemini generateContent 请求体解析失败。", exception);
        }
    }

    public CanonicalRequest toCanonicalRequest(
            AuthenticatedDistributedKey distributedKey,
            String model,
            GeminiGenerateContentRequest request,
            boolean stream) {
        validateManagedTools(request);
        List<CanonicalMessage> messages = toMessages(request.systemInstruction(), request.contents());
        ensureUserMessage(messages);

        Double temperature = request.generationConfig() != null && request.generationConfig().has("temperature")
                ? request.generationConfig().path("temperature").asDouble()
                : null;
        Integer maxTokens = request.generationConfig() != null && request.generationConfig().has("maxOutputTokens")
                ? request.generationConfig().path("maxOutputTokens").asInt()
                : null;

        return new CanonicalRequest(
                distributedKey.keyPrefix(),
                CanonicalIngressProtocol.GOOGLE_NATIVE,
                stream
                        ? "/v1beta/models/" + model + ":streamGenerateContent"
                        : "/v1beta/models/" + model + ":generateContent",
                model,
                messages,
                toTools(request.tools()),
                null,
                temperature,
                maxTokens,
                buildReasoningConfig(request.generationConfig()),
                objectMapper.valueToTree(request)
        );
    }

    private void validateManagedTools(GeminiGenerateContentRequest request) {
        JsonNode tools = request.tools();
        if (tools == null || !tools.isArray()) {
            return;
        }
        for (JsonNode tool : tools) {
            if (tool.has("googleMaps") && !Boolean.TRUE.equals(request.allowGoogleMaps())) {
                throw new IllegalArgumentException("googleMaps grounding 需要 x_ai_gateway_allow_google_maps=true。");
            }
        }
    }

    private CanonicalReasoningConfig buildReasoningConfig(JsonNode generationConfig) {
        if (generationConfig == null || !generationConfig.isObject()) {
            return null;
        }
        JsonNode thinkingConfig = generationConfig.path("thinkingConfig");
        if (thinkingConfig.isObject()) {
            String effort = thinkingConfig.path("thinkingLevel").asText(null);
            if (effort == null || effort.isBlank()) {
                effort = thinkingConfig.has("thinkingBudget") ? "budget:" + thinkingConfig.path("thinkingBudget").asInt() : null;
            }
            return new CanonicalReasoningConfig(thinkingConfig, effort);
        }
        if (generationConfig.has("thinkingBudget") || generationConfig.has("thinkingLevel")) {
            return new CanonicalReasoningConfig(generationConfig, generationConfig.path("thinkingLevel").asText(null));
        }
        return null;
    }

    private List<CanonicalMessage> toMessages(JsonNode systemInstruction, JsonNode contents) {
        List<CanonicalMessage> result = new ArrayList<>();
        String systemPrompt = extractText(systemInstruction);
        if (StringUtils.hasText(systemPrompt)) {
            result.add(new CanonicalMessage(CanonicalMessageRole.SYSTEM, List.of(CanonicalContentPart.text(systemPrompt))));
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
                        result.add(new CanonicalMessage(
                                CanonicalMessageRole.TOOL,
                                List.of(CanonicalContentPart.toolResult(null, toolName, responseText))
                        ));
                        continue;
                    }
                }
                List<CanonicalContentPart> canonicalParts = new ArrayList<>();
                if (StringUtils.hasText(text)) {
                    canonicalParts.add(CanonicalContentPart.text(text));
                }
                canonicalParts.addAll(extractMedia(parts));
                if (canonicalParts.isEmpty()) {
                    continue;
                }
                result.add(new CanonicalMessage(
                        "model".equalsIgnoreCase(role) ? CanonicalMessageRole.ASSISTANT : CanonicalMessageRole.USER,
                        List.copyOf(canonicalParts)
                ));
            }
        }
        return List.copyOf(result);
    }

    private void ensureUserMessage(List<CanonicalMessage> messages) {
        boolean hasUser = messages.stream()
                .anyMatch(message -> message.role() == CanonicalMessageRole.USER
                        && message.parts() != null
                        && !message.parts().isEmpty());
        if (!hasUser) {
            throw new IllegalArgumentException("至少需要一条带 text 的 user content。");
        }
    }

    private List<CanonicalToolDefinition> toTools(JsonNode toolsNode) {
        List<CanonicalToolDefinition> result = new ArrayList<>();
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
                result.add(new CanonicalToolDefinition(
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

    private List<CanonicalContentPart> extractMedia(JsonNode parts) {
        List<CanonicalContentPart> result = new ArrayList<>();
        if (parts == null || !parts.isArray()) {
            return result;
        }

        for (JsonNode part : parts) {
            JsonNode fileData = part.path("fileData");
            if (!fileData.isMissingNode() && !fileData.isNull()) {
                String fileId = fileData.path("fileId").asText(null);
                if (fileId != null && !fileId.isBlank()) {
                    result.add(fileData.path("mimeType").asText("").startsWith("image/")
                            ? CanonicalContentPart.image(fileData.path("mimeType").asText("image/*"), "gateway://" + fileId, null)
                            : CanonicalContentPart.file(fileData.path("mimeType").asText("application/octet-stream"), "gateway://" + fileId, null));
                    continue;
                }
                String uri = fileData.path("fileUri").asText(null);
                if (uri != null && !uri.isBlank()) {
                    result.add(fileData.path("mimeType").asText("").startsWith("image/")
                            ? CanonicalContentPart.image(fileData.path("mimeType").asText("image/*"), uri, null)
                            : CanonicalContentPart.file(fileData.path("mimeType").asText("application/octet-stream"), uri, null));
                }
            }
        }

        return List.copyOf(result);
    }
}
