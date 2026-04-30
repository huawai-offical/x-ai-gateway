package com.prodigalgal.xaigateway.protocol.ingress.publicapi;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayTokenAuthenticationResolver;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayCacheResourceService;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
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
@RequestMapping("/api/v1/caches")
public class GatewayCachesController {

    private final GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver;
    private final GatewayCacheResourceService gatewayCacheResourceService;

    public GatewayCachesController(
            GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver,
            GatewayCacheResourceService gatewayCacheResourceService) {
        this.gatewayTokenAuthenticationResolver = gatewayTokenAuthenticationResolver;
        this.gatewayCacheResourceService = gatewayCacheResourceService;
    }

    @GetMapping
    public ObjectNode list(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam(required = false) ProviderType providerType,
            @RequestParam(required = false) String status) {
        AuthenticatedDistributedKey distributedKey = authenticate(authorization);
        return gatewayCacheResourceService.list(distributedKey.id(), providerType, status);
    }

    @PostMapping("/import")
    public ObjectNode importCache(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = authenticate(authorization);
        return gatewayCacheResourceService.importCache(distributedKey.id(), requestBody);
    }

    @GetMapping("/{cacheName}")
    public ObjectNode get(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String cacheName) {
        AuthenticatedDistributedKey distributedKey = authenticate(authorization);
        return gatewayCacheResourceService.get(distributedKey.id(), cacheName);
    }

    @DeleteMapping("/{cacheName}")
    public ObjectNode delete(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String cacheName) {
        AuthenticatedDistributedKey distributedKey = authenticate(authorization);
        return gatewayCacheResourceService.delete(distributedKey.id(), cacheName);
    }

    @PostMapping("/{cacheName}:invalidate")
    public ObjectNode invalidate(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String cacheName) {
        AuthenticatedDistributedKey distributedKey = authenticate(authorization);
        return gatewayCacheResourceService.invalidate(distributedKey.id(), cacheName);
    }

    @PostMapping("/{cacheName}:touch")
    public ObjectNode touch(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String cacheName) {
        AuthenticatedDistributedKey distributedKey = authenticate(authorization);
        return gatewayCacheResourceService.touch(distributedKey.id(), cacheName);
    }

    private AuthenticatedDistributedKey authenticate(String authorization) {
        return gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
    }
}
