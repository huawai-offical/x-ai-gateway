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
@RequestMapping("/v1/fine_tuning/jobs")
public class OpenAiFineTuningJobsController {

    private final GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver;
    private final GatewayAsyncResourceService gatewayAsyncResourceService;

    public OpenAiFineTuningJobsController(
            GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver,
            GatewayAsyncResourceService gatewayAsyncResourceService) {
        this.gatewayTokenAuthenticationResolver = gatewayTokenAuthenticationResolver;
        this.gatewayAsyncResourceService = gatewayAsyncResourceService;
    }

    @PostMapping
    public JsonNode createTuning(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayAsyncResourceService.createTuning(distributedKey.id(), requestBody);
    }

    @GetMapping("/{jobId}")
    public JsonNode getTuning(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String jobId) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayAsyncResourceService.getTuning(jobId, distributedKey.id());
    }

    @PostMapping("/{jobId}/cancel")
    public JsonNode cancelTuning(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String jobId) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayAsyncResourceService.cancelTuning(jobId, distributedKey.id());
    }
}
