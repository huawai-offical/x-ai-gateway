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
import tools.jackson.databind.node.JsonNodeFactory;

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
                return writeJson(Map.of(
                        "model", request.model(),
                        "message", Map.of("role", "assistant", "content", text == null ? "" : text),
                        "done", done
                )) + "\n";
            });
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_NDJSON).body(chunks);
        }
        var response = gatewayChatExecutionService.executeGatewayResponse(canonical);
        var encoded = openAiEncoder.encode(response);
        String content = encoded.choices().isEmpty() ? "" : encoded.choices().get(0).message().content();
        return ResponseEntity.ok(Map.of(
                "model", request.model(),
                "created_at", Instant.now().toString(),
                "message", Map.of("role", "assistant", "content", content == null ? "" : content),
                "done", true
        ));
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
        List<OpenAiChatCompletionRequest.Message> messages = new java.util.ArrayList<>();
        JsonNode sourceMessages = body.path("messages");
        if (sourceMessages.isArray()) {
            for (JsonNode item : sourceMessages) {
                messages.add(new OpenAiChatCompletionRequest.Message(
                        item.path("role").asText("user"),
                        JsonNodeFactory.instance.textNode(item.path("content").asText("")),
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
        return Map.of(
                "name", model.publicModelId(),
                "model", model.publicModelId(),
                "modified_at", Instant.EPOCH.toString(),
                "details", Map.of(
                        "family", family,
                        "format", "x-ai-gateway"
                )
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Ollama native 响应序列化失败。", exception);
        }
    }
}
