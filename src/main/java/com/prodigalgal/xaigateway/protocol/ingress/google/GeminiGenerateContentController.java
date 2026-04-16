package com.prodigalgal.xaigateway.protocol.ingress.google;

import com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionService;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionService;
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
    private final GatewayResourceExecutionService gatewayResourceExecutionService;
    private final GeminiGenerateContentRequestMapper geminiGenerateContentRequestMapper;
    private final GeminiGenerateContentEncoder geminiGenerateContentEncoder;
    private final GeminiGenerateContentModeResolver geminiGenerateContentModeResolver;
    private final GeminiGenerateContentResourceMapper geminiGenerateContentResourceMapper;
    private final GeminiGenerateContentResourceEncoder geminiGenerateContentResourceEncoder;

    public GeminiGenerateContentController(
            DistributedKeyAuthenticationService distributedKeyAuthenticationService,
            GatewayChatExecutionService gatewayChatExecutionService,
            GeminiGenerateContentRequestMapper geminiGenerateContentRequestMapper,
            GeminiGenerateContentEncoder geminiGenerateContentEncoder,
            GatewayResourceExecutionService gatewayResourceExecutionService,
            GeminiGenerateContentModeResolver geminiGenerateContentModeResolver,
            GeminiGenerateContentResourceMapper geminiGenerateContentResourceMapper,
            GeminiGenerateContentResourceEncoder geminiGenerateContentResourceEncoder) {
        this.distributedKeyAuthenticationService = distributedKeyAuthenticationService;
        this.gatewayChatExecutionService = gatewayChatExecutionService;
        this.gatewayResourceExecutionService = gatewayResourceExecutionService;
        this.geminiGenerateContentRequestMapper = geminiGenerateContentRequestMapper;
        this.geminiGenerateContentEncoder = geminiGenerateContentEncoder;
        this.geminiGenerateContentModeResolver = geminiGenerateContentModeResolver;
        this.geminiGenerateContentResourceMapper = geminiGenerateContentResourceMapper;
        this.geminiGenerateContentResourceEncoder = geminiGenerateContentResourceEncoder;
    }

    @PostMapping("/{model}:generateContent")
    public ResponseEntity<GeminiGenerateContentResponse> generateContent(
            @PathVariable String model,
            @RequestHeader(value = API_KEY_HEADER, required = false) String headerApiKey,
            @RequestParam(value = "key", required = false) String queryApiKey,
            @Valid @RequestBody GeminiGenerateContentRequest request) {
        AuthenticatedDistributedKey distributedKey = authenticate(headerApiKey, queryApiKey);
        GeminiGenerateContentModeResolver.GeminiGenerateContentMode mode = geminiGenerateContentModeResolver.resolve(request);
        if (mode == GeminiGenerateContentModeResolver.GeminiGenerateContentMode.IMAGE_GENERATION) {
            var resourceRequest = geminiGenerateContentResourceMapper.toImageGenerationRequest(distributedKey, model, request);
            var result = gatewayResourceExecutionService.executeDetailedJson(resourceRequest, distributedKey.id(), model);
            return ResponseEntity.ok(geminiGenerateContentResourceEncoder.encodeImageGeneration(result));
        }
        if (mode == GeminiGenerateContentModeResolver.GeminiGenerateContentMode.AUDIO_SPEECH) {
            var resourceRequest = geminiGenerateContentResourceMapper.toAudioSpeechRequest(distributedKey, model, request);
            var result = gatewayResourceExecutionService.executeDetailedBinaryJson(resourceRequest, distributedKey.id(), model);
            return ResponseEntity.ok(geminiGenerateContentResourceEncoder.encodeAudioSpeech(result));
        }
        CanonicalRequest canonicalRequest = geminiGenerateContentRequestMapper.toCanonicalRequest(distributedKey, model, request, false);
        var response = gatewayChatExecutionService.executeGatewayResponse(canonicalRequest);
        return ResponseEntity.ok(geminiGenerateContentEncoder.encode(response));
    }

    @PostMapping("/{model}:streamGenerateContent")
    public ResponseEntity<Flux<String>> streamGenerateContent(
            @PathVariable String model,
            @RequestHeader(value = API_KEY_HEADER, required = false) String headerApiKey,
            @RequestParam(value = "key", required = false) String queryApiKey,
            @Valid @RequestBody GeminiGenerateContentRequest request) {
        AuthenticatedDistributedKey distributedKey = authenticate(headerApiKey, queryApiKey);
        GeminiGenerateContentModeResolver.GeminiGenerateContentMode mode = geminiGenerateContentModeResolver.resolve(request);
        if (mode != GeminiGenerateContentModeResolver.GeminiGenerateContentMode.CHAT) {
            throw new IllegalArgumentException("resource-mode 当前不支持 streamGenerateContent。");
        }
        CanonicalRequest canonicalRequest = geminiGenerateContentRequestMapper.toCanonicalRequest(distributedKey, model, request, true);
        var streamResponse = gatewayChatExecutionService.executeGatewayStream(canonicalRequest);
        Flux<String> body = geminiGenerateContentEncoder.encodeStream(streamResponse);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(body);
    }

    private AuthenticatedDistributedKey authenticate(String headerApiKey, String queryApiKey) {
        String token = StringUtils.hasText(headerApiKey) ? headerApiKey : queryApiKey;
        return distributedKeyAuthenticationService.authenticateRawToken(token);
    }
}
