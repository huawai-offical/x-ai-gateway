package com.prodigalgal.xaigateway.protocol.ingress.publicapi;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayTokenAuthenticationResolver;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayPublicResourceService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping("/api/v1/operations")
public class GatewayOperationsController {

    private final GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver;
    private final GatewayPublicResourceService gatewayPublicResourceService;

    public GatewayOperationsController(
            GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver,
            GatewayPublicResourceService gatewayPublicResourceService) {
        this.gatewayTokenAuthenticationResolver = gatewayTokenAuthenticationResolver;
        this.gatewayPublicResourceService = gatewayPublicResourceService;
    }

    @GetMapping
    public ObjectNode list(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String status) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayPublicResourceService.listOperations(distributedKey.id(), resourceType, status);
    }

    @GetMapping("/{operationName}")
    public ObjectNode get(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String operationName) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayPublicResourceService.getOperation(distributedKey.id(), operationName);
    }

    @PostMapping("/{operationName}:cancel")
    public ObjectNode cancel(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String operationName) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayPublicResourceService.cancelOperation(distributedKey.id(), operationName);
    }

    @PostMapping("/{operationName}:wait")
    public ObjectNode wait(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String operationName,
            @RequestBody(required = false) JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayPublicResourceService.waitOperation(distributedKey.id(), operationName, requestBody);
    }

    @DeleteMapping("/{operationName}")
    public ObjectNode delete(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String operationName) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayPublicResourceService.deleteOperation(distributedKey.id(), operationName);
    }
}
