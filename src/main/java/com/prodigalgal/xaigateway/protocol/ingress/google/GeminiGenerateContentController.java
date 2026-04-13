package com.prodigalgal.xaigateway.protocol.ingress.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionService;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/v1beta/models")
public class GeminiGenerateContentController {

    private static final String API_KEY_HEADER = "x-goog-api-key";

    private final DistributedKeyAuthenticationService distributedKeyAuthenticationService;
    private final GatewayChatExecutionService gatewayChatExecutionService;
    private final GeminiGenerateContentRequestMapper geminiGenerateContentRequestMapper;
    private final GeminiGenerateContentEncoder geminiGenerateContentEncoder;

    public GeminiGenerateContentController(
            DistributedKeyAuthenticationService distributedKeyAuthenticationService,
            GatewayChatExecutionService gatewayChatExecutionService,
            GeminiGenerateContentRequestMapper geminiGenerateContentRequestMapper,
            ObjectMapper objectMapper) {
        this.distributedKeyAuthenticationService = distributedKeyAuthenticationService;
        this.gatewayChatExecutionService = gatewayChatExecutionService;
        this.geminiGenerateContentRequestMapper = geminiGenerateContentRequestMapper;
        this.geminiGenerateContentEncoder = new GeminiGenerateContentEncoder(objectMapper);
    }

    @PostMapping("/{model}:generateContent")
    public ResponseEntity<GeminiGenerateContentResponse> generateContent(
            @PathVariable String model,
            @RequestHeader(value = API_KEY_HEADER, required = false) String headerApiKey,
            @RequestParam(value = "key", required = false) String queryApiKey,
            @Valid @RequestBody GeminiGenerateContentRequest request) {
        AuthenticatedDistributedKey distributedKey = authenticate(headerApiKey, queryApiKey);
        ChatExecutionRequest executionRequest = toExecutionRequest(distributedKey, model, request, false);
        var response = gatewayChatExecutionService.executeGatewayResponse(executionRequest);
        return ResponseEntity.ok(geminiGenerateContentEncoder.encode(response));
    }

    @PostMapping("/{model}:streamGenerateContent")
    public ResponseEntity<Flux<String>> streamGenerateContent(
            @PathVariable String model,
            @RequestHeader(value = API_KEY_HEADER, required = false) String headerApiKey,
            @RequestParam(value = "key", required = false) String queryApiKey,
            @Valid @RequestBody GeminiGenerateContentRequest request) {
        AuthenticatedDistributedKey distributedKey = authenticate(headerApiKey, queryApiKey);
        ChatExecutionRequest executionRequest = toExecutionRequest(distributedKey, model, request, true);
        var streamResponse = gatewayChatExecutionService.executeGatewayStream(executionRequest);
        Flux<String> body = geminiGenerateContentEncoder.encodeStream(streamResponse);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(body);
    }

    private AuthenticatedDistributedKey authenticate(String headerApiKey, String queryApiKey) {
        String token = StringUtils.hasText(headerApiKey) ? headerApiKey : queryApiKey;
        return distributedKeyAuthenticationService.authenticateRawToken(token);
    }

    private ChatExecutionRequest toExecutionRequest(
            AuthenticatedDistributedKey distributedKey,
            String model,
            GeminiGenerateContentRequest request,
            boolean stream) {
        return geminiGenerateContentRequestMapper.toExecutionRequest(distributedKey, model, request, stream);
    }

}
