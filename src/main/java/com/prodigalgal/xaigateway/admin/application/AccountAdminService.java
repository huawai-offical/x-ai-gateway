package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ExportedClientConfigResponse;
import com.prodigalgal.xaigateway.admin.api.AccountImportAuthJsonRequest;
import com.prodigalgal.xaigateway.admin.api.UpstreamAccountResponse;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountPoolEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountPoolRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class AccountAdminService {

    private final UpstreamAccountRepository upstreamAccountRepository;
    private final UpstreamAccountPoolRepository upstreamAccountPoolRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final SupportedModelCatalogService supportedModelCatalogService;

    public AccountAdminService(
            UpstreamAccountRepository upstreamAccountRepository,
            UpstreamAccountPoolRepository upstreamAccountPoolRepository,
            CredentialCryptoService credentialCryptoService,
            SupportedModelCatalogService supportedModelCatalogService) {
        this.upstreamAccountRepository = upstreamAccountRepository;
        this.upstreamAccountPoolRepository = upstreamAccountPoolRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.supportedModelCatalogService = supportedModelCatalogService;
    }

    @Transactional(readOnly = true)
    public List<UpstreamAccountResponse> list(Long poolId) {
        if (poolId == null) {
            return upstreamAccountRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
        }
        return listByPool(poolId);
    }

    @Transactional(readOnly = true)
    public List<UpstreamAccountResponse> listByPool(Long poolId) {
        return upstreamAccountRepository.findAllByPool_IdOrderByCreatedAtDesc(poolId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UpstreamAccountResponse get(Long id) {
        return toResponse(getRequired(id));
    }

    public UpstreamAccountResponse toggleFrozen(Long id, boolean frozen) {
        UpstreamAccountEntity entity = getRequired(id);
        entity.setFrozen(frozen);
        return toResponse(upstreamAccountRepository.save(entity));
    }

    public UpstreamAccountResponse refresh(Long id) {
        UpstreamAccountEntity entity = getRequired(id);
        entity.setLastRefreshAt(Instant.now());
        entity.setHealthy(true);
        entity.setLastErrorMessage(null);
        return toResponse(upstreamAccountRepository.save(entity));
    }

    public UpstreamAccountResponse updateNetwork(Long id, Long proxyId, Long tlsFingerprintProfileId) {
        UpstreamAccountEntity entity = getRequired(id);
        entity.setProxyId(proxyId);
        entity.setTlsFingerprintProfileId(tlsFingerprintProfileId);
        return toResponse(upstreamAccountRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public ExportedClientConfigResponse exportConfig(Long id, String clientFamily) {
        UpstreamAccountEntity entity = getRequired(id);
        String token = entity.getAccessTokenCiphertext() == null ? "" : credentialCryptoService.decrypt(entity.getAccessTokenCiphertext());
        String config = switch (entity.getProviderType()) {
            case OPENAI_OAUTH -> "{\n  \"OPENAI_API_KEY\": \"" + token + "\"\n}";
            case GEMINI_OAUTH -> "{\n  \"GEMINI_API_KEY\": \"" + token + "\"\n}";
            case CLAUDE_ACCOUNT -> "{\n  \"ANTHROPIC_API_KEY\": \"" + token + "\"\n}";
        };
        return new ExportedClientConfigResponse(entity.getAccountName(), clientFamily, config);
    }

    public UpstreamAccountResponse importAuthJson(AccountImportAuthJsonRequest request) {
        UpstreamAccountPoolEntity pool = resolvePool(request.poolId());

        String accessToken = request.accessToken().trim();
        if (accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken 不能为空。");
        }

        UpstreamAccountEntity entity = new UpstreamAccountEntity();
        entity.setPool(pool);
        com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType providerType =
                pool != null
                        ? pool.getProviderType()
                        : com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType.OPENAI_OAUTH;
        entity.setProviderType(providerType);
        entity.setAccountName(resolveAccountName(request.accountName(), pool == null ? null : pool.getPoolName()));
        entity.setExternalAccountId(resolveExternalAccountId(request.externalAccountId(), providerType.name()));
        entity.setAccessTokenCiphertext(credentialCryptoService.encrypt(accessToken));
        entity.setRefreshTokenCiphertext(request.refreshToken() == null || request.refreshToken().isBlank()
                ? null
                : credentialCryptoService.encrypt(request.refreshToken().trim()));
        entity.setActive(request.active() == null || request.active());
        entity.setFrozen(false);
        entity.setHealthy(true);
        entity.setLastRefreshAt(Instant.now());
        entity.setMetadataJson(request.metadataJson() == null || request.metadataJson().isBlank() ? "{}" : request.metadataJson().trim());
        entity.setSupportedModels(supportedModelCatalogService.resolveForAccountImport(pool, request.supportedModels()));
        entity.setProxyId(request.proxyId());
        entity.setTlsFingerprintProfileId(request.tlsFingerprintProfileId());
        entity.setSiteProfileId(request.siteProfileId());

        return toResponse(upstreamAccountRepository.save(entity));
    }

    private UpstreamAccountEntity getRequired(Long id) {
        return upstreamAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定账号。"));
    }

    private UpstreamAccountPoolEntity resolvePool(Long poolId) {
        if (poolId == null) {
            return null;
        }
        return upstreamAccountPoolRepository.findById(poolId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定账号池。"));
    }

    private String resolveAccountName(String accountName, String poolName) {
        if (accountName != null && !accountName.isBlank()) {
            return accountName.trim();
        }
        String namePrefix = poolName == null || poolName.isBlank() ? "unassigned" : poolName;
        return namePrefix + "-" + Instant.now().toEpochMilli();
    }

    private String resolveExternalAccountId(String externalAccountId, String providerName) {
        if (externalAccountId != null && !externalAccountId.isBlank()) {
            return externalAccountId.trim();
        }
        return providerName.toLowerCase() + ":" + Instant.now().toEpochMilli();
    }

    private UpstreamAccountResponse toResponse(UpstreamAccountEntity entity) {
        long totalRequests = entity.getTotalRequestCount();
        long successRequests = entity.getSuccessfulRequestCount();
        long totalTokens = entity.getTotalTokenCount();
        long cacheHitTokens = entity.getTotalCacheHitTokenCount();
        long durationSamples = entity.getDurationSampleCount();
        long firstTokenSamples = entity.getFirstTokenSampleCount();
        return new UpstreamAccountResponse(
                entity.getId(),
                entity.getPool() == null ? null : entity.getPool().getId(),
                entity.getAccountName(),
                entity.getProviderType(),
                supportedModelCatalogService.normalize(entity.getSupportedModels()),
                entity.getExternalAccountId(),
                entity.isActive(),
                entity.isFrozen(),
                entity.isHealthy(),
                entity.getLastErrorMessage(),
                entity.getProxyId(),
                entity.getTlsFingerprintProfileId(),
                entity.getLastRefreshAt(),
                entity.getLastUsedAt(),
                totalRequests,
                successRequests,
                entity.getFailedRequestCount(),
                entity.getCanceledRequestCount(),
                totalTokens,
                cacheHitTokens,
                entity.getTotalCacheWriteTokenCount(),
                entity.getTotalSavedInputTokenCount(),
                ratio(successRequests, totalRequests),
                ratio(cacheHitTokens, totalTokens),
                entity.getTotalDurationMs(),
                durationSamples,
                ratio(entity.getTotalDurationMs(), durationSamples),
                entity.getTotalFirstTokenMs(),
                firstTokenSamples,
                ratio(entity.getTotalFirstTokenMs(), firstTokenSamples),
                entity.getLastFirstTokenMs(),
                entity.getMinFirstTokenMs(),
                entity.getMaxFirstTokenMs(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return ((double) numerator) / denominator;
    }
}
