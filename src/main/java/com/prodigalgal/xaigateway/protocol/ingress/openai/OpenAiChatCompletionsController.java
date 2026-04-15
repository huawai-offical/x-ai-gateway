package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionService;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
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
@RequestMapping("/v1/chat/completions")
public class OpenAiChatCompletionsController {

    private final DistributedKeyAuthenticationService distributedKeyAuthenticationService;
    private final GatewayChatExecutionService gatewayChatExecutionService;
    private final OpenAiChatCompletionRequestMapper openAiChatCompletionRequestMapper;
    private final OpenAiChatCompletionEncoder openAiChatCompletionEncoder;

    public OpenAiChatCompletionsController(
            DistributedKeyAuthenticationService distributedKeyAuthenticationService,
            GatewayChatExecutionService gatewayChatExecutionService,
            OpenAiChatCompletionRequestMapper openAiChatCompletionRequestMapper,
            tools.jackson.databind.ObjectMapper objectMapper) {
        this.distributedKeyAuthenticationService = distributedKeyAuthenticationService;
        this.gatewayChatExecutionService = gatewayChatExecutionService;
        this.openAiChatCompletionRequestMapper = openAiChatCompletionRequestMapper;
        this.openAiChatCompletionEncoder = new OpenAiChatCompletionEncoder(objectMapper);
    }

    @PostMapping
    public ResponseEntity<?> createCompletion(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody OpenAiChatCompletionRequest request) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        CanonicalRequest canonicalRequest = openAiChatCompletionRequestMapper.toCanonicalRequest(distributedKey, request);

        if (Boolean.TRUE.equals(request.stream())) {
            var streamResponse = gatewayChatExecutionService.executeGatewayStream(canonicalRequest);
            Flux<String> body = openAiChatCompletionEncoder.encodeStream(streamResponse);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(body);
        }

        var response = gatewayChatExecutionService.executeGatewayResponse(canonicalRequest);
        return ResponseEntity.ok(openAiChatCompletionEncoder.encode(response));
    }
}
