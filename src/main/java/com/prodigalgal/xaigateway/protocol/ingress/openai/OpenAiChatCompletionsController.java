package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionService;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamilyResolver;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequestMetadata;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceService;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Validated
@RestController
@RequestMapping("/v1/chat/completions")
public class OpenAiChatCompletionsController {

    private static final String OPENAI_ORGANIZATION_HEADER = "OpenAI-Organization";
    private static final String OPENAI_PROJECT_HEADER = "OpenAI-Project";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final int MAX_LIST_LIMIT = 100;

    private final DistributedKeyAuthenticationService distributedKeyAuthenticationService;
    private final GatewayChatExecutionService gatewayChatExecutionService;
    private final GatewayClientFamilyResolver gatewayClientFamilyResolver;
    private final GatewayAsyncResourceService gatewayAsyncResourceService;
    private final OpenAiIdempotencyReplayService openAiIdempotencyReplayService;
    private final OpenAiChatCompletionRequestMapper openAiChatCompletionRequestMapper;
    private final OpenAiChatCompletionEncoder openAiChatCompletionEncoder;
    private final ObjectMapper objectMapper;

    public OpenAiChatCompletionsController(
            DistributedKeyAuthenticationService distributedKeyAuthenticationService,
            GatewayChatExecutionService gatewayChatExecutionService,
            GatewayClientFamilyResolver gatewayClientFamilyResolver,
            GatewayAsyncResourceService gatewayAsyncResourceService,
            OpenAiIdempotencyReplayService openAiIdempotencyReplayService,
            OpenAiChatCompletionRequestMapper openAiChatCompletionRequestMapper,
            ObjectMapper objectMapper) {
        this.distributedKeyAuthenticationService = distributedKeyAuthenticationService;
        this.gatewayChatExecutionService = gatewayChatExecutionService;
        this.gatewayClientFamilyResolver = gatewayClientFamilyResolver;
        this.gatewayAsyncResourceService = gatewayAsyncResourceService;
        this.openAiIdempotencyReplayService = openAiIdempotencyReplayService;
        this.openAiChatCompletionRequestMapper = openAiChatCompletionRequestMapper;
        this.openAiChatCompletionEncoder = new OpenAiChatCompletionEncoder(objectMapper);
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<?> createCompletion(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = "X-AI-Gateway-Client-Family", required = false) String explicitClientFamily,
            @RequestHeader(value = OPENAI_ORGANIZATION_HEADER, required = false) String openAiOrganization,
            @RequestHeader(value = OPENAI_PROJECT_HEADER, required = false) String openAiProject,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
            @Valid @RequestBody OpenAiChatCompletionRequest request) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        GatewayClientFamily clientFamily = gatewayClientFamilyResolver.resolve(explicitClientFamily, userAgent);
        JsonNode requestPayload = objectMapper.valueToTree(request);
        if (!Boolean.TRUE.equals(request.stream())) {
            var replayed = openAiIdempotencyReplayService.replay(
                    distributedKey.id(),
                    "/v1/chat/completions",
                    idempotencyKey,
                    requestPayload
            );
            if (replayed.isPresent()) {
                return replayed(replayed.get());
            }
        }
        CanonicalRequestMetadata metadata = new CanonicalRequestMetadata(
                clientFamily == null ? null : clientFamily.name(),
                null,
                null,
                null,
                null,
                null,
                null,
                safeHeader(userAgent, 256),
                safeHeader(openAiOrganization, 128),
                safeHeader(openAiProject, 128),
                safeHeader(idempotencyKey, 256)
        );
        CanonicalRequest canonicalRequest = openAiChatCompletionRequestMapper.toCanonicalRequest(distributedKey, request, metadata);

        if (Boolean.TRUE.equals(request.stream())) {
            var streamResponse = clientFamily == GatewayClientFamily.GENERIC_OPENAI
                    ? gatewayChatExecutionService.executeGatewayStream(canonicalRequest)
                    : gatewayChatExecutionService.executeGatewayStream(canonicalRequest, clientFamily);
            Flux<String> body = openAiChatCompletionEncoder.encodeStream(streamResponse, request.streamOptions());
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(body);
        }

        var response = clientFamily == GatewayClientFamily.GENERIC_OPENAI
                ? gatewayChatExecutionService.executeGatewayResponse(canonicalRequest)
                : gatewayChatExecutionService.executeGatewayResponse(canonicalRequest, clientFamily);
        OpenAiChatCompletionResponse payload = openAiChatCompletionEncoder.encode(response);
        if (Boolean.TRUE.equals(request.store())) {
            JsonNode storedPayload = gatewayAsyncResourceService.storeChatCompletion(
                    distributedKey.id(),
                    canonicalRequest.requestedModel(),
                    requestPayload,
                    objectMapper.valueToTree(payload)
            );
            return ResponseEntity.ok(openAiIdempotencyReplayService.remember(
                    distributedKey.id(),
                    "/v1/chat/completions",
                    idempotencyKey,
                    requestPayload,
                    storedPayload
            ));
        }
        return ResponseEntity.ok(openAiIdempotencyReplayService.remember(
                distributedKey.id(),
                "/v1/chat/completions",
                idempotencyKey,
                requestPayload,
                objectMapper.valueToTree(payload)
        ));
    }

    @GetMapping
    public JsonNode listStoredCompletions(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam Map<String, String> query) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.listChatCompletions(
                distributedKey.id(),
                query.get("after"),
                parseLimit(query.get("limit")),
                query.get("model"),
                parseOrder(query.get("order")),
                metadataFilter(query)
        );
    }

    @GetMapping("/{completionId}")
    public JsonNode getStoredCompletion(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String completionId) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.getChatCompletion(completionId, distributedKey.id());
    }

    @PostMapping("/{completionId}")
    public JsonNode updateStoredCompletion(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String completionId,
            @RequestBody(required = false) JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        JsonNode metadata = requestBody == null ? null : requestBody.path("metadata");
        return gatewayAsyncResourceService.updateChatCompletionMetadata(completionId, distributedKey.id(), metadata);
    }

    @DeleteMapping("/{completionId}")
    public JsonNode deleteStoredCompletion(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String completionId) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.deleteChatCompletion(completionId, distributedKey.id());
    }

    @GetMapping("/{completionId}/messages")
    public JsonNode listStoredCompletionMessages(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String completionId,
            @RequestParam(required = false) String after,
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String order) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.listChatCompletionMessages(
                completionId,
                distributedKey.id(),
                after,
                parseLimit(limit),
                parseOrder(order));
    }

    private String safeHeader(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private ResponseEntity<JsonNode> replayed(JsonNode payload) {
        return ResponseEntity.ok()
                .header(OpenAiIdempotencyReplayService.REPLAYED_HEADER, "true")
                .body(payload);
    }

    private Integer parseLimit(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 1 || parsed > MAX_LIST_LIMIT) {
                throw new IllegalArgumentException("limit 必须在 1 到 100 之间。");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("limit 必须是整数。", exception);
        }
    }

    private String parseOrder(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        if ("asc".equals(normalized) || "desc".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("order 必须是 asc 或 desc。");
    }

    private Map<String, String> metadataFilter(Map<String, String> query) {
        Map<String, String> result = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) {
            return result;
        }
        query.forEach((key, value) -> {
            if (key != null && key.startsWith("metadata[") && key.endsWith("]") && key.length() > 10) {
                result.put(key.substring(9, key.length() - 1), value);
            }
        });
        return result;
    }
}
