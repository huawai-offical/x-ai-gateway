package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.CacheHitLogResponse;
import com.prodigalgal.xaigateway.admin.api.ObservabilitySummaryResponse;
import com.prodigalgal.xaigateway.admin.api.RouteDecisionLogResponse;
import com.prodigalgal.xaigateway.admin.api.UpstreamCacheReferenceResponse;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.CacheHitLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RouteDecisionLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCacheReferenceEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.CacheHitLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RouteDecisionLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCacheReferenceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ObservabilityQueryService {

    private static final PageRequest DEFAULT_SAMPLE_PAGE = PageRequest.of(0, 100);
    private static final Duration DEFAULT_PARTIAL_WINDOW = Duration.ofHours(24);

    private final RouteDecisionLogRepository routeDecisionLogRepository;
    private final CacheHitLogRepository cacheHitLogRepository;
    private final UpstreamCacheReferenceRepository upstreamCacheReferenceRepository;

    public ObservabilityQueryService(
            RouteDecisionLogRepository routeDecisionLogRepository,
            CacheHitLogRepository cacheHitLogRepository,
            UpstreamCacheReferenceRepository upstreamCacheReferenceRepository) {
        this.routeDecisionLogRepository = routeDecisionLogRepository;
        this.cacheHitLogRepository = cacheHitLogRepository;
        this.upstreamCacheReferenceRepository = upstreamCacheReferenceRepository;
    }

    public List<RouteDecisionLogResponse> listRouteDecisions(Long distributedKeyId) {
        return listRouteDecisions(distributedKeyId, null, null, null);
    }

    public List<RouteDecisionLogResponse> listRouteDecisions(Long distributedKeyId, ProviderType providerType) {
        return listRouteDecisions(distributedKeyId, providerType, null, null);
    }

    public List<RouteDecisionLogResponse> listRouteDecisions(
            Long distributedKeyId,
            ProviderType providerType,
            Instant from,
            Instant to) {
        TimeWindow window = resolveWindow(from, to);
        List<RouteDecisionLogEntity> entities;
        if (window == null) {
            entities = routeDecisionLogRepository.search(distributedKeyId, providerType, DEFAULT_SAMPLE_PAGE);
        } else {
            entities = routeDecisionLogRepository.searchWithinWindow(
                    distributedKeyId,
                    providerType,
                    window.from(),
                    window.to());
        }
        return entities.stream().map(this::toRouteDecisionResponse).toList();
    }

    public List<CacheHitLogResponse> listCacheHits(Long distributedKeyId, ProviderType providerType) {
        List<CacheHitLogEntity> entities = cacheHitLogRepository.search(
                distributedKeyId,
                providerType,
                DEFAULT_SAMPLE_PAGE);
        return entities.stream().map(this::toCacheHitResponse).toList();
    }

    public List<CacheHitLogResponse> listCacheHits(
            Long distributedKeyId,
            ProviderType providerType,
            Instant from,
            Instant to) {
        TimeWindow window = resolveWindow(from, to);
        if (window == null) {
            return listCacheHits(distributedKeyId, providerType);
        }

        List<CacheHitLogEntity> entities = cacheHitLogRepository.searchWithinWindow(
                distributedKeyId,
                providerType,
                window.from(),
                window.to());
        return entities.stream().map(this::toCacheHitResponse).toList();
    }

    public List<UpstreamCacheReferenceResponse> listUpstreamCacheReferences(Long distributedKeyId, String status) {
        return listUpstreamCacheReferences(distributedKeyId, null, status, null, null);
    }

    public List<UpstreamCacheReferenceResponse> listUpstreamCacheReferences(
            Long distributedKeyId,
            ProviderType providerType,
            String status,
            Instant from,
            Instant to) {
        String normalizedStatus = normalizeStatus(status);
        TimeWindow window = resolveWindow(from, to);

        List<UpstreamCacheReferenceEntity> entities;
        if (window == null) {
            entities = upstreamCacheReferenceRepository.search(
                    distributedKeyId,
                    providerType,
                    normalizedStatus,
                    DEFAULT_SAMPLE_PAGE);
        } else {
            entities = upstreamCacheReferenceRepository.searchWithinWindow(
                    distributedKeyId,
                    providerType,
                    normalizedStatus,
                    window.from(),
                    window.to());
        }
        return entities.stream().map(this::toUpstreamCacheReferenceResponse).toList();
    }

    public ObservabilitySummaryResponse summary(Long distributedKeyId, ProviderType providerType) {
        return summary(distributedKeyId, providerType, null, null);
    }

    public ObservabilitySummaryResponse summary(
            Long distributedKeyId,
            ProviderType providerType,
            Instant from,
            Instant to) {
        TimeWindow window = resolveWindow(from, to);
        List<RouteDecisionLogResponse> routeDecisions = listRouteDecisions(distributedKeyId, providerType, from, to);
        List<CacheHitLogResponse> cacheHits = listCacheHits(distributedKeyId, providerType, from, to);
        List<UpstreamCacheReferenceResponse> upstreamReferences = listUpstreamCacheReferences(
                distributedKeyId,
                providerType,
                "ACTIVE",
                from,
                to);

        long totalCacheHitTokens = cacheHits.stream()
                .mapToLong(CacheHitLogResponse::cacheHitTokens)
                .sum();
        long totalCacheWriteTokens = cacheHits.stream()
                .mapToLong(CacheHitLogResponse::cacheWriteTokens)
                .sum();
        long totalSavedInputTokens = cacheHits.stream()
                .mapToLong(CacheHitLogResponse::savedInputTokens)
                .sum();

        return new ObservabilitySummaryResponse(
                window == null ? null : window.from(),
                window == null ? null : window.to(),
                routeDecisions.size(),
                cacheHits.size(),
                upstreamReferences.size(),
                totalCacheHitTokens,
                totalCacheWriteTokens,
                totalSavedInputTokens
        );
    }

    private TimeWindow resolveWindow(Instant from, Instant to) {
        if (from == null && to == null) {
            return null;
        }

        Instant resolvedTo = to == null ? Instant.now() : to;
        Instant resolvedFrom = from == null ? resolvedTo.minus(DEFAULT_PARTIAL_WINDOW) : from;
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new IllegalArgumentException("from 不能晚于 to。");
        }
        return new TimeWindow(resolvedFrom, resolvedTo);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return status.trim().toUpperCase();
    }

    private RouteDecisionLogResponse toRouteDecisionResponse(RouteDecisionLogEntity entity) {
        return new RouteDecisionLogResponse(
                entity.getId(),
                entity.getRequestId(),
                entity.getDistributedKeyId(),
                entity.getDistributedKeyPrefix(),
                entity.getRequestedModel(),
                entity.getPublicModel(),
                entity.getResolvedModelKey(),
                entity.getProtocol(),
                entity.getModelGroup(),
                entity.getSelectionSource(),
                entity.getSelectedCredentialId(),
                entity.getSelectedProviderType(),
                entity.getSelectedBaseUrl(),
                entity.getPrefixHash(),
                entity.getFingerprint(),
                entity.getCandidateCount(),
                entity.getCandidateSummaryJson(),
                entity.getCreatedAt()
        );
    }

    private CacheHitLogResponse toCacheHitResponse(CacheHitLogEntity entity) {
        return new CacheHitLogResponse(
                entity.getId(),
                entity.getRequestId(),
                entity.getDistributedKeyId(),
                entity.getProtocol(),
                entity.getProviderType(),
                entity.getCredentialId(),
                entity.getModelGroup(),
                entity.getPrefixHash(),
                entity.getFingerprint(),
                entity.getCacheKind(),
                entity.getCacheHitTokens(),
                entity.getCacheWriteTokens(),
                entity.getSavedInputTokens(),
                entity.getCachedContentRef(),
                entity.getCreatedAt()
        );
    }

    private UpstreamCacheReferenceResponse toUpstreamCacheReferenceResponse(UpstreamCacheReferenceEntity entity) {
        return new UpstreamCacheReferenceResponse(
                entity.getId(),
                entity.getDistributedKeyId(),
                entity.getProviderType(),
                entity.getCredentialId(),
                entity.getModelGroup(),
                entity.getPrefixHash(),
                entity.getExternalCacheRef(),
                entity.getStatus(),
                entity.getExpireAt(),
                entity.getLastUsedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private record TimeWindow(Instant from, Instant to) {
    }
}
