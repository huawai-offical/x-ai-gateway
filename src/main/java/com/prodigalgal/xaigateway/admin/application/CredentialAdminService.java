package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.CredentialConnectivityRequest;
import com.prodigalgal.xaigateway.admin.api.CredentialConnectivityResponse;
import com.prodigalgal.xaigateway.admin.api.CredentialModelRefreshResponse;
import com.prodigalgal.xaigateway.admin.api.CredentialRequest;
import com.prodigalgal.xaigateway.admin.api.CredentialResponse;
import com.prodigalgal.xaigateway.gateway.core.catalog.CredentialModelDiscoveryService;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountPoolEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountPoolRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CredentialAdminService {

    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final CredentialModelDiscoveryService credentialModelDiscoveryService;
    private final ProviderSiteRegistryService providerSiteRegistryService;
    private final UpstreamAccountPoolRepository upstreamAccountPoolRepository;
    private final ObjectMapper objectMapper;
    private final SupportedModelCatalogService supportedModelCatalogService;

    public CredentialAdminService(
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialCryptoService credentialCryptoService,
            CredentialModelDiscoveryService credentialModelDiscoveryService,
            ProviderSiteRegistryService providerSiteRegistryService,
            UpstreamAccountPoolRepository upstreamAccountPoolRepository,
            ObjectMapper objectMapper,
            SupportedModelCatalogService supportedModelCatalogService) {
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.credentialModelDiscoveryService = credentialModelDiscoveryService;
        this.providerSiteRegistryService = providerSiteRegistryService;
        this.upstreamAccountPoolRepository = upstreamAccountPoolRepository;
        this.objectMapper = objectMapper;
        this.supportedModelCatalogService = supportedModelCatalogService;
    }

    @Transactional(readOnly = true)
    public List<CredentialResponse> list() {
        List<UpstreamCredentialEntity> credentials = upstreamCredentialRepository.findAllByDeletedFalseOrderByCreatedAtDesc();
        Map<Long, String> poolNameMap = resolvePoolNameMap(credentials);
        return credentials.stream()
                .sorted(Comparator.comparing(UpstreamCredentialEntity::getCreatedAt).reversed())
                .map(entity -> toResponse(entity, poolNameMap.get(entity.getPoolId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CredentialResponse> listByPool(Long poolId) {
        List<UpstreamCredentialEntity> credentials = upstreamCredentialRepository.findAllByPoolIdAndDeletedFalseOrderByCreatedAtDesc(poolId);
        Map<Long, String> poolNameMap = resolvePoolNameMap(credentials);
        return credentials.stream()
                .map(entity -> toResponse(entity, poolNameMap.get(entity.getPoolId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CredentialResponse get(Long id) {
        UpstreamCredentialEntity entity = getRequired(id);
        String poolName = entity.getPoolId() == null
                ? null
                : upstreamAccountPoolRepository.findById(entity.getPoolId()).map(UpstreamAccountPoolEntity::getPoolName).orElse(null);
        return toResponse(entity, poolName);
    }

    public CredentialResponse create(CredentialRequest request) {
        String secret = requireSecret(request.resolvedSecret());
        String fingerprint = credentialCryptoService.fingerprint(secret);
        if (upstreamCredentialRepository.findByApiKeyFingerprintAndDeletedFalse(fingerprint).isPresent()) {
            throw new IllegalArgumentException("已存在相同的上游凭证密钥。");
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
                        request.resolvedAuthKind(),
                        requireSecret(request.resolvedSecret()),
                        request.resolvedCredentialMetadata()
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

    @Transactional(readOnly = true)
    public List<String> listSupportedModelCatalog(ProviderType providerType) {
        return supportedModelCatalogService.listByProvider(providerType);
    }

    private UpstreamCredentialEntity getRequired(Long id) {
        Optional<UpstreamCredentialEntity> entity = upstreamCredentialRepository.findById(id);
        if (entity.isEmpty() || entity.get().isDeleted()) {
            throw new IllegalArgumentException("未找到指定的上游凭证。");
        }
        return entity.get();
    }

    private void apply(UpstreamCredentialEntity entity, CredentialRequest request) {
        String secret = requireSecret(request.resolvedSecret());
        UpstreamAccountPoolEntity pool = resolvePool(request.poolId());
        entity.setCredentialName(request.credentialName().trim());
        entity.setProviderType(request.providerType());
        entity.setBaseUrl(request.baseUrl().trim());
        entity.setAuthKind(request.resolvedAuthKind());
        entity.setApiKeyCiphertext(credentialCryptoService.encrypt(secret));
        entity.setApiKeyFingerprint(credentialCryptoService.fingerprint(secret));
        entity.setCredentialMetadataJson(writeMetadata(request.resolvedCredentialMetadata()));
        entity.setSupportedModels(supportedModelCatalogService.resolveForCredentialImport(
                request.providerType(),
                pool,
                request.resolvedSupportedModels()
        ));
        entity.setActive(request.active() == null || request.active());
        entity.setProxyId(request.proxyId());
        entity.setTlsFingerprintProfileId(request.tlsFingerprintProfileId());
        entity.setSiteProfileId(providerSiteRegistryService.ensureSiteProfile(
                request.providerType(),
                request.baseUrl().trim(),
                request.siteProfileId()
        ).getId());
        entity.setPoolId(pool == null ? null : pool.getId());
    }

    private CredentialResponse toResponse(UpstreamCredentialEntity entity, String poolName) {
        long totalRequests = entity.getTotalRequestCount();
        long successRequests = entity.getSuccessfulRequestCount();
        long totalTokens = entity.getTotalTokenCount();
        long cacheHitTokens = entity.getTotalCacheHitTokenCount();
        long durationSamples = entity.getDurationSampleCount();
        long firstTokenSamples = entity.getFirstTokenSampleCount();
        return new CredentialResponse(
                entity.getId(),
                entity.getCredentialName(),
                entity.getProviderType(),
                entity.getBaseUrl(),
                entity.getAuthKind(),
                supportedModelCatalogService.normalize(entity.getSupportedModels()),
                entity.getApiKeyFingerprint(),
                readMetadata(entity.getCredentialMetadataJson()),
                entity.isActive(),
                entity.getCooldownUntil(),
                entity.getLastErrorCode(),
                entity.getLastErrorMessage(),
                entity.getLastErrorAt(),
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
                entity.getProxyId(),
                entity.getTlsFingerprintProfileId(),
                entity.getSiteProfileId(),
                entity.getPoolId(),
                poolName,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private CredentialResponse toResponse(UpstreamCredentialEntity entity) {
        String poolName = entity.getPoolId() == null
                ? null
                : upstreamAccountPoolRepository.findById(entity.getPoolId())
                        .map(UpstreamAccountPoolEntity::getPoolName)
                        .orElse(null);
        return toResponse(entity, poolName);
    }

    private UpstreamAccountPoolEntity resolvePool(Long requestPoolId) {
        if (requestPoolId == null) {
            return null;
        }
        return upstreamAccountPoolRepository.findById(requestPoolId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定账号池。"));
    }

    private Map<Long, String> resolvePoolNameMap(List<UpstreamCredentialEntity> credentials) {
        Set<Long> poolIds = credentials.stream()
                .map(UpstreamCredentialEntity::getPoolId)
                .filter(id -> id != null && id > 0)
                .collect(java.util.stream.Collectors.toSet());
        if (poolIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        upstreamAccountPoolRepository.findAllById(poolIds)
                .forEach(pool -> result.put(pool.getId(), pool.getPoolName()));
        return result;
    }

    private String requireSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("凭证 secret 不能为空。");
        }
        return secret.trim();
    }

    private String writeMetadata(Map<String, Object> metadata) {
        try {
            return metadata == null || metadata.isEmpty() ? null : objectMapper.writeValueAsString(metadata);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("无法序列化凭证 metadata。", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, Map.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("无法解析凭证 metadata。", exception);
        }
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return ((double) numerator) / denominator;
    }
}
