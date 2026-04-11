package com.prodigalgal.xaigateway.protocol.ingress.interop;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayTokenAuthenticationResolver;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayInteropPlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/interop")
public class InteropPlanController {

    private final GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver;
    private final GatewayInteropPlanService gatewayInteropPlanService;

    public InteropPlanController(
            GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver,
            GatewayInteropPlanService gatewayInteropPlanService) {
        this.gatewayTokenAuthenticationResolver = gatewayTokenAuthenticationResolver;
        this.gatewayInteropPlanService = gatewayInteropPlanService;
    }

    @PostMapping("/plan")
    public InteropPlanResponse plan(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "x-api-key", required = false) String xApiKey,
            @RequestHeader(value = "x-goog-api-key", required = false) String xGoogApiKey,
            @RequestParam(value = "key", required = false) String key,
            @Valid @RequestBody InteropPlanRequest request) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(
                authorization,
                xApiKey,
                xGoogApiKey,
                key
        );
        return gatewayInteropPlanService.preview(distributedKey.keyPrefix(), request);
    }
}
