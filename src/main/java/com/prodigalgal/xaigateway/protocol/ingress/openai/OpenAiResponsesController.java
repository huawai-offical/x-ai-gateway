package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionService;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamilyResolver;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceService;
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
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Validated
@RestController
@RequestMapping("/v1/responses")
public class OpenAiResponsesController {

    private final DistributedKeyAuthenticationService distributedKeyAuthenticationService;
    private final GatewayChatExecutionService gatewayChatExecutionService;
    private final GatewayClientFamilyResolver gatewayClientFamilyResolver;
    private final GatewayAsyncResourceService gatewayAsyncResourceService;
    private final OpenAiResponsesRequestMapper openAiResponsesRequestMapper;
    private final ObjectMapper objectMapper;
    private final OpenAiResponsesEncoder openAiResponsesEncoder;

    public OpenAiResponsesController(
            DistributedKeyAuthenticationService distributedKeyAuthenticationService,
            GatewayChatExecutionService gatewayChatExecutionService,
            GatewayClientFamilyResolver gatewayClientFamilyResolver,
            GatewayAsyncResourceService gatewayAsyncResourceService,
            OpenAiResponsesRequestMapper openAiResponsesRequestMapper,
            ObjectMapper objectMapper) {
        this.distributedKeyAuthenticationService = distributedKeyAuthenticationService;
        this.gatewayChatExecutionService = gatewayChatExecutionService;
        this.gatewayClientFamilyResolver = gatewayClientFamilyResolver;
        this.gatewayAsyncResourceService = gatewayAsyncResourceService;
        this.openAiResponsesRequestMapper = openAiResponsesRequestMapper;
        this.objectMapper = objectMapper;
        this.openAiResponsesEncoder = new OpenAiResponsesEncoder(objectMapper);
    }

    @PostMapping
    public ResponseEntity<?> createResponse(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = "X-AI-Gateway-Client-Family", required = false) String explicitClientFamily,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        CanonicalRequest canonicalRequest = openAiResponsesRequestMapper.toCanonicalRequest(distributedKey.keyPrefix(), requestBody);
        GatewayClientFamily clientFamily = gatewayClientFamilyResolver.resolve(explicitClientFamily, userAgent);

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
            return ResponseEntity.ok(gatewayAsyncResourceService.storeResponse(
                    distributedKey.id(),
                    canonicalRequest.requestedModel(),
                    requestBody,
                    objectMapper.valueToTree(payload)
            ));
        }
        return ResponseEntity.ok(payload);
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
}
