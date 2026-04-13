package com.prodigalgal.xaigateway.gateway.core.auth;

import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DistributedKeyAuthenticationService {

    private final DistributedKeyRepository distributedKeyRepository;
    private final DistributedKeySecretService distributedKeySecretService;
    private final AuthCacheStore authCacheStore;
    private final GatewayProperties gatewayProperties;

    public DistributedKeyAuthenticationService(
            DistributedKeyRepository distributedKeyRepository,
            DistributedKeySecretService distributedKeySecretService,
            AuthCacheStore authCacheStore,
            GatewayProperties gatewayProperties) {
        this.distributedKeyRepository = distributedKeyRepository;
        this.distributedKeySecretService = distributedKeySecretService;
        this.authCacheStore = authCacheStore;
        this.gatewayProperties = gatewayProperties;
    }

    public AuthenticatedDistributedKey authenticateBearerToken(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        return authenticateToken(token);
    }

    public AuthenticatedDistributedKey authenticateRawToken(String token) {
        if (token == null || token.isBlank()) {
            throw new GatewayUnauthorizedException("缺少网关 key。");
        }

        return authenticateToken(token.trim());
    }

    private AuthenticatedDistributedKey authenticateToken(String token) {
        int separatorIndex = token.indexOf('.');
        if (!token.startsWith("sk-gw-") || separatorIndex <= 6 || separatorIndex == token.length() - 1) {
            throw new GatewayUnauthorizedException("无效的网关 key。");
        }

        String keyPrefix = token.substring(0, separatorIndex);
        String secret = token.substring(separatorIndex + 1);

        String secretHash = distributedKeySecretService.hashSecret(secret);
        DistributedKeyAuthSnapshot snapshot = authCacheStore.get(keyPrefix)
                .orElseGet(() -> loadAndCache(keyPrefix));
        if (!secretHash.equals(snapshot.secretHash())) {
            throw new GatewayUnauthorizedException("网关 key 校验失败。");
        }

        return new AuthenticatedDistributedKey(
                snapshot.distributedKeyId(),
                snapshot.keyPrefix(),
                snapshot.keyName(),
                snapshot.allowedClientFamilies()
        );
    }

    private DistributedKeyAuthSnapshot loadAndCache(String keyPrefix) {
        DistributedKeyEntity entity = distributedKeyRepository.findByKeyPrefixAndActiveTrue(keyPrefix)
                .orElseThrow(() -> new GatewayUnauthorizedException("未找到可用的网关 key。"));
        DistributedKeyAuthSnapshot snapshot = new DistributedKeyAuthSnapshot(
                entity.getId(),
                entity.getKeyPrefix(),
                entity.getKeyName(),
                entity.getMaskedKey(),
                entity.getSecretHash(),
                entity.getAllowedClientFamilies()
        );
        authCacheStore.put(snapshot, gatewayProperties.getCache().getAuthTtl());
        return snapshot;
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new GatewayUnauthorizedException("缺少 Authorization 头。");
        }

        String trimmed = authorizationHeader.trim();
        if (trimmed.startsWith("Bearer ")) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }
}
