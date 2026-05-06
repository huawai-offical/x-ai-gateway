package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AsyncResourceDetailResponse;
import com.prodigalgal.xaigateway.admin.api.AsyncResourceSummaryResponse;
import com.prodigalgal.xaigateway.admin.api.CacheHitLogResponse;
import com.prodigalgal.xaigateway.admin.api.ObservabilitySummaryResponse;
import com.prodigalgal.xaigateway.admin.api.ObservabilityTraceResponse;
import com.prodigalgal.xaigateway.admin.api.RequestLogResponse;
import com.prodigalgal.xaigateway.admin.api.RouteDecisionLogResponse;
import com.prodigalgal.xaigateway.admin.api.UpstreamCacheReferenceResponse;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.CacheHitLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RequestLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RouteDecisionLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCacheReferenceEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UsageRecordEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.CacheHitLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RequestLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RouteDecisionLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCacheReferenceRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UsageRecordRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.JsonNodeFactory;

@Service
@Transactional(readOnly = true)
public class ObservabilityQueryService {

    private static final PageRequest DEFAULT_SAMPLE_PAGE = PageRequest.of(0, 100);
    private static final Duration DEFAULT_PARTIAL_WINDOW = Duration.ofHours(24);

    private final RouteDecisionLogRepository routeDecisionLogRepository;
    private final CacheHitLogRepository cacheHitLogRepository;
    private final RequestLogRepository requestLogRepository;
    private final UpstreamCacheReferenceRepository upstreamCacheReferenceRepository;
    private final UsageRecordRepository usageRecordRepository;
    private final AsyncResourceAdminService asyncResourceAdminService;

    @Autowired
    public ObservabilityQueryService(
            RouteDecisionLogRepository routeDecisionLogRepository,
            CacheHitLogRepository cacheHitLogRepository,
            RequestLogRepository requestLogRepository,
            UpstreamCacheReferenceRepository upstreamCacheReferenceRepository,
            UsageRecordRepository usageRecordRepository,
            AsyncResourceAdminService asyncResourceAdminService) {
        this.routeDecisionLogRepository = routeDecisionLogRepository;
        this.cacheHitLogRepository = cacheHitLogRepository;
        this.requestLogRepository = requestLogRepository;
        this.upstreamCacheReferenceRepository = upstreamCacheReferenceRepository;
        this.usageRecordRepository = usageRecordRepository;
        this.asyncResourceAdminService = asyncResourceAdminService;
    }

    public ObservabilityQueryService(
            RouteDecisionLogRepository routeDecisionLogRepository,
            CacheHitLogRepository cacheHitLogRepository,
            RequestLogRepository requestLogRepository,
            UpstreamCacheReferenceRepository upstreamCacheReferenceRepository,
            UsageRecordRepository usageRecordRepository) {
        this(
                routeDecisionLogRepository,
                cacheHitLogRepository,
                requestLogRepository,
                upstreamCacheReferenceRepository,
                usageRecordRepository,
                null
        );
    }

    public List<RouteDecisionLogResponse> listRouteDecisions(Long distributedKeyId) {
        return listRouteDecisions(distributedKeyId, null, null, null, null, null, null);
    }

    public List<RouteDecisionLogResponse> listRouteDecisions(Long distributedKeyId, ProviderType providerType) {
        return listRouteDecisions(distributedKeyId, providerType, null, null, null, null, null);
    }

    public List<RouteDecisionLogResponse> listRouteDecisions(
            Long distributedKeyId,
            ProviderType providerType,
            Instant from,
            Instant to) {
        return listRouteDecisions(distributedKeyId, providerType, from, to, null, null, null);
    }

    public List<RouteDecisionLogResponse> listRouteDecisions(
            Long distributedKeyId,
            ProviderType providerType,
            Instant from,
            Instant to,
            String requestId,
            String gatewayResourceKey,
            String upstreamObjectId) {
        TimeWindow window = resolveWindow(from, to);
        Set<String> requestIds = resolveRequestIds(
                requestId,
                gatewayResourceKey,
                upstreamObjectId,
                distributedKeyId,
                providerType,
                window
        );
        List<RouteDecisionLogEntity> entities;
        if (requestIds != null) {
            entities = routeDecisionsForRequestIds(requestIds, distributedKeyId, providerType, window);
        } else if (window == null) {
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
        return listCacheHits(distributedKeyId, providerType, null, null, null, null, null);
    }

    public List<CacheHitLogResponse> listCacheHits(
            Long distributedKeyId,
            ProviderType providerType,
            Instant from,
            Instant to) {
        return listCacheHits(distributedKeyId, providerType, from, to, null, null, null);
    }

    public List<CacheHitLogResponse> listCacheHits(
            Long distributedKeyId,
            ProviderType providerType,
            Instant from,
            Instant to,
            String requestId,
            String gatewayResourceKey,
            String upstreamObjectId) {
        TimeWindow window = resolveWindow(from, to);
        Set<String> requestIds = resolveRequestIds(
                requestId,
                gatewayResourceKey,
                upstreamObjectId,
                distributedKeyId,
                providerType,
                window
        );
        List<CacheHitLogEntity> entities;
        if (requestIds != null) {
            entities = cacheHitsForRequestIds(requestIds, distributedKeyId, providerType, window);
        } else if (window == null) {
            entities = cacheHitLogRepository.search(
                    distributedKeyId,
                    providerType,
                    DEFAULT_SAMPLE_PAGE);
        } else {
            entities = cacheHitLogRepository.searchWithinWindow(
                    distributedKeyId,
                    providerType,
                    window.from(),
                    window.to());
        }
        return entities.stream().map(this::toCacheHitResponse).toList();
    }

    public List<RequestLogResponse> listRequestLogs(
            Long distributedKeyId,
            ProviderType providerType,
            Instant from,
            Instant to) {
        return listRequestLogs(distributedKeyId, providerType, from, to, null, null, null);
    }

    public List<RequestLogResponse> listRequestLogs(
            Long distributedKeyId,
            ProviderType providerType,
            Instant from,
            Instant to,
            String requestId,
            String gatewayResourceKey,
            String upstreamObjectId) {
        TimeWindow window = resolveWindow(from, to);
        Set<String> requestIds = resolveRequestIds(
                requestId,
                gatewayResourceKey,
                upstreamObjectId,
                distributedKeyId,
                providerType,
                window
        );
        List<RequestLogEntity> entities;
        if (requestIds != null) {
            entities = requestLogsForRequestIds(requestIds, distributedKeyId, providerType, window);
        } else if (window == null) {
            entities = requestLogRepository.search(distributedKeyId, providerType, DEFAULT_SAMPLE_PAGE);
        } else {
            entities = requestLogRepository.searchWithinWindow(
                    distributedKeyId,
                    providerType,
                    window.from(),
                    window.to());
        }
        return entities.stream().map(this::toRequestLogResponse).toList();
    }

    public List<UpstreamCacheReferenceResponse> listUpstreamCacheReferences(Long distributedKeyId, String status) {
        return listUpstreamCacheReferences(distributedKeyId, null, status, null, null, null, null, null);
    }

    public List<UpstreamCacheReferenceResponse> listUpstreamCacheReferences(
            Long distributedKeyId,
            ProviderType providerType,
            String status,
            Instant from,
            Instant to) {
        return listUpstreamCacheReferences(distributedKeyId, providerType, status, from, to, null, null, null);
    }

    public List<UpstreamCacheReferenceResponse> listUpstreamCacheReferences(
            Long distributedKeyId,
            ProviderType providerType,
            String status,
            Instant from,
            Instant to,
            String requestId,
            String gatewayResourceKey,
            String upstreamObjectId) {
        String normalizedStatus = normalizeStatus(status);
        TimeWindow window = resolveWindow(from, to);
        Set<String> requestIds = resolveRequestIds(
                requestId,
                gatewayResourceKey,
                upstreamObjectId,
                distributedKeyId,
                providerType,
                window
        );

        List<UpstreamCacheReferenceEntity> entities;
        if (requestIds != null) {
            entities = upstreamReferencesForRequestIds(requestIds, normalizedStatus, distributedKeyId, providerType, window);
        } else if (window == null) {
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

    public ObservabilityTraceResponse trace(String requestId) {
        RequestLogEntity requestLog = requestLogRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalArgumentException("未找到对应的 requestId。"));

        RouteDecisionLogResponse routeDecision = routeDecisionLogRepository.findTopByRequestIdOrderByCreatedAtDesc(requestId)
                .map(this::toRouteDecisionResponse)
                .orElse(null);
        List<CacheHitLogResponse> cacheHits = cacheHitLogRepository.findAllByRequestIdOrderByCreatedAtDesc(requestId).stream()
                .map(this::toCacheHitResponse)
                .toList();
        List<UpstreamCacheReferenceResponse> upstreamCacheReferences = upstreamReferencesForRequestIds(
                Set.of(requestId),
                null,
                null,
                null,
                null
        ).stream().map(this::toUpstreamCacheReferenceResponse).toList();

        AsyncResourceSummaryResponse asyncResourceSummary = asyncResourceAdminService == null || requestLog.getGatewayResourceKey() == null
                ? null
                : asyncResourceAdminService.findAsyncResourceSummary(requestLog.getGatewayResourceKey()).orElse(null);
        AsyncResourceDetailResponse asyncResourceDetail = asyncResourceAdminService == null || requestLog.getGatewayResourceKey() == null
                ? null
                : asyncResourceAdminService.findAsyncResourceDetail(requestLog.getGatewayResourceKey()).orElse(null);

        return new ObservabilityTraceResponse(
                toRequestLogResponse(requestLog),
                routeDecision,
                cacheHits,
                upstreamCacheReferences,
                asyncResourceSummary,
                asyncResourceDetail
        );
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
        List<RouteDecisionLogResponse> routeDecisions = listRouteDecisions(distributedKeyId, providerType, from, to, null, null, null);
        List<CacheHitLogResponse> cacheHits = listCacheHits(distributedKeyId, providerType, from, to, null, null, null);
        List<UpstreamCacheReferenceResponse> upstreamReferences = listUpstreamCacheReferences(
                distributedKeyId,
                providerType,
                "ACTIVE",
                from,
                to,
                null,
                null,
                null
        );
        List<UsageRecordEntity> usageRecords = window == null
                ? usageRecordRepository.search(distributedKeyId, providerType, DEFAULT_SAMPLE_PAGE)
                : usageRecordRepository.searchWithinWindow(distributedKeyId, providerType, window.from(), window.to());

        long totalCacheHitTokens = cacheHits.stream()
                .mapToLong(CacheHitLogResponse::cacheHitTokens)
                .sum();
        long totalCacheWriteTokens = cacheHits.stream()
                .mapToLong(CacheHitLogResponse::cacheWriteTokens)
                .sum();
        long totalSavedInputTokens = cacheHits.stream()
                .mapToLong(CacheHitLogResponse::savedInputTokens)
                .sum();
        int finalUsageCount = (int) usageRecords.stream()
                .filter(entity -> entity.getCompleteness() == com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageCompleteness.FINAL)
                .count();
        int partialUsageCount = (int) usageRecords.stream()
                .filter(entity -> entity.getCompleteness() == com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageCompleteness.PARTIAL)
                .count();

        return new ObservabilitySummaryResponse(
                window == null ? null : window.from(),
                window == null ? null : window.to(),
                routeDecisions.size(),
                cacheHits.size(),
                upstreamReferences.size(),
                usageRecords.size(),
                finalUsageCount,
                partialUsageCount,
                totalCacheHitTokens,
                totalCacheWriteTokens,
                totalSavedInputTokens
        );
    }

    private Set<String> resolveRequestIds(
            String requestId,
            String gatewayResourceKey,
            String upstreamObjectId,
            Long distributedKeyId,
            ProviderType providerType,
            TimeWindow window) {
        String normalizedRequestId = normalizeFilter(requestId);
        if (normalizedRequestId != null) {
            return Set.of(normalizedRequestId);
        }

        LinkedHashSet<String> resourceKeys = resolveGatewayResourceKeys(gatewayResourceKey, upstreamObjectId);
        if (resourceKeys == null) {
            return null;
        }
        if (resourceKeys.isEmpty()) {
            return Set.of();
        }

        LinkedHashSet<String> requestIds = new LinkedHashSet<>();
        for (String resourceKey : resourceKeys) {
            for (RequestLogEntity entity : requestLogRepository.findTop100ByGatewayResourceKeyOrderByCreatedAtDesc(resourceKey)) {
                if (matchesRequestLogFilters(entity, distributedKeyId, providerType, window)) {
                    requestIds.add(entity.getRequestId());
                }
            }
        }
        return requestIds;
    }

    private LinkedHashSet<String> resolveGatewayResourceKeys(String gatewayResourceKey, String upstreamObjectId) {
        String normalizedGatewayResourceKey = normalizeFilter(gatewayResourceKey);
        String normalizedUpstreamObjectId = normalizeFilter(upstreamObjectId);
        if (normalizedGatewayResourceKey == null && normalizedUpstreamObjectId == null) {
            return null;
        }

        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (normalizedGatewayResourceKey != null) {
            keys.add(normalizedGatewayResourceKey);
        }
        if (normalizedUpstreamObjectId != null && asyncResourceAdminService != null) {
            asyncResourceAdminService.findAsyncResourcesByUpstreamObjectId(normalizedUpstreamObjectId).stream()
                    .map(AsyncResourceSummaryResponse::resourceKey)
                    .filter(Objects::nonNull)
                    .forEach(keys::add);
        }
        return keys;
    }

    private List<RequestLogEntity> requestLogsForRequestIds(
            Set<String> requestIds,
            Long distributedKeyId,
            ProviderType providerType,
            TimeWindow window) {
        return requestIds.stream()
                .map(requestLogRepository::findByRequestId)
                .flatMap(Optional::stream)
                .filter(entity -> matchesRequestLogFilters(entity, distributedKeyId, providerType, window))
                .sorted(Comparator.comparing(RequestLogEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private List<RouteDecisionLogEntity> routeDecisionsForRequestIds(
            Set<String> requestIds,
            Long distributedKeyId,
            ProviderType providerType,
            TimeWindow window) {
        return requestIds.stream()
                .map(routeDecisionLogRepository::findTopByRequestIdOrderByCreatedAtDesc)
                .flatMap(Optional::stream)
                .filter(entity -> matchesRouteDecisionFilters(entity, distributedKeyId, providerType, window))
                .sorted(Comparator.comparing(RouteDecisionLogEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private List<CacheHitLogEntity> cacheHitsForRequestIds(
            Set<String> requestIds,
            Long distributedKeyId,
            ProviderType providerType,
            TimeWindow window) {
        return requestIds.stream()
                .flatMap(id -> cacheHitLogRepository.findAllByRequestIdOrderByCreatedAtDesc(id).stream())
                .filter(entity -> matchesCacheHitFilters(entity, distributedKeyId, providerType, window))
                .sorted(Comparator.comparing(CacheHitLogEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private List<UpstreamCacheReferenceEntity> upstreamReferencesForRequestIds(
            Set<String> requestIds,
            String normalizedStatus,
            Long distributedKeyId,
            ProviderType providerType,
            TimeWindow window) {
        LinkedHashSet<UpstreamCacheReferenceEntity> references = new LinkedHashSet<>();
        List<RouteDecisionLogEntity> routeDecisions = routeDecisionsForRequestIds(requestIds, distributedKeyId, providerType, window);
        List<CacheHitLogEntity> cacheHits = cacheHitsForRequestIds(requestIds, distributedKeyId, providerType, window);

        routeDecisions.forEach(entity -> maybeAddUpstreamReference(
                references,
                entity.getDistributedKeyId(),
                entity.getSelectedProviderType(),
                entity.getModelGroup(),
                entity.getPrefixHash(),
                normalizedStatus,
                distributedKeyId,
                providerType,
                window
        ));
        cacheHits.forEach(entity -> maybeAddUpstreamReference(
                references,
                entity.getDistributedKeyId(),
                entity.getProviderType(),
                entity.getModelGroup(),
                entity.getPrefixHash(),
                normalizedStatus,
                distributedKeyId,
                providerType,
                window
        ));

        return references.stream()
                .sorted(Comparator.comparing(UpstreamCacheReferenceEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private void maybeAddUpstreamReference(
            LinkedHashSet<UpstreamCacheReferenceEntity> references,
            Long lookupDistributedKeyId,
            ProviderType lookupProviderType,
            String modelGroup,
            String prefixHash,
            String normalizedStatus,
            Long distributedKeyFilter,
            ProviderType providerFilter,
            TimeWindow window) {
        if (lookupDistributedKeyId == null || lookupProviderType == null || modelGroup == null || prefixHash == null) {
            return;
        }
        upstreamCacheReferenceRepository.findByDistributedKeyIdAndProviderTypeAndModelGroupAndPrefixHash(
                        lookupDistributedKeyId,
                        lookupProviderType,
                        modelGroup,
                        prefixHash
                )
                .filter(entity -> matchesUpstreamReferenceFilters(entity, distributedKeyFilter, providerFilter, normalizedStatus, window))
                .ifPresent(references::add);
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

    private String normalizeFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean matchesRouteDecisionFilters(
            RouteDecisionLogEntity entity,
            Long distributedKeyId,
            ProviderType providerType,
            TimeWindow window) {
        return (distributedKeyId == null || Objects.equals(entity.getDistributedKeyId(), distributedKeyId))
                && (providerType == null || providerType == entity.getSelectedProviderType())
                && withinWindow(entity.getCreatedAt(), window);
    }

    private boolean matchesCacheHitFilters(
            CacheHitLogEntity entity,
            Long distributedKeyId,
            ProviderType providerType,
            TimeWindow window) {
        return (distributedKeyId == null || Objects.equals(entity.getDistributedKeyId(), distributedKeyId))
                && (providerType == null || providerType == entity.getProviderType())
                && withinWindow(entity.getCreatedAt(), window);
    }

    private boolean matchesRequestLogFilters(
            RequestLogEntity entity,
            Long distributedKeyId,
            ProviderType providerType,
            TimeWindow window) {
        return (distributedKeyId == null || Objects.equals(entity.getDistributedKeyId(), distributedKeyId))
                && (providerType == null || providerType == entity.getProviderType())
                && withinWindow(entity.getCreatedAt(), window);
    }

    private boolean matchesUpstreamReferenceFilters(
            UpstreamCacheReferenceEntity entity,
            Long distributedKeyId,
            ProviderType providerType,
            String normalizedStatus,
            TimeWindow window) {
        return (distributedKeyId == null || Objects.equals(entity.getDistributedKeyId(), distributedKeyId))
                && (providerType == null || providerType == entity.getProviderType())
                && (normalizedStatus == null || normalizedStatus.equals(entity.getStatus()))
                && withinWindow(entity.getUpdatedAt(), window);
    }

    private boolean withinWindow(Instant value, TimeWindow window) {
        if (window == null) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return !value.isBefore(window.from()) && !value.isAfter(window.to());
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
                entity.getRequestPath(),
                entity.getResourceType(),
                entity.getOperation(),
                entity.getModelGroup(),
                entity.getSelectionSource(),
                entity.getExecutionBackend(),
                entity.getSupportStatus(),
                entity.getDegradationLevel(),
                entity.getObjectMode(),
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
                entity.getRequestPath(),
                entity.getResourceType(),
                entity.getOperation(),
                entity.getProviderType(),
                entity.getCredentialId(),
                entity.getModelGroup(),
                entity.getPrefixHash(),
                entity.getFingerprint(),
                entity.getCacheKind(),
                entity.getExecutionBackend(),
                entity.getSupportStatus(),
                entity.getDegradationLevel(),
                entity.getObjectMode(),
                entity.getCacheHitTokens(),
                entity.getCacheWriteTokens(),
                entity.getSavedInputTokens(),
                entity.getCachedContentRef(),
                entity.getCreatedAt()
        );
    }

    private RequestLogResponse toRequestLogResponse(RequestLogEntity entity) {
        return new RequestLogResponse(
                entity.getId(),
                entity.getRequestId(),
                entity.getDistributedKeyId(),
                entity.getDistributedKeyPrefix(),
                entity.getProtocol(),
                entity.getRequestPath(),
                entity.getResourceType(),
                entity.getOperation(),
                entity.getRequestedModel(),
                entity.getPublicModel(),
                entity.getResolvedModelKey(),
                entity.getModelGroup(),
                entity.getProviderType(),
                entity.getCredentialId(),
                entity.getSelectionSource(),
                entity.getExecutionBackend(),
                entity.getSupportStatus(),
                entity.getDegradationLevel(),
                entity.getObjectMode(),
                entity.getGatewayResourceKey(),
                entity.getResponseKind(),
                entity.getResponseObjectType(),
                entity.getResponseObjectId(),
                entity.getResponseStatus(),
                entity.getCanonicalEventCount(),
                entity.getStatus(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getCreatedAt(),
                entity.getDurationMs(),
                entity.getErrorCode(),
                entity.getErrorMessage()
        );
    }

    private UpstreamCacheReferenceResponse toUpstreamCacheReferenceResponse(UpstreamCacheReferenceEntity entity) {
        boolean expired = entity.getExpireAt() != null && entity.getExpireAt().isBefore(Instant.now());
        String effectiveStatus = expired ? "EXPIRED" : entity.getStatus();
        boolean active = "ACTIVE".equalsIgnoreCase(entity.getStatus()) && !expired;
        ObjectNode lifecycle = JsonNodeFactory.instance.objectNode();
        lifecycle.put("status", entity.getStatus());
        lifecycle.put("effective_status", effectiveStatus);
        lifecycle.put("expired", expired);
        lifecycle.put("active", active);
        putInstant(lifecycle, "expire_at", entity.getExpireAt());
        putInstant(lifecycle, "last_used_at", entity.getLastUsedAt());
        return new UpstreamCacheReferenceResponse(
                entity.getId(),
                entity.getDistributedKeyId(),
                entity.getProviderType(),
                entity.getCredentialId(),
                entity.getModelGroup(),
                entity.getPrefixHash(),
                entity.getExternalCacheRef(),
                entity.getStatus(),
                effectiveStatus,
                expired,
                active,
                entity.getExpireAt(),
                entity.getLastUsedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                lifecycle
        );
    }

    private void putInstant(ObjectNode node, String fieldName, Instant value) {
        if (value == null) {
            node.putNull(fieldName);
        } else {
            node.put(fieldName, value.toString());
        }
    }

    private record TimeWindow(Instant from, Instant to) {
    }
}
