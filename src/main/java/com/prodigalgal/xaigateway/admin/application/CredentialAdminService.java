package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.CredentialConnectivityRequest;
import com.prodigalgal.xaigateway.admin.api.CredentialConnectivityResponse;
import com.prodigalgal.xaigateway.admin.api.CredentialModelRefreshResponse;
import com.prodigalgal.xaigateway.admin.api.CredentialRequest;
import com.prodigalgal.xaigateway.admin.api.CredentialResponse;
import com.prodigalgal.xaigateway.gateway.core.catalog.CredentialModelDiscoveryService;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CredentialAdminService {

    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final CredentialModelDiscoveryService credentialModelDiscoveryService;

    public CredentialAdminService(
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialCryptoService credentialCryptoService,
            CredentialModelDiscoveryService credentialModelDiscoveryService) {
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.credentialModelDiscoveryService = credentialModelDiscoveryService;
    }

    @Transactional(readOnly = true)
    public List<CredentialResponse> list() {
        return upstreamCredentialRepository.findAllByDeletedFalseOrderByCreatedAtDesc().stream()
                .sorted(Comparator.comparing(UpstreamCredentialEntity::getCreatedAt).reversed())
                .map(this::toResponse)
                .toList();
    }

    public CredentialResponse create(CredentialRequest request) {
        String fingerprint = credentialCryptoService.fingerprint(request.apiKey().trim());
        if (upstreamCredentialRepository.findByApiKeyFingerprintAndDeletedFalse(fingerprint).isPresent()) {
            throw new IllegalArgumentException("已存在相同的上游 API key。");
        }

        UpstreamCredentialEntity entity = new UpstreamCredentialEntity();
        apply(entity, request);
        return toResponse(upstreamCredentialRepository.save(entity));
    }

    public CredentialResponse update(Long id, CredentialRequest request) {
        UpstreamCredentialEntity entity = getRequired(id);
        apply(entity, request);
        return toResponse(upstreamCredentialRepository.save(entity));
    }

    public CredentialResponse toggle(Long id, boolean active) {
        UpstreamCredentialEntity entity = getRequired(id);
        entity.setActive(active);
        return toResponse(upstreamCredentialRepository.save(entity));
    }

    public void delete(Long id) {
        UpstreamCredentialEntity entity = getRequired(id);
        entity.setDeleted(true);
        entity.setActive(false);
        upstreamCredentialRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public CredentialConnectivityResponse testConnectivity(CredentialConnectivityRequest request) {
        CredentialModelDiscoveryService.CredentialConnectivityProbe probe =
                credentialModelDiscoveryService.probe(
                        request.providerType(),
                        request.baseUrl().trim(),
                        request.apiKey().trim()
                );
        List<String> sampleModels = probe.models().stream()
                .map(model -> model.modelName())
                .limit(10)
                .toList();
        return new CredentialConnectivityResponse(
                probe.providerType(),
                probe.baseUrl(),
                true,
                probe.latencyMs(),
                probe.models().size(),
                sampleModels,
                "联通性测试成功。"
        );
    }

    public CredentialModelRefreshResponse refreshModels(Long credentialId) {
        CredentialModelDiscoveryService.CredentialRefreshResult result =
                credentialModelDiscoveryService.refreshCredential(credentialId);
        return new CredentialModelRefreshResponse(
                result.credentialId(),
                result.models().size(),
                result.models().stream().map(model -> model.modelName()).limit(10).toList(),
                result.refreshedAt()
        );
    }

    private UpstreamCredentialEntity getRequired(Long id) {
        Optional<UpstreamCredentialEntity> entity = upstreamCredentialRepository.findById(id);
        if (entity.isEmpty() || entity.get().isDeleted()) {
            throw new IllegalArgumentException("未找到指定的上游凭证。");
        }
        return entity.get();
    }

    private void apply(UpstreamCredentialEntity entity, CredentialRequest request) {
        entity.setCredentialName(request.credentialName().trim());
        entity.setProviderType(request.providerType());
        entity.setBaseUrl(request.baseUrl().trim());
        entity.setApiKeyCiphertext(credentialCryptoService.encrypt(request.apiKey().trim()));
        entity.setApiKeyFingerprint(credentialCryptoService.fingerprint(request.apiKey().trim()));
        entity.setActive(request.active() == null || request.active());
        entity.setProxyId(request.proxyId());
        entity.setTlsFingerprintProfileId(request.tlsFingerprintProfileId());
    }

    private CredentialResponse toResponse(UpstreamCredentialEntity entity) {
        return new CredentialResponse(
                entity.getId(),
                entity.getCredentialName(),
                entity.getProviderType(),
                entity.getBaseUrl(),
                entity.getApiKeyFingerprint(),
                entity.isActive(),
                entity.getCooldownUntil(),
                entity.getLastErrorCode(),
                entity.getLastErrorMessage(),
                entity.getLastErrorAt(),
                entity.getLastUsedAt(),
                entity.getProxyId(),
                entity.getTlsFingerprintProfileId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
