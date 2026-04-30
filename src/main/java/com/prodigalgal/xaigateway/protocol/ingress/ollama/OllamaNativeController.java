package com.prodigalgal.xaigateway.protocol.ingress.ollama;

import com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionService;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.catalog.GatewayPublicModelView;
import com.prodigalgal.xaigateway.gateway.core.catalog.ModelCatalogQueryService;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionStreamResult;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEventType;
import com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionEncoder;
import com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionRequest;
import com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionRequestMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping("/ollama/api")
public class OllamaNativeController {

    private final DistributedKeyAuthenticationService authenticationService;
    private final DistributedKeyQueryService distributedKeyQueryService;
    private final ModelCatalogQueryService modelCatalogQueryService;
    private final GatewayChatExecutionService gatewayChatExecutionService;
    private final OpenAiChatCompletionRequestMapper requestMapper;
    private final OpenAiChatCompletionEncoder openAiEncoder;
    private final ObjectMapper objectMapper;

    public OllamaNativeController(
            DistributedKeyAuthenticationService authenticationService,
            DistributedKeyQueryService distributedKeyQueryService,
            ModelCatalogQueryService modelCatalogQueryService,
            GatewayChatExecutionService gatewayChatExecutionService,
            OpenAiChatCompletionRequestMapper requestMapper,
            OpenAiChatCompletionEncoder openAiEncoder,
            ObjectMapper objectMapper) {
        this.authenticationService = authenticationService;
        this.distributedKeyQueryService = distributedKeyQueryService;
        this.modelCatalogQueryService = modelCatalogQueryService;
        this.gatewayChatExecutionService = gatewayChatExecutionService;
        this.requestMapper = requestMapper;
        this.openAiEncoder = openAiEncoder;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/tags")
    public Map<String, Object> tags(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "x-api-key", required = false) String apiKey) {
        AuthenticatedDistributedKey key = authenticate(authorization, apiKey);
        var keyView = distributedKeyQueryService.findActiveByKeyPrefix(key.keyPrefix())
                .orElseThrow(() -> new IllegalArgumentException("未找到可用的 DistributedKey。"));
        List<Map<String, Object>> models = modelCatalogQueryService.listAccessiblePublicModels(keyView, "ollama").stream()
                .map(this::toOllamaModel)
                .toList();
        return Map.of("models", models);
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "x-api-key", required = false) String apiKey,
            @RequestBody JsonNode body) {
        AuthenticatedDistributedKey key = authenticate(authorization, apiKey);
        OpenAiChatCompletionRequest request = toOpenAiRequest(body);
        var canonical = requestMapper.toCanonicalRequest(key, request);
        if (Boolean.TRUE.equals(request.stream())) {
            CanonicalExecutionStreamResult stream = gatewayChatExecutionService.executeGatewayStream(canonical);
            Flux<String> chunks = stream.events().map(event -> {
                String text = event.textDelta();
                boolean done = event.type() == CanonicalStreamEventType.COMPLETED;
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("model", request.model());
                payload.put("created_at", Instant.now().toString());
                payload.put("message", Map.of("role", "assistant", "content", text == null ? "" : text));
                payload.put("done", done);
                if (done) {
                    payload.put("done_reason", event.finishReason() == null ? "stop" : event.finishReason().name().toLowerCase());
                    putUsage(payload, event.usage());
                }
                return writeJson(payload) + "\n";
            });
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_NDJSON).body(chunks);
        }
        var response = gatewayChatExecutionService.executeGatewayResponse(canonical);
        var encoded = openAiEncoder.encode(response);
        String content = encoded.choices().isEmpty() ? "" : encoded.choices().get(0).message().content();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", request.model());
        payload.put("created_at", Instant.now().toString());
        payload.put("message", Map.of("role", "assistant", "content", content == null ? "" : content));
        payload.put("done", true);
        payload.put("done_reason", response.response().finishReason() == null ? "stop" : response.response().finishReason().name().toLowerCase());
        putUsage(payload, response.response().usage());
        return ResponseEntity.ok(payload);
    }

    private AuthenticatedDistributedKey authenticate(String authorization, String apiKey) {
        if (StringUtils.hasText(authorization)) {
            return authenticationService.authenticateBearerToken(authorization);
        }
        return authenticationService.authenticateRawToken(apiKey);
    }

    private OpenAiChatCompletionRequest toOpenAiRequest(JsonNode body) {
        String model = body.path("model").asText(null);
        if (!StringUtils.hasText(model)) {
            throw new IllegalArgumentException("Ollama chat 请求缺少 model。");
        }
        boolean stream = body.path("stream").asBoolean(false);
        List<OpenAiChatCompletionRequest.Message> messages = new ArrayList<>();
        JsonNode sourceMessages = body.path("messages");
        if (sourceMessages.isArray()) {
            for (JsonNode item : sourceMessages) {
                messages.add(new OpenAiChatCompletionRequest.Message(
                        item.path("role").asText("user"),
                        toOpenAiContent(item),
                        null
                ));
            }
        } else if (body.has("prompt")) {
            messages.add(new OpenAiChatCompletionRequest.Message("user", JsonNodeFactory.instance.textNode(body.path("prompt").asText("")), null));
        }
        return new OpenAiChatCompletionRequest(
                model,
                messages,
                List.of(),
                null,
                body.get("think"),
                null,
                body.path("options").path("temperature").isNumber() ? body.path("options").path("temperature").asDouble() : null,
                body.path("options").path("num_predict").isNumber() ? body.path("options").path("num_predict").asInt() : null,
                stream
        );
    }

    private Map<String, Object> toOllamaModel(GatewayPublicModelView model) {
        String family = model.providerFamily() == null ? "unknown" : model.providerFamily().name().toLowerCase();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("family", family);
        details.put("format", "x-ai-gateway");
        details.put("parameter_size", "unknown");
        details.put("quantization_level", "unknown");
        details.put("capability_level", model.capabilityLevel() == null ? "unknown" : model.capabilityLevel().name().toLowerCase());
        details.put("preferred_backend", model.preferredBackend() == null ? "unknown" : model.preferredBackend().name().toLowerCase());
        details.put("supports", Map.of(
                "chat", model.supportsChat(),
                "embeddings", model.supportsEmbeddings(),
                "tools", hasSupportedCapability(model, "tool"),
                "vision", hasSupportedCapability(model, "image"),
                "reasoning", hasSupportedCapability(model, "reasoning")
        ));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", model.publicModelId());
        result.put("model", model.publicModelId());
        result.put("modified_at", Instant.EPOCH.toString());
        result.put("details", details);
        return result;
    }

    private JsonNode toOpenAiContent(JsonNode item) {
        String text = item.path("content").asText("");
        JsonNode images = item.path("images");
        if (!images.isArray() || images.isEmpty()) {
            return JsonNodeFactory.instance.textNode(text);
        }
        ArrayNode content = objectMapper.createArrayNode();
        if (StringUtils.hasText(text)) {
            ObjectNode textPart = content.addObject();
            textPart.put("type", "text");
            textPart.put("text", text);
        }
        for (JsonNode image : images) {
            String encoded = image.asText(null);
            if (!StringUtils.hasText(encoded)) {
                continue;
            }
            ObjectNode imagePart = content.addObject();
            imagePart.put("type", "image_url");
            imagePart.putObject("image_url").put("url", normalizeOllamaImageUrl(encoded));
        }
        return content.isEmpty() ? JsonNodeFactory.instance.textNode(text) : content;
    }

    private String normalizeOllamaImageUrl(String encoded) {
        if (encoded.startsWith("data:") || encoded.startsWith("gateway://")) {
            return encoded;
        }
        return "data:image/*;base64," + encoded;
    }

    private void putUsage(Map<String, Object> payload, com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalUsage usage) {
        if (usage == null || !usage.present()) {
            return;
        }
        payload.put("prompt_eval_count", usage.promptTokens());
        payload.put("eval_count", usage.completionTokens());
        payload.put("total_tokens", usage.totalTokens());
        payload.put("x_gateway_usage", Map.of(
                "prompt_tokens", usage.promptTokens(),
                "completion_tokens", usage.completionTokens(),
                "total_tokens", usage.totalTokens(),
                "cache_hit_tokens", usage.cacheHitTokens(),
                "cache_write_tokens", usage.cacheWriteTokens(),
                "reasoning_tokens", usage.reasoningTokens()
        ));
    }

    private boolean hasSupportedCapability(GatewayPublicModelView model, String capabilityKey) {
        if (model.capabilities() == null || model.capabilities().isEmpty()) {
            return false;
        }
        return model.capabilities().entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().toLowerCase().contains(capabilityKey))
                .map(Map.Entry::getValue)
                .anyMatch(value -> value != null && !"blocked".equalsIgnoreCase(value.supportStatus()));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Ollama native 响应序列化失败。", exception);
        }
    }
}
