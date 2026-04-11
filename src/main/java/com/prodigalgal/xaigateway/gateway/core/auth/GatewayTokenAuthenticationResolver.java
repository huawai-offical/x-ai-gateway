package com.prodigalgal.xaigateway.gateway.core.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class GatewayTokenAuthenticationResolver {

    private final DistributedKeyAuthenticationService distributedKeyAuthenticationService;

    public GatewayTokenAuthenticationResolver(
            DistributedKeyAuthenticationService distributedKeyAuthenticationService) {
        this.distributedKeyAuthenticationService = distributedKeyAuthenticationService;
    }

    public AuthenticatedDistributedKey authenticate(
            String authorization,
            String xApiKey,
            String xGoogApiKey,
            String keyQuery) {
        if (authorization != null && !authorization.isBlank()) {
            return distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        }
        if (xApiKey != null && !xApiKey.isBlank()) {
            return distributedKeyAuthenticationService.authenticateRawToken(xApiKey);
        }
        if (xGoogApiKey != null && !xGoogApiKey.isBlank()) {
            return distributedKeyAuthenticationService.authenticateRawToken(xGoogApiKey);
        }
        if (keyQuery != null && !keyQuery.isBlank()) {
            return distributedKeyAuthenticationService.authenticateRawToken(keyQuery);
        }
        throw new GatewayUnauthorizedException(
                "缺少网关 key，请通过 " + HttpHeaders.AUTHORIZATION + "、x-api-key、x-goog-api-key 或 key 提供。");
    }
}
