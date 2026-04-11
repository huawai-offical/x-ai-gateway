package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayTokenAuthenticationResolver;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/batches")
public class OpenAiBatchesController {

    private final GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver;
    private final GatewayAsyncResourceService gatewayAsyncResourceService;

    public OpenAiBatchesController(
            GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver,
            GatewayAsyncResourceService gatewayAsyncResourceService) {
        this.gatewayTokenAuthenticationResolver = gatewayTokenAuthenticationResolver;
        this.gatewayAsyncResourceService = gatewayAsyncResourceService;
    }

    @PostMapping
    public JsonNode createBatch(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayAsyncResourceService.createBatch(distributedKey.id(), requestBody);
    }

    @GetMapping("/{batchId}")
    public JsonNode getBatch(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String batchId) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayAsyncResourceService.getBatch(batchId, distributedKey.id());
    }

    @PostMapping("/{batchId}/cancel")
    public JsonNode cancelBatch(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String batchId) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayAsyncResourceService.cancelBatch(batchId, distributedKey.id());
    }
}
