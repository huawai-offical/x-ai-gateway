package com.prodigalgal.xaigateway.protocol.ingress.publicapi;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayTokenAuthenticationResolver;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayPublicResourceService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1")
public class GatewayMediaTasksController {

    private final GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver;
    private final GatewayPublicResourceService gatewayPublicResourceService;

    public GatewayMediaTasksController(
            GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver,
            GatewayPublicResourceService gatewayPublicResourceService) {
        this.gatewayTokenAuthenticationResolver = gatewayTokenAuthenticationResolver;
        this.gatewayPublicResourceService = gatewayPublicResourceService;
    }

    @PostMapping("/videos/generations")
    public JsonNode createVideo(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayPublicResourceService.createVideo(distributedKey.id(), requestBody);
    }

    @GetMapping("/media/provider-matrix")
    public JsonNode mediaProviderMatrix(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayPublicResourceService.mediaProviderMatrix();
    }

    @GetMapping("/videos/{videoId}")
    public JsonNode getVideo(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String videoId) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayPublicResourceService.getVideo(distributedKey.id(), videoId);
    }

    @PostMapping("/videos/{videoId}/cancel")
    public JsonNode cancelVideo(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String videoId) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayPublicResourceService.cancelVideo(distributedKey.id(), videoId);
    }

    @GetMapping("/videos/{videoId}/download")
    public JsonNode downloadVideo(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String videoId) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayPublicResourceService.downloadVideo(distributedKey.id(), videoId);
    }

    @PostMapping("/music/generations")
    public JsonNode createMusic(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayPublicResourceService.createMusic(distributedKey.id(), requestBody);
    }

    @GetMapping("/music/{musicId}")
    public JsonNode getMusic(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String musicId) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayPublicResourceService.getMusic(distributedKey.id(), musicId);
    }

    @PostMapping("/music/{musicId}/cancel")
    public JsonNode cancelMusic(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String musicId) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayPublicResourceService.cancelMusic(distributedKey.id(), musicId);
    }

    @GetMapping("/music/{musicId}/download")
    public JsonNode downloadMusic(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String musicId) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayPublicResourceService.downloadMusic(distributedKey.id(), musicId);
    }
}
