package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AsyncResourceDetailResponse;
import com.prodigalgal.xaigateway.admin.api.AsyncResourceSummaryResponse;
import com.prodigalgal.xaigateway.admin.api.CacheHitLogResponse;
import com.prodigalgal.xaigateway.admin.api.CodexObservabilityRequestResponse;
import com.prodigalgal.xaigateway.admin.api.CredentialHealthMetricResponse;
import com.prodigalgal.xaigateway.admin.api.HealthMetricResponse;
import com.prodigalgal.xaigateway.admin.api.ObservabilityHealthResponse;
import com.prodigalgal.xaigateway.admin.api.ObservabilitySummaryResponse;
import com.prodigalgal.xaigateway.admin.api.ObservabilityTraceResponse;
import com.prodigalgal.xaigateway.admin.api.ProviderHealthMetricResponse;
import com.prodigalgal.xaigateway.admin.api.RequestLogResponse;
import com.prodigalgal.xaigateway.admin.api.RequestTraceDetailResponse;
import com.prodigalgal.xaigateway.admin.api.RouteDecisionLogResponse;
import com.prodigalgal.xaigateway.admin.api.UpstreamCacheReferenceResponse;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestStatus;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.CacheHitLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RequestLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RequestTraceDetailEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RouteDecisionLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCacheReferenceEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UsageRecordEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.CacheHitLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RequestLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RequestTraceDetailRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RouteDecisionLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCacheReferenceRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UsageRecordRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final RequestTraceDetailRepository requestTraceDetailRepository;
    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final UpstreamCacheReferenceRepository upstreamCacheReferenceRepository;
    private final UsageRecordRepository usageRecordRepository;
    private final AsyncResourceAdminService asyncResourceAdminService;

    @Autowired
    public ObservabilityQueryService(
            RouteDecisionLogRepository routeDecisionLogRepository,
            CacheHitLogRepository cacheHitLogRepository,
            RequestLogRepository requestLogRepository,
            RequestTraceDetailRepository requestTraceDetailRepository,
            UpstreamCredentialRepository upstreamCredentialRepository,
            UpstreamCacheReferenceRepository upstreamCacheReferenceRepository,
            UsageRecordRepository usageRecordRepository,
            AsyncResourceAdminService asyncResourceAdminService) {
        this.routeDecisionLogRepository = routeDecisionLogRepository;
        this.cacheHitLogRepository = cacheHitLogRepository;
        this.requestLogRepository = requestLogRepository;
        this.requestTraceDetailRepository = requestTraceDetailRepository;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
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
                null,
                null,
                upstreamCacheReferenceRepository,
                usageRecordRepository,
                null
        );
    }

    public ObservabilityQueryService(
            RouteDecisionLogRepository routeDecisionLogRepository,
            CacheHitLogRepository cacheHitLogRepository,
            RequestLogRepository requestLogRepository,
            UpstreamCacheReferenceRepository upstreamCacheReferenceRepository,
            UsageRecordRepository usageRecordRepository,
            AsyncResourceAdminService asyncResourceAdminService) {
        this(
                routeDecisionLogRepository,
                cacheHitLogRepository,
                requestLogRepository,
                null,
                null,
                upstreamCacheReferenceRepository,
                usageRecordRepository,
                asyncResourceAdminService
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

    public List<CodexObservabilityRequestResponse> listCodexRequests(
            Long distributedKeyId,
            ProviderType providerType,
            String requestId,
            String clientInstance,
            String sessionAffinityKey,
            String model,
            String status,
            Instant from,
            Instant to) {
        List<RequestLogResponse> requestLogs = listRequestLogs(
                distributedKeyId,
                providerType,
                from,
                to,
                requestId,
                null,
                null
        );
        List<String> requestIds = requestLogs.stream()
                .map(RequestLogResponse::requestId)
                .filter(Objects::nonNull)
                .toList();
        Map<String, UsageRecordEntity> usageByRequestId = usageRecordRepository.findAllByRequestIdIn(requestIds).stream()
                .collect(LinkedHashMap::new, (target, entity) -> target.put(entity.getRequestId(), entity), Map::putAll);

        return requestLogs.stream()
                .map(row -> toCodexObservabilityResponse(
                        row,
                        routeDecisionLogRepository.findTopByRequestIdOrderByCreatedAtDesc(row.requestId())
                                .map(this::toRouteDecisionResponse)
                                .orElse(null),
                        cacheHitLogRepository.findAllByRequestIdOrderByCreatedAtDesc(row.requestId()).stream()
                                .map(this::toCacheHitResponse)
                                .toList(),
                        usageByRequestId.get(row.requestId())
                ))
                .filter(response -> isCodexRequest(response))
                .filter(response -> matchesText(response.clientInstance(), clientInstance)
                        || matchesText(response.workspaceHint(), clientInstance)
                        || matchesText(response.clientFamily(), clientInstance))
                .filter(response -> matchesText(response.sessionAffinityKey(), sessionAffinityKey)
                        || matchesText(response.sessionAffinitySource(), sessionAffinityKey))
                .filter(response -> matchesText(response.model(), model))
                .filter(response -> matchesText(response.status(), status))
                .toList();
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
                requestTraceDetails(requestId),
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

    public ObservabilityHealthResponse health(
            ProviderType providerType,
            Long credentialId,
            Instant from,
            Instant to) {
        TimeWindow window = resolveHealthWindow(from, to);
        List<RequestLogEntity> requestLogs = requestLogRepository.searchHealthWithinWindow(
                providerType,
                credentialId,
                window.from(),
                window.to()
        );

        HealthAccumulator total = new HealthAccumulator();
        Map<ProviderType, HealthAccumulator> providerAccumulators = new LinkedHashMap<>();
        Map<CredentialHealthKey, HealthAccumulator> credentialAccumulators = new LinkedHashMap<>();

        for (RequestLogEntity requestLog : requestLogs) {
            total.accept(requestLog);
            providerAccumulators
                    .computeIfAbsent(requestLog.getProviderType(), ignored -> new HealthAccumulator())
                    .accept(requestLog);
            CredentialHealthKey key = new CredentialHealthKey(requestLog.getCredentialId(), requestLog.getProviderType());
            credentialAccumulators
                    .computeIfAbsent(key, ignored -> new HealthAccumulator())
                    .accept(requestLog);
        }

        Map<Long, UpstreamCredentialEntity> credentialsById = resolveCredentialsById(credentialAccumulators.keySet());

        List<CredentialHealthMetricResponse> credentialMetrics = credentialAccumulators.entrySet().stream()
                .map(entry -> toCredentialHealthMetric(entry.getKey(), entry.getValue(), credentialsById.get(entry.getKey().credentialId())))
                .sorted(Comparator
                        .comparingLong(CredentialHealthMetricResponse::failedRequests).reversed()
                        .thenComparing(CredentialHealthMetricResponse::successRate)
                        .thenComparing(item -> item.credentialId() == null ? Long.MAX_VALUE : item.credentialId()))
                .toList();
        List<ProviderHealthMetricResponse> providerMetrics = providerAccumulators.entrySet().stream()
                .map(entry -> toProviderHealthMetric(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparingLong(ProviderHealthMetricResponse::failedRequests).reversed()
                        .thenComparing(ProviderHealthMetricResponse::successRate)
                        .thenComparing(item -> item.providerType() == null ? "" : item.providerType().name()))
                .toList();

        return new ObservabilityHealthResponse(
                window.from(),
                window.to(),
                total.toHealthMetric(),
                credentialMetrics,
                providerMetrics
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

    private TimeWindow resolveHealthWindow(Instant from, Instant to) {
        Instant resolvedTo = to == null ? Instant.now() : to;
        Instant resolvedFrom = from == null ? resolvedTo.minus(DEFAULT_PARTIAL_WINDOW) : from;
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new IllegalArgumentException("from 不能晚于 to。");
        }
        return new TimeWindow(resolvedFrom, resolvedTo);
    }

    private Map<Long, UpstreamCredentialEntity> resolveCredentialsById(Set<CredentialHealthKey> keys) {
        if (upstreamCredentialRepository == null || keys == null || keys.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = keys.stream()
                .map(CredentialHealthKey::credentialId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return upstreamCredentialRepository.findAllByIdInAndDeletedFalse(ids).stream()
                .collect(Collectors.toMap(
                        UpstreamCredentialEntity::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private CredentialHealthMetricResponse toCredentialHealthMetric(
            CredentialHealthKey key,
            HealthAccumulator accumulator,
            UpstreamCredentialEntity credential) {
        HealthMetricResponse metric = accumulator.toHealthMetric();
        ProviderType providerType = credential == null ? key.providerType() : credential.getProviderType();
        return new CredentialHealthMetricResponse(
                key.credentialId(),
                providerType,
                credential == null ? fallbackCredentialLabel(key) : credential.getCredentialName(),
                credential == null ? null : shortFingerprint(credential.getApiKeyFingerprint()),
                metric.totalRequests(),
                metric.successfulRequests(),
                metric.failedRequests(),
                metric.canceledRequests(),
                metric.successRate(),
                metric.availabilityRate(),
                metric.errorRate(),
                metric.cancellationRate(),
                metric.avgDurationMs(),
                metric.lastSuccessfulAt(),
                metric.lastFailedAt()
        );
    }

    private ProviderHealthMetricResponse toProviderHealthMetric(ProviderType providerType, HealthAccumulator accumulator) {
        HealthMetricResponse metric = accumulator.toHealthMetric();
        return new ProviderHealthMetricResponse(
                providerType,
                metric.totalRequests(),
                metric.successfulRequests(),
                metric.failedRequests(),
                metric.canceledRequests(),
                metric.successRate(),
                metric.availabilityRate(),
                metric.errorRate(),
                metric.cancellationRate(),
                metric.avgDurationMs(),
                metric.lastSuccessfulAt(),
                metric.lastFailedAt()
        );
    }

    private String fallbackCredentialLabel(CredentialHealthKey key) {
        if (key == null || key.credentialId() == null) {
            return "未知凭证";
        }
        return "凭证 " + key.credentialId();
    }

    private String shortFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return null;
        }
        return fingerprint.length() <= 12 ? fingerprint : fingerprint.substring(0, 12);
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return (double) numerator / (double) denominator;
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
                entity.getClientFamily(),
                entity.getClientInstance(),
                entity.getWorkspaceHint(),
                entity.getSessionAffinitySource(),
                entity.getSessionAffinityKey(),
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

    private List<RequestTraceDetailResponse> requestTraceDetails(String requestId) {
        if (requestTraceDetailRepository == null || requestId == null || requestId.isBlank()) {
            return List.of();
        }
        return requestTraceDetailRepository.findAllByRequestIdOrderByCreatedAtAscIdAsc(requestId).stream()
                .map(this::toRequestTraceDetailResponse)
                .toList();
    }

    private RequestTraceDetailResponse toRequestTraceDetailResponse(RequestTraceDetailEntity entity) {
        return new RequestTraceDetailResponse(
                entity.getId(),
                entity.getRequestId(),
                entity.getStage(),
                entity.getDirection(),
                entity.getContentKind(),
                entity.getPayloadJson(),
                entity.getMetadataJson(),
                entity.getPayloadHash(),
                entity.getMetadataHash(),
                entity.getOriginalLength(),
                entity.getStoredLength(),
                entity.getMetadataOriginalLength(),
                entity.getMetadataStoredLength(),
                entity.isTruncated(),
                entity.isMetadataTruncated(),
                entity.isRedacted(),
                entity.isMetadataRedacted(),
                entity.getExpiresAt(),
                entity.getCreatedAt()
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

    private CodexObservabilityRequestResponse toCodexObservabilityResponse(
            RequestLogResponse requestLog,
            RouteDecisionLogResponse routeDecision,
            List<CacheHitLogResponse> cacheHits,
            UsageRecordEntity usageRecord) {
        int cacheHitTokens = cacheHits.stream().mapToInt(CacheHitLogResponse::cacheHitTokens).sum();
        int cacheWriteTokens = cacheHits.stream().mapToInt(CacheHitLogResponse::cacheWriteTokens).sum();
        int savedInputTokens = cacheHits.stream().mapToInt(CacheHitLogResponse::savedInputTokens).sum();
        Integer inputTokens = usageRecord == null ? null : usageRecord.getPromptTokens();
        Integer outputTokens = usageRecord == null ? null : usageRecord.getCompletionTokens();
        Integer reasoningTokens = usageRecord == null ? null : usageRecord.getReasoningTokens();
        Integer totalTokens = usageRecord == null ? null : usageRecord.getTotalTokens();
        String filterSummaryJson = extractFilterSummaryJson(routeDecision == null ? null : routeDecision.candidateSummaryJson());
        String filterSummary = filterSummaryJson == null ? "未记录 filter 命中" : "选路候选包含过滤命中元数据";
        String model = firstNonBlank(
                requestLog.publicModel(),
                requestLog.requestedModel(),
                requestLog.resolvedModelKey(),
                routeDecision == null ? null : routeDecision.publicModel(),
                routeDecision == null ? null : routeDecision.requestedModel(),
                routeDecision == null ? null : routeDecision.resolvedModelKey()
        );
        String status = firstNonBlank(
                requestLog.status() == null ? null : requestLog.status().name(),
                requestLog.responseStatus(),
                requestLog.supportStatus(),
                routeDecision == null ? null : routeDecision.supportStatus()
        );
        ProviderType providerType = requestLog.providerType() != null
                ? requestLog.providerType()
                : routeDecision == null ? null : routeDecision.selectedProviderType();
        Long credentialId = requestLog.credentialId() != null
                ? requestLog.credentialId()
                : routeDecision == null ? null : routeDecision.selectedCredentialId();
        String routeSummary = routeDecision == null
                ? "选路详情待加载"
                : String.join(" / ", List.of(
                                routeDecision.selectedProviderType() == null ? "" : routeDecision.selectedProviderType().name(),
                                routeDecision.selectedCredentialId() == null ? "" : "credential " + routeDecision.selectedCredentialId(),
                                "candidates " + routeDecision.candidateCount(),
                                defaultString(routeDecision.supportStatus(), ""),
                                defaultString(routeDecision.degradationLevel(), "")
                        ).stream()
                        .filter(value -> !value.isBlank())
                        .toList());
        String cacheSummary = buildCacheSummary(cacheHitTokens, cacheWriteTokens, savedInputTokens, usageRecord);
        String errorSummary = buildErrorSummary(requestLog);
        String diagnosticJson = buildDiagnosticJson(
                requestLog,
                routeDecision,
                model,
                status,
                providerType,
                credentialId,
                filterSummary,
                cacheSummary,
                errorSummary
        );

        return new CodexObservabilityRequestResponse(
                requestLog.requestId(),
                requestLog.distributedKeyId(),
                requestLog.distributedKeyPrefix(),
                requestLog.clientFamily(),
                requestLog.clientInstance(),
                requestLog.workspaceHint(),
                requestLog.sessionAffinitySource(),
                requestLog.sessionAffinityKey(),
                model,
                status,
                providerType,
                credentialId,
                routeSummary,
                routeDecision == null ? null : routeDecision.candidateCount(),
                routeDecision == null ? requestLog.supportStatus() : routeDecision.supportStatus(),
                routeDecision == null ? requestLog.degradationLevel() : routeDecision.degradationLevel(),
                filterSummary,
                filterSummaryJson,
                inputTokens,
                outputTokens,
                reasoningTokens,
                totalTokens,
                cacheHitTokens,
                cacheWriteTokens,
                savedInputTokens,
                cacheSummary,
                errorSummary,
                diagnosticJson,
                requestLog.startedAt(),
                requestLog.completedAt(),
                requestLog.createdAt(),
                requestLog.durationMs()
        );
    }

    private boolean isCodexRequest(CodexObservabilityRequestResponse response) {
        String values = String.join(" ",
                defaultString(response.clientFamily(), ""),
                defaultString(response.clientInstance(), ""),
                defaultString(response.workspaceHint(), ""),
                defaultString(response.model(), ""),
                defaultString(response.providerType() == null ? null : response.providerType().name(), ""),
                defaultString(response.routeSummary(), "")
        ).toLowerCase(Locale.ROOT);
        return values.contains("codex")
                || values.contains("gpt-5")
                || values.contains("responses");
    }

    private boolean matchesText(String value, String filter) {
        String normalizedFilter = normalizeFilter(filter);
        if (normalizedFilter == null) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(normalizedFilter.toLowerCase(Locale.ROOT));
    }

    private String extractFilterSummaryJson(String candidateSummaryJson) {
        if (candidateSummaryJson == null || candidateSummaryJson.isBlank()) {
            return null;
        }
        String normalized = candidateSummaryJson.toLowerCase(Locale.ROOT);
        if (!normalized.contains("x_ai_gateway_filter") && !normalized.contains("filter")) {
            return null;
        }
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("source", "route_candidate_summary");
        node.put("summary", "candidate summary includes filter metadata");
        node.put("sample", redactText(candidateSummaryJson, 360));
        return node.toString();
    }

    private String buildCacheSummary(
            int cacheHitTokens,
            int cacheWriteTokens,
            int savedInputTokens,
            UsageRecordEntity usageRecord) {
        int usageCacheHitTokens = usageRecord == null ? 0 : usageRecord.getCacheHitTokens() + usageRecord.getUpstreamCacheHitTokens();
        int usageCacheWriteTokens = usageRecord == null ? 0 : usageRecord.getCacheWriteTokens() + usageRecord.getUpstreamCacheWriteTokens();
        int effectiveHitTokens = Math.max(cacheHitTokens, usageCacheHitTokens);
        int effectiveWriteTokens = Math.max(cacheWriteTokens, usageCacheWriteTokens);
        int effectiveSavedTokens = Math.max(savedInputTokens, usageRecord == null ? 0 : usageRecord.getSavedInputTokens());
        if (effectiveHitTokens == 0 && effectiveWriteTokens == 0 && effectiveSavedTokens == 0) {
            return "无缓存收益";
        }
        return "saved " + effectiveSavedTokens + " / hit " + effectiveHitTokens + " / write " + effectiveWriteTokens;
    }

    private String buildErrorSummary(RequestLogResponse requestLog) {
        String value = List.of(
                defaultString(requestLog.errorCode(), ""),
                defaultString(requestLog.errorMessage(), "")
        ).stream().filter(part -> !part.isBlank()).toList().toString();
        if ("[]".equals(value)) {
            return "无错误摘要";
        }
        return redactText(value.replace("[", "").replace("]", ""), 220);
    }

    private String buildDiagnosticJson(
            RequestLogResponse requestLog,
            RouteDecisionLogResponse routeDecision,
            String model,
            String status,
            ProviderType providerType,
            Long credentialId,
            String filterSummary,
            String cacheSummary,
            String errorSummary) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        putString(node, "requestId", requestLog.requestId());
        putString(node, "model", model);
        putString(node, "status", status);
        putString(node, "providerType", providerType == null ? null : providerType.name());
        putLong(node, "credentialId", credentialId);
        putString(node, "clientFamily", requestLog.clientFamily());
        putString(node, "clientInstance", requestLog.clientInstance());
        putString(node, "workspaceHint", requestLog.workspaceHint());
        putString(node, "sessionAffinitySource", requestLog.sessionAffinitySource());
        putString(node, "sessionAffinityKey", requestLog.sessionAffinityKey());
        putString(node, "routeSummary", routeDecision == null ? "选路详情待加载" : routeDecision.supportStatus());
        putString(node, "filterSummary", filterSummary);
        putString(node, "cacheSummary", cacheSummary);
        putString(node, "errorSummary", errorSummary);
        node.put("redaction", "已移除 prompt、token、secret、完整 auth.json 和完整 upstream 错误正文。");
        return node.toString();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "-";
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String redactText(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String sanitized = value
                .replaceAll("(sk-[A-Za-z0-9_-]{8})[A-Za-z0-9_-]+", "$1***")
                .replaceAll("Bearer\\s+[A-Za-z0-9._-]+", "Bearer ***")
                .replaceAll("([A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{12,}\\.)[A-Za-z0-9_-]{12,}", "$1***");
        return sanitized.substring(0, Math.min(maxLength, sanitized.length()));
    }

    private void putInstant(ObjectNode node, String fieldName, Instant value) {
        if (value == null) {
            node.putNull(fieldName);
        } else {
            node.put(fieldName, value.toString());
        }
    }

    private void putString(ObjectNode node, String fieldName, String value) {
        if (value == null) {
            node.putNull(fieldName);
        } else {
            node.put(fieldName, value);
        }
    }

    private void putLong(ObjectNode node, String fieldName, Long value) {
        if (value == null) {
            node.putNull(fieldName);
        } else {
            node.put(fieldName, value);
        }
    }

    public String generateCodexRecoveryCommand(String requestId) {
        RequestLogEntity requestLog = requestLogRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalArgumentException("未找到对应的 requestId。"));

        String sessionAffinityKey = requestLog.getSessionAffinityKey();
        if (sessionAffinityKey == null || sessionAffinityKey.isBlank()) {
            sessionAffinityKey = "default-session";
        }

        String parentMessageId = requestLog.getResponseObjectId();
        if (parentMessageId == null || parentMessageId.isBlank()) {
            parentMessageId = "msg-" + requestId;
        }

        String keyPlaceholder = "YOUR_API_KEY";
        if (requestLog.getDistributedKeyPrefix() != null && !requestLog.getDistributedKeyPrefix().isBlank()) {
            keyPlaceholder = requestLog.getDistributedKeyPrefix() + "...";
        }

        return String.format(
                "export OPENAI_BASE_URL=\"https://gateway.example.com/v1\" && " +
                "export OPENAI_API_KEY=\"%s\" && " +
                "codex resume --session-id %s --parent-message-id %s",
                keyPlaceholder, sessionAffinityKey, parentMessageId
        );
    }

    private record TimeWindow(Instant from, Instant to) {
    }

    private record CredentialHealthKey(Long credentialId, ProviderType providerType) {
    }

    private class HealthAccumulator {
        private long totalRequests;
        private long successfulRequests;
        private long failedRequests;
        private long canceledRequests;
        private long totalDurationMs;
        private long durationSampleCount;
        private Instant lastSuccessfulAt;
        private Instant lastFailedAt;

        private void accept(RequestLogEntity requestLog) {
            if (requestLog == null) {
                return;
            }
            totalRequests++;
            GatewayRequestStatus status = requestLog.getStatus();
            Instant completedAt = firstNonNullInstant(requestLog.getCompletedAt(), requestLog.getStartedAt(), requestLog.getCreatedAt());
            if (status == GatewayRequestStatus.COMPLETED) {
                successfulRequests++;
                lastSuccessfulAt = maxInstant(lastSuccessfulAt, completedAt);
            } else if (status == GatewayRequestStatus.FAILED) {
                failedRequests++;
                lastFailedAt = maxInstant(lastFailedAt, completedAt);
            } else if (status == GatewayRequestStatus.CANCELED) {
                canceledRequests++;
            }
            if (requestLog.getDurationMs() != null && requestLog.getDurationMs() >= 0) {
                totalDurationMs += requestLog.getDurationMs();
                durationSampleCount++;
            }
        }

        private HealthMetricResponse toHealthMetric() {
            return new HealthMetricResponse(
                    totalRequests,
                    successfulRequests,
                    failedRequests,
                    canceledRequests,
                    ratio(successfulRequests, totalRequests),
                    totalRequests == 0 ? 0D : 1D - ratio(failedRequests, totalRequests),
                    ratio(failedRequests, totalRequests),
                    ratio(canceledRequests, totalRequests),
                    ratio(totalDurationMs, durationSampleCount),
                    lastSuccessfulAt,
                    lastFailedAt
            );
        }
    }

    private Instant firstNonNullInstant(Instant... values) {
        if (values == null) {
            return null;
        }
        for (Instant value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Instant maxInstant(Instant current, Instant candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.isAfter(current)) {
            return candidate;
        }
        return current;
    }
}
