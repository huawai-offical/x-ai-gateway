package com.prodigalgal.xaigateway.protocol.ingress.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionService;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
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

    private final DistributedKeyAuthenticationService distributedKeyAuthenticationService;
    private final GatewayChatExecutionService gatewayChatExecutionService;
    private final AnthropicMessagesRequestMapper anthropicMessagesRequestMapper;
    private final AnthropicMessagesEncoder anthropicMessagesEncoder;

    public AnthropicMessagesController(
            DistributedKeyAuthenticationService distributedKeyAuthenticationService,
            GatewayChatExecutionService gatewayChatExecutionService,
            AnthropicMessagesRequestMapper anthropicMessagesRequestMapper,
            ObjectMapper objectMapper) {
        this.distributedKeyAuthenticationService = distributedKeyAuthenticationService;
        this.gatewayChatExecutionService = gatewayChatExecutionService;
        this.anthropicMessagesRequestMapper = anthropicMessagesRequestMapper;
        this.anthropicMessagesEncoder = new AnthropicMessagesEncoder(objectMapper);
    }

    @PostMapping
    public ResponseEntity<?> createMessage(
            @RequestHeader(API_KEY_HEADER) String apiKey,
            @Valid @RequestBody AnthropicMessagesRequest request) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateRawToken(apiKey);
        ChatExecutionRequest executionRequest = anthropicMessagesRequestMapper.toExecutionRequest(distributedKey, request);

        if (Boolean.TRUE.equals(request.stream())) {
            var streamResponse = gatewayChatExecutionService.executeGatewayStream(executionRequest);
            Flux<String> body = anthropicMessagesEncoder.encodeStream(streamResponse);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(body);
        }

        var response = gatewayChatExecutionService.executeGatewayResponse(executionRequest);
        return ResponseEntity.ok(anthropicMessagesEncoder.encode(response));
    }
}
