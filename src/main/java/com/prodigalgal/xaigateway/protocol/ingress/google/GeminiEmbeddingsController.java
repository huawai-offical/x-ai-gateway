package com.prodigalgal.xaigateway.protocol.ingress.google;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.NonChatCanonicalRenderService;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionService;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import java.util.List;
import java.util.Map;
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
import tools.jackson.databind.JsonNode;

@Validated
@RestController
@RequestMapping("/v1beta/models")
public class GeminiEmbeddingsController {

    private static final String API_KEY_HEADER = "x-goog-api-key";

    private final DistributedKeyAuthenticationService distributedKeyAuthenticationService;
    private final GatewayResourceExecutionService gatewayResourceExecutionService;
    private final GeminiEmbeddingsRequestMapper geminiEmbeddingsRequestMapper;
    private final NonChatCanonicalRenderService nonChatCanonicalRenderService;

    public GeminiEmbeddingsController(
            DistributedKeyAuthenticationService distributedKeyAuthenticationService,
            GatewayResourceExecutionService gatewayResourceExecutionService,
            GeminiEmbeddingsRequestMapper geminiEmbeddingsRequestMapper,
            NonChatCanonicalRenderService nonChatCanonicalRenderService) {
        this.distributedKeyAuthenticationService = distributedKeyAuthenticationService;
        this.gatewayResourceExecutionService = gatewayResourceExecutionService;
        this.geminiEmbeddingsRequestMapper = geminiEmbeddingsRequestMapper;
        this.nonChatCanonicalRenderService = nonChatCanonicalRenderService;
    }

    @PostMapping("/{model}:embedContent")
    public ResponseEntity<JsonNode> embedContent(
            @PathVariable String model,
            @RequestHeader(value = API_KEY_HEADER, required = false) String headerApiKey,
            @RequestParam(value = "key", required = false) String queryApiKey,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = authenticate(headerApiKey, queryApiKey);
        CanonicalResourceRequest request = buildEmbeddingsRequest(
                distributedKey,
                model,
                "/v1beta/models/" + model + ":embedContent",
                geminiEmbeddingsRequestMapper.toEmbedRequest(model, requestBody)
        );
        return (ResponseEntity<JsonNode>) nonChatCanonicalRenderService
                .render(request, null, gatewayResourceExecutionService.executeDetailedJson(request, distributedKey.id(), model))
                .response();
    }

    @PostMapping("/{model}:batchEmbedContents")
    public ResponseEntity<JsonNode> batchEmbedContents(
            @PathVariable String model,
            @RequestHeader(value = API_KEY_HEADER, required = false) String headerApiKey,
            @RequestParam(value = "key", required = false) String queryApiKey,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = authenticate(headerApiKey, queryApiKey);
        CanonicalResourceRequest request = buildEmbeddingsRequest(
                distributedKey,
                model,
                "/v1beta/models/" + model + ":batchEmbedContents",
                geminiEmbeddingsRequestMapper.toBatchEmbedRequest(model, requestBody)
        );
        return (ResponseEntity<JsonNode>) nonChatCanonicalRenderService
                .render(request, null, gatewayResourceExecutionService.executeDetailedJson(request, distributedKey.id(), model))
                .response();
    }

    private CanonicalResourceRequest buildEmbeddingsRequest(
            AuthenticatedDistributedKey distributedKey,
            String model,
            String requestPath,
            JsonNode requestBody) {
        return new CanonicalResourceRequest(
                distributedKey.keyPrefix(),
                CanonicalIngressProtocol.GOOGLE_NATIVE,
                "POST",
                requestPath,
                "/v1/embeddings",
                Map.of("model", model),
                model,
                TranslationResourceType.EMBEDDING,
                TranslationOperation.EMBEDDING_CREATE,
                requestBody,
                Map.of(),
                List.of(),
                false,
                false
        );
    }

    private AuthenticatedDistributedKey authenticate(String headerApiKey, String queryApiKey) {
        String token = StringUtils.hasText(headerApiKey) ? headerApiKey : queryApiKey;
        return distributedKeyAuthenticationService.authenticateRawToken(token);
    }
}
