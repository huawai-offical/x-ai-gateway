package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayTokenAuthenticationResolver;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/realtime")
public class OpenAiRealtimeController {

    private final GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver;
    private final GatewayResourceExecutionService gatewayResourceExecutionService;

    public OpenAiRealtimeController(
            GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver,
            GatewayResourceExecutionService gatewayResourceExecutionService) {
        this.gatewayTokenAuthenticationResolver = gatewayTokenAuthenticationResolver;
        this.gatewayResourceExecutionService = gatewayResourceExecutionService;
    }

    @PostMapping("/client_secrets")
    public JsonNode createClientSecret(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayResourceExecutionService.executeLifecycleJson(
                distributedKey.id(),
                distributedKey.keyPrefix(),
                "POST",
                "/v1/realtime/client_secrets",
                "resource-orchestration",
                requestBody
        );
    }
}
