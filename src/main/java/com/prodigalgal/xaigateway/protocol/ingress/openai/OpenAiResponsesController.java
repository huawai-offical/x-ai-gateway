package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionService;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamilyResolver;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequestMetadata;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Validated
@RestController
@RequestMapping("/v1/responses")
public class OpenAiResponsesController {

    private static final String OPENAI_ORGANIZATION_HEADER = "OpenAI-Organization";
    private static final String OPENAI_PROJECT_HEADER = "OpenAI-Project";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final DistributedKeyAuthenticationService distributedKeyAuthenticationService;
    private final GatewayChatExecutionService gatewayChatExecutionService;
    private final GatewayClientFamilyResolver gatewayClientFamilyResolver;
    private final GatewayAsyncResourceService gatewayAsyncResourceService;
    private final OpenAiIdempotencyReplayService openAiIdempotencyReplayService;
    private final OpenAiResponsesRequestMapper openAiResponsesRequestMapper;
    private final ObjectMapper objectMapper;
    private final OpenAiResponsesEncoder openAiResponsesEncoder;

    public OpenAiResponsesController(
            DistributedKeyAuthenticationService distributedKeyAuthenticationService,
            GatewayChatExecutionService gatewayChatExecutionService,
            GatewayClientFamilyResolver gatewayClientFamilyResolver,
            GatewayAsyncResourceService gatewayAsyncResourceService,
            OpenAiIdempotencyReplayService openAiIdempotencyReplayService,
            OpenAiResponsesRequestMapper openAiResponsesRequestMapper,
            ObjectMapper objectMapper) {
        this.distributedKeyAuthenticationService = distributedKeyAuthenticationService;
        this.gatewayChatExecutionService = gatewayChatExecutionService;
        this.gatewayClientFamilyResolver = gatewayClientFamilyResolver;
        this.gatewayAsyncResourceService = gatewayAsyncResourceService;
        this.openAiIdempotencyReplayService = openAiIdempotencyReplayService;
        this.openAiResponsesRequestMapper = openAiResponsesRequestMapper;
        this.objectMapper = objectMapper;
        this.openAiResponsesEncoder = new OpenAiResponsesEncoder(objectMapper);
    }

    @PostMapping
    public ResponseEntity<?> createResponse(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = "X-AI-Gateway-Client-Family", required = false) String explicitClientFamily,
            @RequestHeader(value = "X-AI-Gateway-Client-Instance", required = false) String clientInstance,
            @RequestHeader(value = "X_AI_GATEWAY_CLIENT_INSTANCE", required = false) String legacyClientInstance,
            @RequestHeader(value = "X-AI-Gateway-Workspace-Hint", required = false) String workspaceHint,
            @RequestHeader(value = "X_AI_GATEWAY_WORKSPACE_HINT", required = false) String legacyWorkspaceHint,
            @RequestHeader(value = "openai-beta", required = false) String openAiBeta,
            @RequestHeader(value = OPENAI_ORGANIZATION_HEADER, required = false) String openAiOrganization,
            @RequestHeader(value = OPENAI_PROJECT_HEADER, required = false) String openAiProject,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestHeader(value = "originator", required = false) String originator,
            @RequestHeader(value = "session_id", required = false) String sessionId,
            @RequestHeader(value = "conversation_id", required = false) String conversationId,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        GatewayClientFamily clientFamily = gatewayClientFamilyResolver.resolve(explicitClientFamily, userAgent);
        if (!requestBody.path("stream").asBoolean(false)) {
            var replayed = openAiIdempotencyReplayService.replay(
                    distributedKey.id(),
                    "/v1/responses",
                    idempotencyKey,
                    requestBody
            );
            if (replayed.isPresent()) {
                return replayed(replayed.get());
            }
        }
        CanonicalRequestMetadata metadata = buildMetadata(
                clientFamily,
                prefer(clientInstance, legacyClientInstance),
                prefer(workspaceHint, legacyWorkspaceHint),
                openAiBeta,
                openAiOrganization,
                openAiProject,
                idempotencyKey,
                originator,
                sessionId,
                conversationId,
                userAgent
        );
        CanonicalRequest canonicalRequest = openAiResponsesRequestMapper.toCanonicalRequest(
                distributedKey.keyPrefix(),
                requestBody,
                metadata
        );

        if (requestBody.path("stream").asBoolean(false)) {
            var streamResponse = clientFamily == GatewayClientFamily.GENERIC_OPENAI
                    ? gatewayChatExecutionService.executeGatewayStream(canonicalRequest)
                    : gatewayChatExecutionService.executeGatewayStream(canonicalRequest, clientFamily);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(openAiResponsesEncoder.encodeStream(streamResponse));
        }

        var response = clientFamily == GatewayClientFamily.GENERIC_OPENAI
                ? gatewayChatExecutionService.executeGatewayResponse(canonicalRequest)
                : gatewayChatExecutionService.executeGatewayResponse(canonicalRequest, clientFamily);
        OpenAiResponsesResponse payload = openAiResponsesEncoder.encode(response);
        if (requestBody.path("store").asBoolean(false)) {
            JsonNode storedPayload = gatewayAsyncResourceService.storeResponse(
                    distributedKey.id(),
                    canonicalRequest.requestedModel(),
                    requestBody,
                    objectMapper.valueToTree(payload)
            );
            return ResponseEntity.ok(openAiIdempotencyReplayService.remember(
                    distributedKey.id(),
                    "/v1/responses",
                    idempotencyKey,
                    requestBody,
                    storedPayload
            ));
        }
        return ResponseEntity.ok(openAiIdempotencyReplayService.remember(
                distributedKey.id(),
                "/v1/responses",
                idempotencyKey,
                requestBody,
                objectMapper.valueToTree(payload)
        ));
    }

    private ResponseEntity<JsonNode> replayed(JsonNode payload) {
        return ResponseEntity.ok()
                .header(OpenAiIdempotencyReplayService.REPLAYED_HEADER, "true")
                .body(payload);
    }

    private CanonicalRequestMetadata buildMetadata(
            GatewayClientFamily clientFamily,
            String clientInstance,
            String workspaceHint,
            String openAiBeta,
            String openAiOrganization,
            String openAiProject,
            String idempotencyKey,
            String originator,
            String sessionId,
            String conversationId,
            String userAgent) {
        String affinitySource = null;
        String affinityValue = null;
        if (hasText(sessionId)) {
            affinitySource = "session_id";
            affinityValue = sessionId;
        } else if (hasText(conversationId)) {
            affinitySource = "conversation_id";
            affinityValue = conversationId;
        } else if (hasText(clientInstance)) {
            affinitySource = "client_instance";
            affinityValue = clientInstance;
        }
        String affinityKey = affinityValue == null ? null : fingerprint(affinitySource + ":" + affinityValue);
        return new CanonicalRequestMetadata(
                clientFamily == null ? null : clientFamily.name(),
                safeHeader(clientInstance, 128),
                safeHeader(workspaceHint, 256),
                affinitySource,
                affinityKey,
                safeHeader(openAiBeta, 128),
                safeHeader(originator, 256),
                safeHeader(userAgent, 256),
                safeHeader(openAiOrganization, 128),
                safeHeader(openAiProject, 128),
                safeHeader(idempotencyKey, 256)
        );
    }

    private String prefer(String primary, String fallback) {
        return hasText(primary) ? primary : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeHeader(String value, int maxLength) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 32);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成 Codex 会话粘性指纹。", exception);
        }
    }

    @GetMapping("/{responseId}")
    public JsonNode getStoredResponse(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String responseId) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.getResponse(responseId, distributedKey.id());
    }

    @DeleteMapping("/{responseId}")
    public JsonNode deleteStoredResponse(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String responseId) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.deleteResponse(responseId, distributedKey.id());
    }

    @PostMapping("/{responseId}/cancel")
    public JsonNode cancelStoredResponse(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String responseId) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.cancelResponse(responseId, distributedKey.id());
    }

    @GetMapping("/{responseId}/input_items")
    public JsonNode listResponseInputItems(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String responseId,
            @RequestParam(required = false) String after,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String order) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.listResponseInputItems(responseId, distributedKey.id(), after, limit, order);
    }
}
