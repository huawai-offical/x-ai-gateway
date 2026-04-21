package com.prodigalgal.xaigateway.gateway.core.observability;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.SupportStatus;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateEvaluation;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteExecutionAttempt;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GatewayObservabilityService {

    private final RouteDecisionLogRepository routeDecisionLogRepository;
    private final CacheHitLogRepository cacheHitLogRepository;
    private final UpstreamCacheReferenceRepository upstreamCacheReferenceRepository;
    private final ObjectMapper objectMapper;
    private final GatewayObservabilityAsyncPersistenceService asyncPersistenceService;

    @Autowired
    public GatewayObservabilityService(
            RouteDecisionLogRepository routeDecisionLogRepository,
            CacheHitLogRepository cacheHitLogRepository,
            UpstreamCacheReferenceRepository upstreamCacheReferenceRepository,
            ObjectMapper objectMapper,
            GatewayObservabilityAsyncPersistenceService asyncPersistenceService) {
        this.routeDecisionLogRepository = routeDecisionLogRepository;
        this.cacheHitLogRepository = cacheHitLogRepository;
        this.upstreamCacheReferenceRepository = upstreamCacheReferenceRepository;
        this.objectMapper = objectMapper;
        this.asyncPersistenceService = asyncPersistenceService;
    }

    public GatewayObservabilityService(
            RouteDecisionLogRepository routeDecisionLogRepository,
            CacheHitLogRepository cacheHitLogRepository,
            UpstreamCacheReferenceRepository upstreamCacheReferenceRepository,
            ObjectMapper objectMapper) {
        this(routeDecisionLogRepository, cacheHitLogRepository, upstreamCacheReferenceRepository, objectMapper, null);
    }

    public String nextRequestId() {
        return UUID.randomUUID().toString();
    }

    public void recordRouteDecision(String requestId, RouteSelectionResult selectionResult) {
        recordRouteDecision(requestId, selectionResult, null, null, null, null, null, null, null);
    }

    public void recordRouteDecision(
            String requestId,
            RouteSelectionResult selectionResult,
            String requestPath,
            String resourceType,
            String operation,
            ExecutionBackend executionBackend,
            String objectMode) {
        recordRouteDecision(
                requestId,
                selectionResult,
                requestPath,
                resourceType,
                operation,
                executionBackend,
                null,
                objectMode,
                null
        );
    }

    public void recordRouteDecision(
            String requestId,
            RouteSelectionResult selectionResult,
            String requestPath,
            String resourceType,
            String operation,
            ExecutionBackend executionBackend,
            SupportStatus supportStatus,
            String objectMode,
            InteropCapabilityLevel degradationLevel) {
        GatewayObservabilityAsyncPersistenceService.RouteDecisionLogSnapshot snapshot =
                new GatewayObservabilityAsyncPersistenceService.RouteDecisionLogSnapshot(
                        requestId,
                        selectionResult.distributedKeyId(),
                        selectionResult.distributedKeyPrefix(),
                        selectionResult.requestedModel(),
                        selectionResult.publicModel(),
                        selectionResult.resolvedModelKey(),
                        selectionResult.protocol(),
                        requestPath,
                        resourceType,
                        operation,
                        selectionResult.modelGroup(),
                        selectionResult.selectionSource().name(),
                        executionBackend == null ? null : executionBackend.wireName(),
                        supportStatus == null ? null : supportStatus.name(),
                        degradationLevel == null ? null : degradationLevel.name(),
                        objectMode,
                        selectionResult.selectedCandidate().candidate().credentialId(),
                        selectionResult.selectedCandidate().candidate().providerType(),
                        selectionResult.selectedCandidate().candidate().baseUrl(),
                        selectionResult.prefixHash(),
                        selectionResult.fingerprint(),
                        selectionResult.candidates().size(),
                        serializeCandidates(selectionResult)
                );
        enqueueOrPersistRouteDecision(snapshot);
    }

    public void recordCacheUsage(String requestId, RouteSelectionResult selectionResult, GatewayUsage usage, String cacheKind, String cachedContentRef) {
        recordCacheUsage(requestId, selectionResult, usage, cacheKind, cachedContentRef, null, null, null, null, null, null, null);
    }

    public void recordCacheUsage(
            String requestId,
            RouteSelectionResult selectionResult,
            GatewayUsage usage,
            String cacheKind,
            String cachedContentRef,
            String requestPath,
            String resourceType,
            String operation,
            ExecutionBackend executionBackend,
            String objectMode) {
        recordCacheUsage(
                requestId,
                selectionResult,
                usage,
                cacheKind,
                cachedContentRef,
                requestPath,
                resourceType,
                operation,
                executionBackend,
                null,
                objectMode,
                null
        );
    }

    public void recordCacheUsage(
            String requestId,
            RouteSelectionResult selectionResult,
            GatewayUsage usage,
            String cacheKind,
            String cachedContentRef,
            String requestPath,
            String resourceType,
            String operation,
            ExecutionBackend executionBackend,
            SupportStatus supportStatus,
            String objectMode,
            InteropCapabilityLevel degradationLevel) {
        if (usage == null) {
            return;
        }

        boolean structuredResourceLog = requestPath != null || resourceType != null || operation != null || executionBackend != null || objectMode != null;
        if (!structuredResourceLog && usage.cacheHitTokens() <= 0 && usage.cacheWriteTokens() <= 0) {
            return;
        }

        GatewayObservabilityAsyncPersistenceService.CacheHitLogSnapshot snapshot =
                new GatewayObservabilityAsyncPersistenceService.CacheHitLogSnapshot(
                        requestId,
                        selectionResult.distributedKeyId(),
                        selectionResult.protocol(),
                        requestPath,
                        resourceType,
                        operation,
                        selectionResult.selectedCandidate().candidate().providerType(),
                        selectionResult.selectedCandidate().candidate().credentialId(),
                        selectionResult.modelGroup(),
                        selectionResult.prefixHash(),
                        selectionResult.fingerprint(),
                        cacheKind,
                        executionBackend == null ? null : executionBackend.wireName(),
                        supportStatus == null ? null : supportStatus.name(),
                        degradationLevel == null ? null : degradationLevel.name(),
                        objectMode,
                        usage.cacheHitTokens(),
                        usage.cacheWriteTokens(),
                        usage.savedInputTokens(),
                        cachedContentRef
                );
        enqueueOrPersistCacheHit(snapshot);
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

    private void enqueueOrPersistRouteDecision(
            GatewayObservabilityAsyncPersistenceService.RouteDecisionLogSnapshot snapshot) {
        if (asyncPersistenceService != null && asyncPersistenceService.enqueueRouteDecisionLogInsert(snapshot)) {
            return;
        }
        RouteDecisionLogEntity entity = new RouteDecisionLogEntity();
        entity.setRequestId(snapshot.requestId());
        entity.setDistributedKeyId(snapshot.distributedKeyId());
        entity.setDistributedKeyPrefix(snapshot.distributedKeyPrefix());
        entity.setRequestedModel(snapshot.requestedModel());
        entity.setPublicModel(snapshot.publicModel());
        entity.setResolvedModelKey(snapshot.resolvedModelKey());
        entity.setProtocol(snapshot.protocol());
        entity.setRequestPath(snapshot.requestPath());
        entity.setResourceType(snapshot.resourceType());
        entity.setOperation(snapshot.operation());
        entity.setModelGroup(snapshot.modelGroup());
        entity.setSelectionSource(snapshot.selectionSource());
        entity.setExecutionBackend(snapshot.executionBackend());
        entity.setSupportStatus(snapshot.supportStatus());
        entity.setDegradationLevel(snapshot.degradationLevel());
        entity.setObjectMode(snapshot.objectMode());
        entity.setSelectedCredentialId(snapshot.selectedCredentialId());
        entity.setSelectedProviderType(snapshot.selectedProviderType());
        entity.setSelectedBaseUrl(snapshot.selectedBaseUrl());
        entity.setPrefixHash(snapshot.prefixHash());
        entity.setFingerprint(snapshot.fingerprint());
        entity.setCandidateCount(snapshot.candidateCount());
        entity.setCandidateSummaryJson(snapshot.candidateSummaryJson());
        routeDecisionLogRepository.save(entity);
    }

    private void enqueueOrPersistCacheHit(
            GatewayObservabilityAsyncPersistenceService.CacheHitLogSnapshot snapshot) {
        if (asyncPersistenceService != null && asyncPersistenceService.enqueueCacheHitLogInsert(snapshot)) {
            return;
        }
        CacheHitLogEntity entity = new CacheHitLogEntity();
        entity.setRequestId(snapshot.requestId());
        entity.setDistributedKeyId(snapshot.distributedKeyId());
        entity.setProtocol(snapshot.protocol());
        entity.setRequestPath(snapshot.requestPath());
        entity.setResourceType(snapshot.resourceType());
        entity.setOperation(snapshot.operation());
        entity.setProviderType(snapshot.providerType());
        entity.setCredentialId(snapshot.credentialId());
        entity.setModelGroup(snapshot.modelGroup());
        entity.setPrefixHash(snapshot.prefixHash());
        entity.setFingerprint(snapshot.fingerprint());
        entity.setCacheKind(snapshot.cacheKind());
        entity.setExecutionBackend(snapshot.executionBackend());
        entity.setSupportStatus(snapshot.supportStatus());
        entity.setDegradationLevel(snapshot.degradationLevel());
        entity.setObjectMode(snapshot.objectMode());
        entity.setCacheHitTokens(snapshot.cacheHitTokens());
        entity.setCacheWriteTokens(snapshot.cacheWriteTokens());
        entity.setSavedInputTokens(snapshot.savedInputTokens());
        entity.setCachedContentRef(snapshot.cachedContentRef());
        cacheHitLogRepository.save(entity);
    }

    private String serializeCandidates(RouteSelectionResult selectionResult) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("selectionSource", selectionResult.selectionSource().name());
        root.put("candidates", selectionResult.candidateEvaluations().stream().map(this::candidateSummary).toList());
        root.put("attempts", selectionResult.attempts().stream().map(this::attemptSummary).toList());
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法序列化候选摘要。", exception);
        }
    }

    private Map<String, Object> candidateSummary(RouteCandidateEvaluation candidate) {
        Map<String, Object> map = new LinkedHashMap<>();
        RouteCandidateView candidateView = candidate.candidate();
        map.put("credentialId", candidateView.candidate().credentialId());
        map.put("providerType", candidateView.candidate().providerType().name());
        map.put("modelKey", candidateView.candidate().modelKey());
        map.put("bindingPriority", candidateView.bindingPriority());
        map.put("bindingWeight", candidateView.bindingWeight());
        map.put("capabilityLevel", candidateView.capabilityLevel());
        map.put("healthState", candidate.healthState());
        map.put("cooldownUntil", candidate.cooldownUntil());
        map.put("affinityMatched", candidate.affinityMatched());
        map.put("selectionSource", candidate.selectionSource().name());
        map.put("eligible", candidate.eligible());
        map.put("totalScore", candidate.totalScore());
        map.put("scoreBreakdown", candidate.scoreBreakdown());
        map.put("exclusionReasons", candidate.exclusionReasons());
        return map;
    }

    private Map<String, Object> attemptSummary(RouteExecutionAttempt attempt) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("attempt", attempt.attempt());
        map.put("credentialId", attempt.credentialId());
        map.put("providerType", attempt.providerType());
        map.put("outcome", attempt.outcome());
        map.put("detail", attempt.detail());
        return map;
    }
}
