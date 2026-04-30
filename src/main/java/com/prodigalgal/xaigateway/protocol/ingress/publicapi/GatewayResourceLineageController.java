package com.prodigalgal.xaigateway.protocol.ingress.publicapi;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayTokenAuthenticationResolver;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayPublicResourceService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping("/api/v1/resources")
public class GatewayResourceLineageController {

    private final GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver;
    private final GatewayPublicResourceService gatewayPublicResourceService;

    public GatewayResourceLineageController(
            GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver,
            GatewayPublicResourceService gatewayPublicResourceService) {
        this.gatewayTokenAuthenticationResolver = gatewayTokenAuthenticationResolver;
        this.gatewayPublicResourceService = gatewayPublicResourceService;
    }

    @GetMapping("/{resourceType}/{resourceId}/lineage")
    public ObjectNode lineage(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String resourceType,
            @PathVariable String resourceId) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayPublicResourceService.lineage(distributedKey.id(), resourceType, resourceId);
    }
}
