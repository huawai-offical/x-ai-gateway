package com.prodigalgal.xaigateway.gateway.core.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileContent;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
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
    public GatewayChatRuntimeResult execute(GatewayChatRuntimeContext context) {
        ensureSupportedRequest(context.request());
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
        return new GatewayChatRuntimeResult(
                extractText(response),
                new GatewayUsage(promptTokens, promptTokens, completionTokens, 0, 0, 0, 0, 0, null, promptTokens + completionTokens, response),
                extractToolCalls(response),
                extractReasoning(response)
        );
    }

    @Override
    public Flux<ChatExecutionStreamChunk> executeStream(GatewayChatRuntimeContext context) {
        ensureSupportedRequest(context.request());
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

    private void ensureSupportedRequest(ChatExecutionRequest request) {
        for (ChatExecutionRequest.MessageInput message : request.messages()) {
            if (message.media() == null || message.media().isEmpty()) {
                continue;
            }
            for (ChatExecutionRequest.MediaInput media : message.media()) {
                if ("file".equalsIgnoreCase(media.kind())) {
                    throw new IllegalArgumentException("当前 Ollama 运行时暂不支持通用 file/document input。");
                }
                if (!"image".equalsIgnoreCase(media.kind())) {
                    throw new IllegalArgumentException("当前 Ollama 运行时仅支持图片输入。");
                }
                if (media.url() == null || media.url().isBlank()) {
                    throw new IllegalArgumentException("Ollama 图片输入缺少可用 URL。");
                }
                if (!media.url().startsWith("gateway://") && !media.url().startsWith("data:")) {
                    throw new IllegalArgumentException("当前 Ollama 运行时仅支持 gateway:// 或 data URL 图片输入。");
                }
            }
        }
    }

    private ObjectNode buildBody(GatewayChatRuntimeContext context, boolean stream) {
        ChatExecutionRequest request = context.request();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", context.selectionResult().resolvedModelKey());
        body.put("stream", stream);
        Object think = resolveThinkingControl(request.executionMetadata());
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
        for (ChatExecutionRequest.MessageInput message : request.messages()) {
            boolean hasText = message.content() != null && !message.content().isBlank();
            boolean hasMedia = message.media() != null && !message.media().isEmpty();
            if (!hasText && !hasMedia) {
                continue;
            }
            ObjectNode item = messages.addObject();
            item.put("role", normalizeRole(message.role()));
            if (hasText) {
                item.put("content", message.content());
            }
            ArrayNode encodedImages = encodeImages(message, context);
            if (!encodedImages.isEmpty()) {
                item.set("images", encodedImages);
            }
            if (message.toolName() != null && !message.toolName().isBlank()) {
                item.put("name", message.toolName());
            }
            if (message.toolCallId() != null && !message.toolCallId().isBlank()) {
                item.put("tool_call_id", message.toolCallId());
            }
        }
        if (request.tools() != null && !request.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (GatewayToolDefinition tool : request.tools()) {
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

    private String normalizeRole(String role) {
        if (role == null) {
            return "user";
        }
        return switch (role.trim().toLowerCase()) {
            case "assistant", "model" -> "assistant";
            case "system" -> "system";
            case "tool" -> "tool";
            default -> "user";
        };
    }

    private ArrayNode encodeImages(ChatExecutionRequest.MessageInput message, GatewayChatRuntimeContext context) {
        ArrayNode images = objectMapper.createArrayNode();
        if (message.media() == null || message.media().isEmpty()) {
            return images;
        }
        for (ChatExecutionRequest.MediaInput media : message.media()) {
            if (!"image".equalsIgnoreCase(media.kind())) {
                continue;
            }
            images.add(encodeImage(media, context));
        }
        return images;
    }

    private String encodeImage(ChatExecutionRequest.MediaInput media, GatewayChatRuntimeContext context) {
        if (media.url().startsWith("gateway://")) {
            String fileKey = media.url().substring("gateway://".length());
            GatewayFileContent content = gatewayFileService.getFileContent(fileKey, context.selectionResult().distributedKeyId());
            String mimeType = content.mimeType() == null || content.mimeType().isBlank() ? media.mimeType() : content.mimeType();
            if (mimeType == null || !mimeType.toLowerCase().startsWith("image/")) {
                throw new IllegalArgumentException("当前 Ollama 运行时仅支持图片类型的 gateway:// 文件输入。");
            }
            return Base64.getEncoder().encodeToString(content.bytes());
        }
        if (media.url().startsWith("data:")) {
            int commaIndex = media.url().indexOf(',');
            if (commaIndex < 0) {
                throw new IllegalArgumentException("无法解析 data URL 图片输入。");
            }
            String metadata = media.url().substring(5, commaIndex);
            String payload = media.url().substring(commaIndex + 1);
            if (metadata.contains(";base64")) {
                return payload;
            }
            return Base64.getEncoder().encodeToString(URLDecoder.decode(payload, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));
        }
        throw new IllegalArgumentException("当前 Ollama 运行时不支持远程 URL 图片输入。");
    }

    private Object resolveThinkingControl(JsonNode executionMetadata) {
        if (executionMetadata == null || executionMetadata.isMissingNode() || executionMetadata.isNull()) {
            return null;
        }
        JsonNode reasoningEffort = executionMetadata.path("reasoning_effort");
        Object mappedEffort = mapThinkingValue(reasoningEffort);
        if (mappedEffort != null) {
            return mappedEffort;
        }
        JsonNode reasoning = executionMetadata.path("reasoning");
        if (reasoning.isBoolean()) {
            return reasoning.asBoolean();
        }
        if (reasoning.isTextual()) {
            return mapThinkingValue(reasoning);
        }
        if (reasoning.isObject()) {
            Object nested = mapThinkingValue(reasoning.path("effort"));
            if (nested != null) {
                return nested;
            }
            JsonNode enabled = reasoning.path("enabled");
            if (enabled.isBoolean()) {
                return enabled.asBoolean();
            }
            return Boolean.TRUE;
        }
        return null;
    }

    private Object mapThinkingValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (!node.isTextual()) {
            return Boolean.TRUE;
        }
        return switch (node.asText().trim().toLowerCase()) {
            case "", "auto" -> Boolean.TRUE;
            case "none", "off", "disabled" -> Boolean.FALSE;
            case "low", "medium", "high" -> node.asText().trim().toLowerCase();
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

    private List<GatewayToolCall> extractToolCalls(JsonNode payload) {
        JsonNode toolCallsNode = payload.path("message").path("tool_calls");
        if (!toolCallsNode.isArray() || toolCallsNode.isEmpty()) {
            toolCallsNode = payload.path("tool_calls");
        }
        if (!toolCallsNode.isArray() || toolCallsNode.isEmpty()) {
            return List.of();
        }
        List<GatewayToolCall> toolCalls = new ArrayList<>();
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
            toolCalls.add(new GatewayToolCall(
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

    private Flux<ChatExecutionStreamChunk> decodeChunk(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return Flux.empty();
        }
        String[] lines = rawBody.split("\\r?\\n");
        List<ChatExecutionStreamChunk> chunks = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            try {
                JsonNode payload = objectMapper.readTree(line);
                String content = extractText(payload);
                String reasoning = extractReasoning(payload);
                List<GatewayToolCall> toolCalls = extractToolCalls(payload);
                if ((content != null && !content.isBlank())
                        || (reasoning != null && !reasoning.isBlank())
                        || !toolCalls.isEmpty()) {
                    chunks.add(new ChatExecutionStreamChunk(
                            content == null || content.isBlank() ? null : content,
                            null,
                            GatewayUsage.empty(),
                            false,
                            toolCalls,
                            reasoning == null || reasoning.isBlank() ? null : reasoning
                    ));
                }
                if (payload.path("done").asBoolean(false)) {
                    int promptTokens = payload.path("prompt_eval_count").asInt(0);
                    int completionTokens = payload.path("eval_count").asInt(0);
                    chunks.add(new ChatExecutionStreamChunk(
                            null,
                            firstNonBlank(payload.path("done_reason").asText(null), payload.path("stop_reason").asText(null), "stop"),
                            new GatewayUsage(promptTokens, promptTokens, completionTokens, 0, 0, 0, 0, 0, null, promptTokens + completionTokens, payload),
                            true,
                            List.of(),
                            null
                    ));
                    continue;
                }
            } catch (Exception exception) {
                throw new IllegalStateException("无法解析 Ollama stream 响应。", exception);
            }
        }
        return Flux.fromIterable(chunks);
    }
}
