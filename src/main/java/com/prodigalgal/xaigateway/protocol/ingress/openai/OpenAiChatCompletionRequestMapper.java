package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalContentPart;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessageRole;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequestMetadata;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;

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
        return toCanonicalRequest(distributedKey, request, null);
    }

    public CanonicalRequest toCanonicalRequest(
            AuthenticatedDistributedKey distributedKey,
            OpenAiChatCompletionRequest request,
            CanonicalRequestMetadata metadata) {
        List<CanonicalMessage> messages = toMessages(request.messages());
        ensureUserMessage(messages);
        validateResponseFormat(request.responseFormat());
        validateModalitiesAndAudio(request.modalities(), request.audio());
        validateWebSearchOptions(request.webSearchOptions());
        ChatToolSemantics toolSemantics = toToolSemantics(request);
        return new CanonicalRequest(
                distributedKey.keyPrefix(),
                CanonicalIngressProtocol.OPENAI,
                "/v1/chat/completions",
                request.model(),
                messages,
                toolSemantics.tools(),
                toolSemantics.toolChoice(),
                request.temperature(),
                request.maxTokens(),
                buildReasoningConfig(request),
                buildExecutionMetadata(request),
                metadata
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

    private ChatToolSemantics toToolSemantics(OpenAiChatCompletionRequest request) {
        return new ChatToolSemantics(
                toTools(request.tools(), request.functions()),
                toToolChoice(request.toolChoice(), request.functionCall())
        );
    }

    private List<CanonicalToolDefinition> toTools(List<OpenAiChatCompletionRequest.Tool> tools, JsonNode functions) {
        Map<String, CanonicalToolDefinition> result = new LinkedHashMap<>();
        if (tools != null) {
            for (OpenAiChatCompletionRequest.Tool tool : tools) {
                if (tool == null || tool.function() == null || tool.function().name() == null || tool.function().name().isBlank()) {
                    continue;
                }
                CanonicalToolDefinition definition = new CanonicalToolDefinition(
                        tool.function().name(),
                        tool.function().description(),
                        tool.function().parameters(),
                        tool.function().strict()
                );
                result.putIfAbsent(definition.name(), definition);
            }
        }

        if (hasJson(functions)) {
            if (!functions.isArray()) {
                throw new IllegalArgumentException("functions 必须是 JSON array。");
            }
            for (JsonNode function : functions) {
                if (function == null || !function.isObject()) {
                    throw new IllegalArgumentException("functions 每一项必须是 JSON object。");
                }
                String name = function.path("name").asText(null);
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException("functions 每一项都必须包含 name。");
                }
                CanonicalToolDefinition definition = new CanonicalToolDefinition(
                        name,
                        function.path("description").asText(null),
                        function.path("parameters").isMissingNode() ? null : function.path("parameters"),
                        function.path("strict").isBoolean() ? function.path("strict").asBoolean() : null
                );
                result.putIfAbsent(definition.name(), definition);
            }
        }
        return List.copyOf(result.values());
    }

    private JsonNode toToolChoice(JsonNode toolChoice, JsonNode functionCall) {
        if (hasJson(toolChoice)) {
            return toolChoice;
        }
        if (!hasJson(functionCall)) {
            return null;
        }
        if (functionCall.isTextual()) {
            return functionCall;
        }
        if (!functionCall.isObject()) {
            throw new IllegalArgumentException("function_call 必须是 auto、none 或包含 name 的 JSON object。");
        }
        String name = functionCall.path("name").asText(null);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("function_call object 必须包含 name。");
        }
        ObjectNode choice = objectMapper.createObjectNode();
        choice.put("type", "function");
        choice.set("function", objectMapper.createObjectNode().put("name", name));
        return choice;
    }

    private boolean hasJson(JsonNode value) {
        return value != null && !value.isNull() && !value.isMissingNode();
    }

    private void validateResponseFormat(JsonNode responseFormat) {
        if (!hasJson(responseFormat)) {
            return;
        }
        if (!responseFormat.isObject()) {
            throw new IllegalArgumentException("response_format 必须是 JSON object。");
        }
        String type = responseFormat.path("type").asText(null);
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("response_format.type 不能为空。");
        }
        if ("text".equals(type) || "json_object".equals(type)) {
            return;
        }
        if (!"json_schema".equals(type)) {
            throw new IllegalArgumentException("response_format.type 只支持 text、json_object 或 json_schema。");
        }
        JsonNode jsonSchema = responseFormat.path("json_schema");
        if (!jsonSchema.isObject()) {
            throw new IllegalArgumentException("response_format.json_schema 必须是 JSON object。");
        }
        String name = jsonSchema.path("name").asText(null);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("response_format.json_schema.name 不能为空。");
        }
        if (!jsonSchema.path("schema").isObject()) {
            throw new IllegalArgumentException("response_format.json_schema.schema 必须是 JSON object。");
        }
    }

    private void validateModalitiesAndAudio(JsonNode modalities, JsonNode audio) {
        boolean requestsAudio = false;
        if (hasJson(modalities)) {
            if (!modalities.isArray()) {
                throw new IllegalArgumentException("modalities 必须是 JSON array。");
            }
            for (JsonNode item : modalities) {
                if (item == null || !item.isTextual()) {
                    throw new IllegalArgumentException("modalities 每一项必须是 text 或 audio。");
                }
                String value = item.asText();
                if ("audio".equals(value)) {
                    requestsAudio = true;
                    continue;
                }
                if (!"text".equals(value)) {
                    throw new IllegalArgumentException("modalities 每一项必须是 text 或 audio。");
                }
            }
        }
        if (!hasJson(audio)) {
            if (requestsAudio) {
                throw new IllegalArgumentException("modalities 包含 audio 时必须提供 audio 参数。");
            }
            return;
        }
        if (!audio.isObject()) {
            throw new IllegalArgumentException("audio 必须是 JSON object。");
        }
        validateKnownValue(audio, "voice", "audio.voice", List.of("alloy", "ash", "ballad", "coral", "echo", "fable", "onyx", "nova", "sage", "shimmer"));
        validateKnownValue(audio, "format", "audio.format", List.of("mp3", "flac", "opus", "pcm16", "wav"));
    }

    private void validateWebSearchOptions(JsonNode webSearchOptions) {
        if (!hasJson(webSearchOptions)) {
            return;
        }
        if (!webSearchOptions.isObject()) {
            throw new IllegalArgumentException("web_search_options 必须是 JSON object。");
        }
        if (webSearchOptions.has("search_context_size") && !webSearchOptions.get("search_context_size").isNull()) {
            String value = webSearchOptions.get("search_context_size").asText(null);
            if (!List.of("low", "medium", "high").contains(value)) {
                throw new IllegalArgumentException("web_search_options.search_context_size 不支持值 " + value + "。");
            }
        }
        JsonNode userLocation = webSearchOptions.path("user_location");
        if (userLocation.isMissingNode() || userLocation.isNull()) {
            return;
        }
        if (!userLocation.isObject()) {
            throw new IllegalArgumentException("web_search_options.user_location 必须是 JSON object。");
        }
        String type = userLocation.path("type").asText(null);
        if (type != null && !type.isBlank() && !"approximate".equals(type)) {
            throw new IllegalArgumentException("web_search_options.user_location.type 只支持 approximate。");
        }
        JsonNode approximate = userLocation.path("approximate");
        if (!approximate.isMissingNode() && !approximate.isNull() && !approximate.isObject()) {
            throw new IllegalArgumentException("web_search_options.user_location.approximate 必须是 JSON object。");
        }
    }

    private void validateKnownValue(JsonNode source, String field, String fieldName, List<String> supportedValues) {
        String value = source.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空。");
        }
        if (!supportedValues.contains(value)) {
            throw new IllegalArgumentException(fieldName + " 不支持值 " + value + "。");
        }
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
        putJson(metadata, "reasoning", request.reasoning());
        putText(metadata, "reasoning_effort", request.reasoningEffort());
        putBoolean(metadata, "store", request.store());
        putJson(metadata, "metadata", request.metadata());
        putDouble(metadata, "frequency_penalty", request.frequencyPenalty());
        putJson(metadata, "logit_bias", request.logitBias());
        putBoolean(metadata, "logprobs", request.logprobs());
        putInteger(metadata, "top_logprobs", request.topLogprobs());
        putDouble(metadata, "top_p", request.topP());
        putInteger(metadata, "max_completion_tokens", request.maxCompletionTokens());
        putInteger(metadata, "n", request.n());
        putJson(metadata, "modalities", request.modalities());
        putJson(metadata, "audio", request.audio());
        putDouble(metadata, "presence_penalty", request.presencePenalty());
        putJson(metadata, "response_format", request.responseFormat());
        putInteger(metadata, "seed", request.seed());
        putText(metadata, "service_tier", request.serviceTier());
        putJson(metadata, "stop", request.stop());
        putJson(metadata, "stream_options", request.streamOptions());
        putBoolean(metadata, "parallel_tool_calls", request.parallelToolCalls());
        putText(metadata, "user", request.user());
        putJson(metadata, "web_search_options", request.webSearchOptions());
        putText(metadata, "verbosity", request.verbosity());
        putText(metadata, "prompt_cache_key", request.promptCacheKey());
        putText(metadata, "safety_identifier", request.safetyIdentifier());
        putJson(metadata, "prediction", request.prediction());
        putJson(metadata, "functions", request.functions());
        putJson(metadata, "function_call", request.functionCall());
        return metadata.isEmpty() ? null : metadata;
    }

    private void putJson(tools.jackson.databind.node.ObjectNode target, String field, JsonNode value) {
        if (value != null && !value.isNull() && !value.isMissingNode()) {
            target.set(field, value);
        }
    }

    private void putText(tools.jackson.databind.node.ObjectNode target, String field, String value) {
        if (value != null && !value.isBlank()) {
            target.put(field, value);
        }
    }

    private void putBoolean(tools.jackson.databind.node.ObjectNode target, String field, Boolean value) {
        if (value != null) {
            target.put(field, value);
        }
    }

    private void putInteger(tools.jackson.databind.node.ObjectNode target, String field, Integer value) {
        if (value != null) {
            target.put(field, value);
        }
    }

    private void putDouble(tools.jackson.databind.node.ObjectNode target, String field, Double value) {
        if (value != null) {
            target.put(field, value);
        }
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

    private record ChatToolSemantics(
            List<CanonicalToolDefinition> tools,
            JsonNode toolChoice
    ) {
    }
}
