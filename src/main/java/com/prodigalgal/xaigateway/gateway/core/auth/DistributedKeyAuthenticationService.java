package com.prodigalgal.xaigateway.gateway.core.auth;

import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DistributedKeyAuthenticationService {

    private final DistributedKeyRepository distributedKeyRepository;
    private final DistributedKeySecretService distributedKeySecretService;

    public DistributedKeyAuthenticationService(
            DistributedKeyRepository distributedKeyRepository,
            DistributedKeySecretService distributedKeySecretService) {
        this.distributedKeyRepository = distributedKeyRepository;
        this.distributedKeySecretService = distributedKeySecretService;
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

        Optional<DistributedKeyEntity> entity = distributedKeyRepository.findByKeyPrefixAndActiveTrue(keyPrefix);
        if (entity.isEmpty()) {
            throw new GatewayUnauthorizedException("未找到可用的网关 key。");
        }

        String secretHash = distributedKeySecretService.hashSecret(secret);
        if (!secretHash.equals(entity.get().getSecretHash())) {
            throw new GatewayUnauthorizedException("网关 key 校验失败。");
        }

        return new AuthenticatedDistributedKey(
                entity.get().getId(),
                entity.get().getKeyPrefix(),
                entity.get().getKeyName(),
                entity.get().getAllowedClientFamilies()
        );
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
