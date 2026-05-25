package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.UpstreamCredentialInventoryResponse;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountGroupRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class UpstreamCredentialInventoryService {

    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final UpstreamAccountRepository upstreamAccountRepository;
    private final UpstreamAccountGroupRepository upstreamAccountGroupRepository;
    private final SupportedModelCatalogService supportedModelCatalogService;
    private final ObjectMapper objectMapper;

    public UpstreamCredentialInventoryService(
            UpstreamCredentialRepository upstreamCredentialRepository,
            UpstreamAccountRepository upstreamAccountRepository,
            UpstreamAccountGroupRepository upstreamAccountGroupRepository,
            SupportedModelCatalogService supportedModelCatalogService,
            ObjectMapper objectMapper) {
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.upstreamAccountRepository = upstreamAccountRepository;
        this.upstreamAccountGroupRepository = upstreamAccountGroupRepository;
        this.supportedModelCatalogService = supportedModelCatalogService;
        this.objectMapper = objectMapper;
    }

    public List<UpstreamCredentialInventoryResponse> list() {
        List<UpstreamCredentialEntity> credentials = upstreamCredentialRepository.findAllByDeletedFalseOrderByCreatedAtDesc();
        List<UpstreamAccountEntity> accounts = upstreamAccountRepository.findAllByOrderByCreatedAtDesc();
        Map<Long, String> groupNames = resolveGroupNames(credentials, accounts);

        return java.util.stream.Stream.concat(
                        credentials.stream().map(entity -> fromCredential(entity, groupNames.get(entity.getGroupId()))),
                        accounts.stream().map(entity -> fromAccount(entity, groupName(entity, groupNames)))
                )
                .sorted(Comparator.comparing(
                        UpstreamCredentialInventoryResponse::createdAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed())
                .toList();
    }

    private UpstreamCredentialInventoryResponse fromCredential(UpstreamCredentialEntity entity, String groupName) {
        long totalRequests = entity.getTotalRequestCount();
        long successRequests = entity.getSuccessfulRequestCount();
        long totalTokens = entity.getTotalTokenCount();
        long cacheHitTokens = entity.getTotalCacheHitTokenCount();
        long durationSamples = entity.getDurationSampleCount();
        long firstTokenSamples = entity.getFirstTokenSampleCount();
        return new UpstreamCredentialInventoryResponse(
                "API_KEY",
                entity.getId(),
                "api-key:" + entity.getId(),
                entity.getCredentialName(),
                entity.getProviderType().name(),
                entity.getAuthKind().name(),
                entity.getBaseUrl(),
                supportedModelCatalogService.normalize(entity.getSupportedModels()),
                entity.getApiKeyFingerprint(),
                null,
                readMetadata(entity.getCredentialMetadataJson()),
                entity.isActive(),
                null,
                null,
                null,
                null,
                entity.getCooldownUntil(),
                entity.getLastErrorCode(),
                entity.getLastErrorMessage(),
                entity.getLastErrorAt(),
                entity.getLastUsedAt(),
                entity.getConnectivityStatus(),
                entity.getLastConnectivityTestAt(),
                entity.getLastConnectivityLatencyMs(),
                entity.getLastConnectivityErrorMessage(),
                entity.getLastConnectivityResponseSummary(),
                entity.getLastConnectivityUpstreamRequestId(),
                entity.getLastConnectivityModel(),
                null,
                null,
                null,
                entity.getProxyId(),
                entity.getTlsFingerprintProfileId(),
                entity.getSiteProfileId(),
                entity.getProtocolEndpointId(),
                entity.getGroupId(),
                groupName,
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

    private UpstreamCredentialInventoryResponse fromAccount(UpstreamAccountEntity entity, String groupName) {
        long totalRequests = entity.getTotalRequestCount();
        long successRequests = entity.getSuccessfulRequestCount();
        long totalTokens = entity.getTotalTokenCount();
        long cacheHitTokens = entity.getTotalCacheHitTokenCount();
        long durationSamples = entity.getDurationSampleCount();
        long firstTokenSamples = entity.getFirstTokenSampleCount();
        Long groupId = entity.getGroup() == null ? null : entity.getGroup().getId();
        return new UpstreamCredentialInventoryResponse(
                "AUTH_JSON_ACCOUNT",
                entity.getId(),
                "account:" + entity.getId(),
                entity.getAccountName(),
                entity.getProviderType().name(),
                "OAUTH_TOKEN",
                null,
                supportedModelCatalogService.normalize(entity.getSupportedModels()),
                null,
                entity.getExternalAccountId(),
                readMetadata(entity.getMetadataJson()),
                entity.isActive(),
                entity.isFrozen(),
                entity.isHealthy(),
                entity.getRefreshStatus(),
                entity.getRefreshFailureCount(),
                entity.getCooldownUntil(),
                null,
                entity.getLastErrorMessage(),
                null,
                entity.getLastUsedAt(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                entity.getLastRefreshAt(),
                entity.getTokenExpiresAt(),
                entity.getNextRefreshAfter(),
                entity.getProxyId(),
                entity.getTlsFingerprintProfileId(),
                entity.getSiteProfileId(),
                null,
                groupId,
                groupName,
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

    private Map<Long, String> resolveGroupNames(List<UpstreamCredentialEntity> credentials, List<UpstreamAccountEntity> accounts) {
        Set<Long> groupIds = java.util.stream.Stream.concat(
                        credentials.stream().map(UpstreamCredentialEntity::getGroupId),
                        accounts.stream().map(account -> account.getGroup() == null ? null : account.getGroup().getId())
                )
                .filter(id -> id != null && id > 0)
                .collect(java.util.stream.Collectors.toSet());
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        upstreamAccountGroupRepository.findAllById(groupIds)
                .forEach(group -> result.put(group.getId(), group.getGroupName()));
        return result;
    }

    private String groupName(UpstreamAccountEntity entity, Map<Long, String> groupNames) {
        UpstreamAccountGroupEntity group = entity.getGroup();
        if (group == null) {
            return null;
        }
        return groupNames.getOrDefault(group.getId(), group.getGroupName());
    }

    private Map<String, Object> readMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = objectMapper.readValue(metadataJson, Map.class);
            return metadata;
        } catch (Exception ignored) {
            return Map.of("parseError", true);
        }
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return ((double) numerator) / denominator;
    }
}
