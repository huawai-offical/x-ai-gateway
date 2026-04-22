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

    public AccountAdminService(
            UpstreamAccountRepository upstreamAccountRepository,
            UpstreamAccountPoolRepository upstreamAccountPoolRepository,
            CredentialCryptoService credentialCryptoService) {
        this.upstreamAccountRepository = upstreamAccountRepository;
        this.upstreamAccountPoolRepository = upstreamAccountPoolRepository;
        this.credentialCryptoService = credentialCryptoService;
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
        UpstreamAccountPoolEntity pool = upstreamAccountPoolRepository.findById(request.poolId())
                .orElseThrow(() -> new IllegalArgumentException("未找到指定账号池。"));

        String accessToken = request.accessToken().trim();
        if (accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken 不能为空。");
        }

        UpstreamAccountEntity entity = new UpstreamAccountEntity();
        entity.setPool(pool);
        entity.setProviderType(pool.getProviderType());
        entity.setAccountName(resolveAccountName(request.accountName(), pool.getPoolName()));
        entity.setExternalAccountId(resolveExternalAccountId(request.externalAccountId(), pool.getProviderType().name()));
        entity.setAccessTokenCiphertext(credentialCryptoService.encrypt(accessToken));
        entity.setRefreshTokenCiphertext(request.refreshToken() == null || request.refreshToken().isBlank()
                ? null
                : credentialCryptoService.encrypt(request.refreshToken().trim()));
        entity.setActive(request.active() == null || request.active());
        entity.setFrozen(false);
        entity.setHealthy(true);
        entity.setLastRefreshAt(Instant.now());
        entity.setMetadataJson(request.metadataJson() == null || request.metadataJson().isBlank() ? "{}" : request.metadataJson().trim());
        entity.setProxyId(request.proxyId());
        entity.setTlsFingerprintProfileId(request.tlsFingerprintProfileId());
        entity.setSiteProfileId(request.siteProfileId());

        return toResponse(upstreamAccountRepository.save(entity));
    }

    private UpstreamAccountEntity getRequired(Long id) {
        return upstreamAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定账号。"));
    }

    private String resolveAccountName(String accountName, String poolName) {
        if (accountName != null && !accountName.isBlank()) {
            return accountName.trim();
        }
        return poolName + "-" + Instant.now().toEpochMilli();
    }

    private String resolveExternalAccountId(String externalAccountId, String providerName) {
        if (externalAccountId != null && !externalAccountId.isBlank()) {
            return externalAccountId.trim();
        }
        return providerName.toLowerCase() + ":" + Instant.now().toEpochMilli();
    }

    private UpstreamAccountResponse toResponse(UpstreamAccountEntity entity) {
        return new UpstreamAccountResponse(
                entity.getId(),
                entity.getPool().getId(),
                entity.getAccountName(),
                entity.getProviderType(),
                entity.getExternalAccountId(),
                entity.isActive(),
                entity.isFrozen(),
                entity.isHealthy(),
                entity.getLastErrorMessage(),
                entity.getProxyId(),
                entity.getTlsFingerprintProfileId(),
                entity.getLastRefreshAt(),
                entity.getLastUsedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
