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
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping("/api/v1/tunings")
public class GatewayTuningsController {

    private final GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver;
    private final GatewayPublicResourceService gatewayPublicResourceService;

    public GatewayTuningsController(
            GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver,
            GatewayPublicResourceService gatewayPublicResourceService) {
        this.gatewayTokenAuthenticationResolver = gatewayTokenAuthenticationResolver;
        this.gatewayPublicResourceService = gatewayPublicResourceService;
    }

    @PostMapping
    public JsonNode create(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayPublicResourceService.createTuning(distributedKey.id(), requestBody);
    }

    @GetMapping
    public JsonNode list(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayPublicResourceService.listTunings(distributedKey.id());
    }

    @GetMapping("/{tuningId}")
    public JsonNode get(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String tuningId) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayPublicResourceService.getTuning(distributedKey.id(), tuningId);
    }

    @PostMapping("/{tuningId}:cancel")
    public JsonNode cancel(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String tuningId) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayPublicResourceService.cancelTuning(distributedKey.id(), tuningId);
    }

    @DeleteMapping("/{tuningId}")
    public ObjectNode delete(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String tuningId) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayPublicResourceService.deleteTuning(distributedKey.id(), tuningId);
    }

    @PostMapping("/{tuningId}:import")
    public ObjectNode importModel(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String tuningId,
            @RequestBody(required = false) JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayPublicResourceService.importTuning(distributedKey.id(), tuningId, requestBody);
    }
}
