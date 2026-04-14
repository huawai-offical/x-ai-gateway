package com.prodigalgal.xaigateway.gateway.core.execution;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalContentPart;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalPartType;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalReasoningConfig;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResponse;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEventType;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolCall;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalUsage;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileContent;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Service
public class OllamaGatewayChatRuntime implements GatewayChatRuntime {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final GatewayFileService gatewayFileService;

    public OllamaGatewayChatRuntime(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            GatewayFileService gatewayFileService) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.gatewayFileService = gatewayFileService;
    }

    @Override
    public boolean supports(CatalogCandidateView candidate) {
        return candidate.providerType() == ProviderType.OLLAMA_DIRECT;
    }

    @Override
    public CanonicalResponse execute(GatewayChatRuntimeContext context) {
        ensureSupportedRequest(context.canonicalRequest());
        ObjectNode body = buildBody(context, false);
        JsonNode response = client(context).post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        if (response == null) {
            throw new IllegalStateException("Ollama 响应为空。");
        }
        int promptTokens = response.path("prompt_eval_count").asInt(0);
        int completionTokens = response.path("eval_count").asInt(0);
        return new CanonicalResponse(
                null,
                context.selectionResult().publicModel(),
                extractText(response),
                extractReasoning(response),
                extractToolCalls(response),
                new CanonicalUsage(true, promptTokens, completionTokens, promptTokens + completionTokens, 0, 0, 0),
                GatewayFinishReason.fromRaw(firstNonBlank(response.path("done_reason").asText(null), response.path("stop_reason").asText(null), "stop"))
        );
    }

    @Override
    public Flux<CanonicalStreamEvent> executeStream(GatewayChatRuntimeContext context) {
        ensureSupportedRequest(context.canonicalRequest());
        ObjectNode body = buildBody(context, true);
        return client(context).post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(this::decodeChunk);
    }

    private WebClient client(GatewayChatRuntimeContext context) {
        WebClient.Builder builder = webClientBuilder.clone()
                .baseUrl(context.credential().getBaseUrl().replaceAll("/+$", ""));
        if (context.apiKey() != null && !context.apiKey().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + context.apiKey());
        }
        return builder.build();
    }

    private void ensureSupportedRequest(CanonicalRequest request) {
        for (CanonicalMessage message : request.messages()) {
            if (message.parts() == null || message.parts().isEmpty()) {
                continue;
            }
            for (CanonicalContentPart media : message.parts()) {
                if (media.type() == CanonicalPartType.TEXT || media.type() == CanonicalPartType.TOOL_RESULT) {
                    continue;
                }
                if (media.type() == CanonicalPartType.FILE) {
                    throw new IllegalArgumentException("当前 Ollama 运行时暂不支持通用 file/document input。");
                }
                if (media.type() != CanonicalPartType.IMAGE) {
                    throw new IllegalArgumentException("当前 Ollama 运行时仅支持图片输入。");
                }
                if (media.uri() == null || media.uri().isBlank()) {
                    throw new IllegalArgumentException("Ollama 图片输入缺少可用 URL。");
                }
                if (!media.uri().startsWith("gateway://") && !media.uri().startsWith("data:")) {
                    throw new IllegalArgumentException("当前 Ollama 运行时仅支持 gateway:// 或 data URL 图片输入。");
                }
            }
        }
    }

    private ObjectNode buildBody(GatewayChatRuntimeContext context, boolean stream) {
        CanonicalRequest request = context.canonicalRequest();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", context.selectionResult().resolvedModelKey());
        body.put("stream", stream);
        Object think = resolveThinkingControl(request.reasoning());
        if (think instanceof Boolean thinkingEnabled) {
            body.put("think", thinkingEnabled);
        } else if (think instanceof String effort) {
            body.put("think", effort);
        }
        if (request.temperature() != null || request.maxTokens() != null) {
            ObjectNode options = body.putObject("options");
            if (request.temperature() != null) {
                options.put("temperature", request.temperature());
            }
            if (request.maxTokens() != null) {
                options.put("num_predict", request.maxTokens());
            }
        }
        ArrayNode messages = body.putArray("messages");
        for (CanonicalMessage message : request.messages()) {
            String text = joinText(message);
            List<CanonicalContentPart> mediaParts = mediaParts(message);
            CanonicalContentPart toolResult = toolResult(message);
            boolean hasText = text != null && !text.isBlank();
            boolean hasMedia = !mediaParts.isEmpty();
            if (!hasText && !hasMedia) {
                continue;
            }
            ObjectNode item = messages.addObject();
            item.put("role", normalizeRole(message));
            if (hasText) {
                item.put("content", text);
            }
            ArrayNode encodedImages = encodeImages(message, context);
            if (!encodedImages.isEmpty()) {
                item.set("images", encodedImages);
            }
            if (toolResult != null && toolResult.toolName() != null && !toolResult.toolName().isBlank()) {
                item.put("name", toolResult.toolName());
            }
            if (toolResult != null && toolResult.toolCallId() != null && !toolResult.toolCallId().isBlank()) {
                item.put("tool_call_id", toolResult.toolCallId());
            }
        }
        if (request.tools() != null && !request.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (var tool : request.tools()) {
                ObjectNode toolNode = tools.addObject();
                toolNode.put("type", "function");
                ObjectNode function = toolNode.putObject("function");
                function.put("name", tool.name());
                if (tool.description() != null && !tool.description().isBlank()) {
                    function.put("description", tool.description());
                }
                function.set("parameters", tool.inputSchema() == null
                        ? objectMapper.createObjectNode().put("type", "object")
                        : tool.inputSchema());
            }
        }
        return body;
    }

    private String normalizeRole(CanonicalMessage message) {
        if (message.role() == null) {
            return "user";
        }
        return switch (message.role()) {
            case ASSISTANT -> "assistant";
            case SYSTEM -> "system";
            case TOOL -> "tool";
            case USER -> "user";
        };
    }

    private ArrayNode encodeImages(CanonicalMessage message, GatewayChatRuntimeContext context) {
        ArrayNode images = objectMapper.createArrayNode();
        if (message.parts() == null || message.parts().isEmpty()) {
            return images;
        }
        for (CanonicalContentPart media : message.parts()) {
            if (media.type() != CanonicalPartType.IMAGE) {
                continue;
            }
            images.add(encodeImage(media, context));
        }
        return images;
    }

    private String encodeImage(CanonicalContentPart media, GatewayChatRuntimeContext context) {
        if (media.uri().startsWith("gateway://")) {
            String fileKey = media.uri().substring("gateway://".length());
            GatewayFileContent content = gatewayFileService.getFileContent(fileKey, context.selectionResult().distributedKeyId());
            String mimeType = content.mimeType() == null || content.mimeType().isBlank() ? media.mimeType() : content.mimeType();
            if (mimeType == null || !mimeType.toLowerCase().startsWith("image/")) {
                throw new IllegalArgumentException("当前 Ollama 运行时仅支持图片类型的 gateway:// 文件输入。");
            }
            return Base64.getEncoder().encodeToString(content.bytes());
        }
        if (media.uri().startsWith("data:")) {
            int commaIndex = media.uri().indexOf(',');
            if (commaIndex < 0) {
                throw new IllegalArgumentException("无法解析 data URL 图片输入。");
            }
            String metadata = media.uri().substring(5, commaIndex);
            String payload = media.uri().substring(commaIndex + 1);
            if (metadata.contains(";base64")) {
                return payload;
            }
            return Base64.getEncoder().encodeToString(URLDecoder.decode(payload, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));
        }
        throw new IllegalArgumentException("当前 Ollama 运行时不支持远程 URL 图片输入。");
    }

    private Object resolveThinkingControl(CanonicalReasoningConfig reasoning) {
        if (reasoning == null) {
            return null;
        }
        Object mappedEffort = mapThinkingValue(reasoning.effort());
        if (mappedEffort != null) {
            return mappedEffort;
        }
        JsonNode rawReasoning = reasoning.rawSettings();
        if (rawReasoning == null || rawReasoning.isNull() || rawReasoning.isMissingNode()) {
            return null;
        }
        if (rawReasoning.isBoolean()) {
            return rawReasoning.asBoolean();
        }
        if (rawReasoning.isTextual()) {
            return mapThinkingValue(rawReasoning.asText());
        }
        if (rawReasoning.isObject()) {
            Object nested = mapThinkingValue(rawReasoning.path("effort").asText(null));
            if (nested != null) {
                return nested;
            }
            JsonNode enabled = rawReasoning.path("enabled");
            if (enabled.isBoolean()) {
                return enabled.asBoolean();
            }
            return Boolean.TRUE;
        }
        return null;
    }

    private Object mapThinkingValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toLowerCase()) {
            case "", "auto" -> Boolean.TRUE;
            case "none", "off", "disabled" -> Boolean.FALSE;
            case "low", "medium", "high" -> value.trim().toLowerCase();
            default -> Boolean.TRUE;
        };
    }

    private String extractText(JsonNode payload) {
        String text = payload.path("message").path("content").asText(null);
        if (text != null && !text.isBlank()) {
            return text;
        }
        text = payload.path("response").asText(null);
        return text == null || text.isBlank() ? null : text;
    }

    private String extractReasoning(JsonNode payload) {
        String reasoning = payload.path("message").path("thinking").asText(null);
        if (reasoning != null && !reasoning.isBlank()) {
            return reasoning;
        }
        reasoning = payload.path("thinking").asText(null);
        return reasoning == null || reasoning.isBlank() ? null : reasoning;
    }

    private List<CanonicalToolCall> extractToolCalls(JsonNode payload) {
        JsonNode toolCallsNode = payload.path("message").path("tool_calls");
        if (!toolCallsNode.isArray() || toolCallsNode.isEmpty()) {
            toolCallsNode = payload.path("tool_calls");
        }
        if (!toolCallsNode.isArray() || toolCallsNode.isEmpty()) {
            return List.of();
        }
        List<CanonicalToolCall> toolCalls = new ArrayList<>();
        for (JsonNode item : toolCallsNode) {
            JsonNode function = item.path("function");
            String name = function.path("name").asText(item.path("name").asText(null));
            if (name == null || name.isBlank()) {
                continue;
            }
            JsonNode argumentsNode = function.has("arguments") ? function.get("arguments") : item.get("arguments");
            String arguments = null;
            if (argumentsNode != null && !argumentsNode.isNull()) {
                arguments = argumentsNode.isTextual() ? argumentsNode.asText() : argumentsNode.toString();
            }
            toolCalls.add(new CanonicalToolCall(
                    firstNonBlank(item.path("id").asText(null), item.path("call_id").asText(null), "call_" + (toolCalls.size() + 1)),
                    item.path("type").asText("function"),
                    name,
                    arguments
            ));
        }
        return List.copyOf(toolCalls);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Flux<CanonicalStreamEvent> decodeChunk(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return Flux.empty();
        }
        String[] lines = rawBody.split("\\r?\\n");
        List<CanonicalStreamEvent> chunks = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            try {
                JsonNode payload = objectMapper.readTree(line);
                String content = extractText(payload);
                String reasoning = extractReasoning(payload);
                List<CanonicalToolCall> toolCalls = extractToolCalls(payload);
                if ((content != null && !content.isBlank())
                        || (reasoning != null && !reasoning.isBlank())
                        || !toolCalls.isEmpty()) {
                    chunks.add(new CanonicalStreamEvent(
                            !toolCalls.isEmpty() ? CanonicalStreamEventType.TOOL_CALLS
                                    : content != null && !content.isBlank()
                                    ? CanonicalStreamEventType.TEXT_DELTA
                                    : CanonicalStreamEventType.REASONING_DELTA,
                            content == null || content.isBlank() ? null : content,
                            reasoning == null || reasoning.isBlank() ? null : reasoning,
                            toolCalls,
                            CanonicalUsage.empty(),
                            false,
                            null,
                            null,
                            null
                    ));
                }
                if (payload.path("done").asBoolean(false)) {
                    int promptTokens = payload.path("prompt_eval_count").asInt(0);
                    int completionTokens = payload.path("eval_count").asInt(0);
                    chunks.add(new CanonicalStreamEvent(
                            CanonicalStreamEventType.COMPLETED,
                            null,
                            null,
                            List.of(),
                            new CanonicalUsage(true, promptTokens, completionTokens, promptTokens + completionTokens, 0, 0, 0),
                            true,
                            GatewayFinishReason.fromRaw(firstNonBlank(payload.path("done_reason").asText(null), payload.path("stop_reason").asText(null), "stop")),
                            extractText(payload),
                            extractReasoning(payload)
                    ));
                }
            } catch (Exception exception) {
                throw new IllegalStateException("无法解析 Ollama stream 响应。", exception);
            }
        }
        return Flux.fromIterable(chunks);
    }

    private List<CanonicalContentPart> mediaParts(CanonicalMessage message) {
        return message.parts().stream()
                .filter(part -> part.type() == CanonicalPartType.IMAGE || part.type() == CanonicalPartType.FILE)
                .toList();
    }

    private CanonicalContentPart toolResult(CanonicalMessage message) {
        return message.parts().stream()
                .filter(part -> part.type() == CanonicalPartType.TOOL_RESULT)
                .findFirst()
                .orElse(null);
    }

    private String joinText(CanonicalMessage message) {
        return message.parts().stream()
                .filter(part -> part.type() == CanonicalPartType.TEXT)
                .map(CanonicalContentPart::text)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }
}
