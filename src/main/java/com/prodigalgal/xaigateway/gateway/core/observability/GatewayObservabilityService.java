package com.prodigalgal.xaigateway.gateway.core.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.CacheHitLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RouteDecisionLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCacheReferenceEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.CacheHitLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RouteDecisionLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCacheReferenceRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GatewayObservabilityService {

    private final RouteDecisionLogRepository routeDecisionLogRepository;
    private final CacheHitLogRepository cacheHitLogRepository;
    private final UpstreamCacheReferenceRepository upstreamCacheReferenceRepository;
    private final ObjectMapper objectMapper;

    public GatewayObservabilityService(
            RouteDecisionLogRepository routeDecisionLogRepository,
            CacheHitLogRepository cacheHitLogRepository,
            UpstreamCacheReferenceRepository upstreamCacheReferenceRepository,
            ObjectMapper objectMapper) {
        this.routeDecisionLogRepository = routeDecisionLogRepository;
        this.cacheHitLogRepository = cacheHitLogRepository;
        this.upstreamCacheReferenceRepository = upstreamCacheReferenceRepository;
        this.objectMapper = objectMapper;
    }

    public String nextRequestId() {
        return UUID.randomUUID().toString();
    }

    public void recordRouteDecision(String requestId, RouteSelectionResult selectionResult) {
        RouteDecisionLogEntity entity = new RouteDecisionLogEntity();
        entity.setRequestId(requestId);
        entity.setDistributedKeyId(selectionResult.distributedKeyId());
        entity.setDistributedKeyPrefix(selectionResult.distributedKeyPrefix());
        entity.setRequestedModel(selectionResult.requestedModel());
        entity.setPublicModel(selectionResult.publicModel());
        entity.setResolvedModelKey(selectionResult.resolvedModelKey());
        entity.setProtocol(selectionResult.protocol());
        entity.setModelGroup(selectionResult.modelGroup());
        entity.setSelectionSource(selectionResult.selectionSource().name());
        entity.setSelectedCredentialId(selectionResult.selectedCandidate().candidate().credentialId());
        entity.setSelectedProviderType(selectionResult.selectedCandidate().candidate().providerType());
        entity.setSelectedBaseUrl(selectionResult.selectedCandidate().candidate().baseUrl());
        entity.setPrefixHash(selectionResult.prefixHash());
        entity.setFingerprint(selectionResult.fingerprint());
        entity.setCandidateCount(selectionResult.candidates().size());
        entity.setCandidateSummaryJson(serializeCandidates(selectionResult));
        routeDecisionLogRepository.save(entity);
    }

    public void recordCacheUsage(String requestId, RouteSelectionResult selectionResult, GatewayUsage usage, String cacheKind, String cachedContentRef) {
        if (usage == null) {
            return;
        }

        if (usage.cacheHitTokens() <= 0 && usage.cacheWriteTokens() <= 0) {
            return;
        }

        CacheHitLogEntity entity = new CacheHitLogEntity();
        entity.setRequestId(requestId);
        entity.setDistributedKeyId(selectionResult.distributedKeyId());
        entity.setProtocol(selectionResult.protocol());
        entity.setProviderType(selectionResult.selectedCandidate().candidate().providerType());
        entity.setCredentialId(selectionResult.selectedCandidate().candidate().credentialId());
        entity.setModelGroup(selectionResult.modelGroup());
        entity.setPrefixHash(selectionResult.prefixHash());
        entity.setFingerprint(selectionResult.fingerprint());
        entity.setCacheKind(cacheKind);
        entity.setCacheHitTokens(usage.cacheHitTokens());
        entity.setCacheWriteTokens(usage.cacheWriteTokens());
        entity.setSavedInputTokens(usage.savedInputTokens());
        entity.setCachedContentRef(cachedContentRef);
        cacheHitLogRepository.save(entity);
    }

    public void recordUpstreamCacheReference(
            Long distributedKeyId,
            ProviderType providerType,
            Long credentialId,
            String modelGroup,
            String prefixHash,
            String cachedContentRef,
            Instant expireAt,
            String status) {
        Optional<UpstreamCacheReferenceEntity> existing = upstreamCacheReferenceRepository
                .findByDistributedKeyIdAndProviderTypeAndModelGroupAndPrefixHash(
                        distributedKeyId,
                        providerType,
                        modelGroup,
                        prefixHash
                );

        UpstreamCacheReferenceEntity entity = existing.orElseGet(UpstreamCacheReferenceEntity::new);
        entity.setDistributedKeyId(distributedKeyId);
        entity.setProviderType(providerType);
        entity.setCredentialId(credentialId);
        entity.setModelGroup(modelGroup);
        entity.setPrefixHash(prefixHash);
        entity.setExternalCacheRef(cachedContentRef);
        entity.setStatus(status);
        entity.setExpireAt(expireAt);
        entity.setLastUsedAt(Instant.now());
        upstreamCacheReferenceRepository.save(entity);
    }

    public void markUpstreamCacheReferenceInvalid(
            Long distributedKeyId,
            ProviderType providerType,
            String modelGroup,
            String prefixHash) {
        upstreamCacheReferenceRepository
                .findByDistributedKeyIdAndProviderTypeAndModelGroupAndPrefixHash(
                        distributedKeyId,
                        providerType,
                        modelGroup,
                        prefixHash
                )
                .ifPresent(entity -> {
                    entity.setStatus("INVALIDATED");
                    entity.setLastUsedAt(Instant.now());
                    upstreamCacheReferenceRepository.save(entity);
                });
    }

    private String serializeCandidates(RouteSelectionResult selectionResult) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("selectionSource", selectionResult.selectionSource().name());
        root.put("candidates", selectionResult.candidates().stream().map(this::candidateSummary).toList());
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化候选摘要。", exception);
        }
    }

    private Map<String, Object> candidateSummary(RouteCandidateView candidate) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("credentialId", candidate.candidate().credentialId());
        map.put("providerType", candidate.candidate().providerType().name());
        map.put("modelKey", candidate.candidate().modelKey());
        map.put("bindingPriority", candidate.bindingPriority());
        map.put("bindingWeight", candidate.bindingWeight());
        return map;
    }
}
