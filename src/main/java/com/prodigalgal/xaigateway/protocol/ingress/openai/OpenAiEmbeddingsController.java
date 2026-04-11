package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayEmbeddingExecutionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/embeddings")
public class OpenAiEmbeddingsController {

    private final DistributedKeyAuthenticationService distributedKeyAuthenticationService;
    private final GatewayEmbeddingExecutionService gatewayEmbeddingExecutionService;

    public OpenAiEmbeddingsController(
            DistributedKeyAuthenticationService distributedKeyAuthenticationService,
            GatewayEmbeddingExecutionService gatewayEmbeddingExecutionService) {
        this.distributedKeyAuthenticationService = distributedKeyAuthenticationService;
        this.gatewayEmbeddingExecutionService = gatewayEmbeddingExecutionService;
    }

    @PostMapping
    public ResponseEntity<JsonNode> createEmbeddings(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayEmbeddingExecutionService.executeOpenAiEmbeddings(distributedKey.keyPrefix(), requestBody);
    }
}
