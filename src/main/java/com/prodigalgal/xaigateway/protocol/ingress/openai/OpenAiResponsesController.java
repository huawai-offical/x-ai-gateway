package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionService;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
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
    private final GatewayAsyncResourceService gatewayAsyncResourceService;
    private final OpenAiResponsesRequestMapper openAiResponsesRequestMapper;
    private final ObjectMapper objectMapper;
    private final OpenAiResponsesEncoder openAiResponsesEncoder;

    public OpenAiResponsesController(
            DistributedKeyAuthenticationService distributedKeyAuthenticationService,
            GatewayChatExecutionService gatewayChatExecutionService,
            GatewayAsyncResourceService gatewayAsyncResourceService,
            OpenAiResponsesRequestMapper openAiResponsesRequestMapper,
            ObjectMapper objectMapper) {
        this.distributedKeyAuthenticationService = distributedKeyAuthenticationService;
        this.gatewayChatExecutionService = gatewayChatExecutionService;
        this.gatewayAsyncResourceService = gatewayAsyncResourceService;
        this.openAiResponsesRequestMapper = openAiResponsesRequestMapper;
        this.objectMapper = objectMapper;
        this.openAiResponsesEncoder = new OpenAiResponsesEncoder(objectMapper);
    }

    @PostMapping
    public ResponseEntity<?> createResponse(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        ChatExecutionRequest executionRequest = openAiResponsesRequestMapper.toExecutionRequest(distributedKey.keyPrefix(), requestBody);

        if (requestBody.path("stream").asBoolean(false)) {
            var streamResponse = gatewayChatExecutionService.executeGatewayStream(executionRequest);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(openAiResponsesEncoder.encodeStream(streamResponse));
        }

        var response = gatewayChatExecutionService.executeGatewayResponse(executionRequest);
        OpenAiResponsesResponse payload = openAiResponsesEncoder.encode(response);
        if (requestBody.path("store").asBoolean(false)) {
            return ResponseEntity.ok(gatewayAsyncResourceService.storeResponse(
                    distributedKey.id(),
                    executionRequest.requestedModel(),
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
