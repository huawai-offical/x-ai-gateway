package com.prodigalgal.xaigateway.gateway.core.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisRouteCacheStore implements RouteCacheStore {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final GatewayProperties gatewayProperties;

    public RedisRouteCacheStore(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            GatewayProperties gatewayProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.gatewayProperties = gatewayProperties;
    }

    @Override
    public Optional<RoutePlanSnapshot> get(Long distributedKeyId, String protocol, String requestPath, String requestedModel, GatewayRequestSemantics semantics) {
        String payload;
        try {
            payload = stringRedisTemplate.opsForValue().get(cacheKey(distributedKeyId, protocol, requestPath, requestedModel, semantics));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(payload, RoutePlanSnapshot.class));
        } catch (Exception exception) {
            stringRedisTemplate.delete(cacheKey(distributedKeyId, protocol, requestPath, requestedModel, semantics));
            return Optional.empty();
        }
    }

    @Override
    public void put(Long distributedKeyId, String protocol, String requestPath, String requestedModel, GatewayRequestSemantics semantics, RoutePlanSnapshot snapshot, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(
                    cacheKey(distributedKeyId, protocol, requestPath, requestedModel, semantics),
                    objectMapper.writeValueAsString(snapshot),
                    ttl == null ? gatewayProperties.getCache().getRouteTtl() : ttl
            );
        } catch (RuntimeException exception) {
            // ignore in local mode
        } catch (Exception exception) {
            throw new IllegalStateException("无法写入路由缓存快照。", exception);
        }
    }

    @Override
    public void invalidate(Long distributedKeyId, String protocol, String requestPath, String requestedModel, GatewayRequestSemantics semantics) {
        try {
            stringRedisTemplate.delete(cacheKey(distributedKeyId, protocol, requestPath, requestedModel, semantics));
        } catch (RuntimeException exception) {
            // ignore in local mode
        }
    }

    private String cacheKey(Long distributedKeyId, String protocol, String requestPath, String requestedModel, GatewayRequestSemantics semantics) {
        String signature = distributedKeyId
                + "|" + protocol
                + "|" + requestPath
                + "|" + requestedModel
                + "|" + semantics.resourceType()
                + "|" + semantics.operation()
                + "|" + semantics.requiresRouteSelection()
                + "|" + semantics.requiredFeatures().stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        return gatewayProperties.getCache().getKeyPrefix() + ":route:plan:" + digest(signature);
    }

    private String digest(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境缺少 SHA-256。", exception);
        }
    }
}
