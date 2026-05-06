package com.prodigalgal.xaigateway.portal.application;

import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class SocialOAuthJwksCache {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final GatewayProperties gatewayProperties;
    private final Map<String, CachedJwks> cache = new ConcurrentHashMap<>();

    public SocialOAuthJwksCache(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            GatewayProperties gatewayProperties) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.gatewayProperties = gatewayProperties;
    }

    public JsonNode getJwks(String jwksUri) {
        if (jwksUri == null || jwksUri.isBlank()) {
            throw new IllegalArgumentException("JWKS URI 不能为空。");
        }
        Instant now = Instant.now();
        CachedJwks cached = cache.get(jwksUri);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.jwks();
        }
        return refresh(jwksUri);
    }

    public JsonNode refresh(String jwksUri) {
        JsonNode jwks = webClient.get()
                .uri(URI.create(jwksUri))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        validate(jwks);
        cache.put(jwksUri, new CachedJwks(jwks, Instant.now().plus(ttl())));
        return jwks;
    }

    public void clear() {
        cache.clear();
    }

    private void validate(JsonNode jwks) {
        JsonNode keys = jwks == null ? null : jwks.path("keys");
        if (keys == null || !keys.isArray()) {
            throw new IllegalArgumentException("JWKS 响应缺少 keys。");
        }
    }

    private Duration ttl() {
        Duration ttl = gatewayProperties.getOauth().getSocialJwksCacheTtl();
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            return Duration.ofMinutes(30);
        }
        return ttl;
    }

    private record CachedJwks(JsonNode jwks, Instant expiresAt) {
    }
}
