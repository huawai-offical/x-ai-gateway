package com.prodigalgal.xaigateway.protocol.ingress.anthropic;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionService;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamilyResolver;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Validated
@RestController
@RequestMapping("/v1/messages")
public class AnthropicMessagesController {

    private static final String API_KEY_HEADER = "x-api-key";
    private static final String CLIENT_FAMILY_HEADER = "X-AI-Gateway-Client-Family";

    private final DistributedKeyAuthenticationService distributedKeyAuthenticationService;
    private final GatewayChatExecutionService gatewayChatExecutionService;
    private final GatewayClientFamilyResolver gatewayClientFamilyResolver;
    private final AnthropicMessagesRequestMapper anthropicMessagesRequestMapper;
    private final AnthropicMessagesEncoder anthropicMessagesEncoder;

    public AnthropicMessagesController(
            DistributedKeyAuthenticationService distributedKeyAuthenticationService,
            GatewayChatExecutionService gatewayChatExecutionService,
            GatewayClientFamilyResolver gatewayClientFamilyResolver,
            AnthropicMessagesRequestMapper anthropicMessagesRequestMapper,
            ObjectMapper objectMapper) {
        this.distributedKeyAuthenticationService = distributedKeyAuthenticationService;
        this.gatewayChatExecutionService = gatewayChatExecutionService;
        this.gatewayClientFamilyResolver = gatewayClientFamilyResolver;
        this.anthropicMessagesRequestMapper = anthropicMessagesRequestMapper;
        this.anthropicMessagesEncoder = new AnthropicMessagesEncoder(objectMapper);
    }

    @PostMapping
    public ResponseEntity<?> createMessage(
            @RequestHeader(API_KEY_HEADER) String apiKey,
            @RequestHeader(value = CLIENT_FAMILY_HEADER, required = false) String explicitClientFamily,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @Valid @RequestBody AnthropicMessagesRequest request) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateRawToken(apiKey);
        CanonicalRequest canonicalRequest = anthropicMessagesRequestMapper.toCanonicalRequest(distributedKey, request);
        GatewayClientFamily clientFamily = gatewayClientFamilyResolver.resolve(explicitClientFamily, userAgent);

        if (Boolean.TRUE.equals(request.stream())) {
            var streamResponse = clientFamily == GatewayClientFamily.GENERIC_OPENAI
                    ? gatewayChatExecutionService.executeGatewayStream(canonicalRequest)
                    : gatewayChatExecutionService.executeGatewayStream(canonicalRequest, clientFamily);
            Flux<String> body = anthropicMessagesEncoder.encodeStream(streamResponse);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(body);
        }

        var response = clientFamily == GatewayClientFamily.GENERIC_OPENAI
                ? gatewayChatExecutionService.executeGatewayResponse(canonicalRequest)
                : gatewayChatExecutionService.executeGatewayResponse(canonicalRequest, clientFamily);
        return ResponseEntity.ok(anthropicMessagesEncoder.encode(response));
    }
}
