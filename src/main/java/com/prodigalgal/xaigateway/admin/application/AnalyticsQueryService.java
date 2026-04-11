package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AnalyticsOverviewResponse;
import com.prodigalgal.xaigateway.admin.api.CacheHitLogResponse;
import com.prodigalgal.xaigateway.admin.api.RouteDecisionLogResponse;
import com.prodigalgal.xaigateway.admin.api.UpstreamCacheReferenceResponse;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AnalyticsQueryService {

    private static final int DEFAULT_BUCKET_MINUTES = 60;
    private static final int MIN_BUCKET_MINUTES = 5;
    private static final int MAX_BUCKET_MINUTES = 1440;
    private static final int MAX_BUCKET_COUNT = 500;
    private static final Duration DEFAULT_PARTIAL_WINDOW = Duration.ofHours(24);

    private final ObservabilityQueryService observabilityQueryService;

    public AnalyticsQueryService(ObservabilityQueryService observabilityQueryService) {
        this.observabilityQueryService = observabilityQueryService;
    }

    public AnalyticsOverviewResponse overview(Long distributedKeyId, ProviderType providerType) {
        return overview(distributedKeyId, providerType, null, null, null);
    }

    public AnalyticsOverviewResponse overview(
            Long distributedKeyId,
            ProviderType providerType,
            Instant from,
            Instant to,
            Integer bucketMinutes) {
        TimeWindow requestedWindow = resolveRequestedWindow(from, to);
        int resolvedBucketMinutes = normalizeBucketMinutes(bucketMinutes);

        List<RouteDecisionLogResponse> routeDecisions = requestedWindow == null
                ? observabilityQueryService.listRouteDecisions(distributedKeyId, providerType)
                : observabilityQueryService.listRouteDecisions(
                        distributedKeyId,
                        providerType,
                        requestedWindow.from(),
                        requestedWindow.to());
        List<CacheHitLogResponse> cacheHits = requestedWindow == null
                ? observabilityQueryService.listCacheHits(distributedKeyId, providerType)
                : observabilityQueryService.listCacheHits(distributedKeyId, providerType, requestedWindow.from(), requestedWindow.to());
        List<UpstreamCacheReferenceResponse> activeReferences = observabilityQueryService.listUpstreamCacheReferences(
                distributedKeyId,
                providerType,
                "ACTIVE",
                requestedWindow == null ? null : requestedWindow.from(),
                requestedWindow == null ? null : requestedWindow.to());

        List<RouteDecisionLogResponse> sortedRouteDecisions = routeDecisions.stream()
                .sorted(Comparator.comparing(RouteDecisionLogResponse::createdAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<CacheHitLogResponse> sortedCacheHits = cacheHits.stream()
                .sorted(Comparator.comparing(CacheHitLogResponse::createdAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        long totalCacheHitTokens = sortedCacheHits.stream().mapToLong(CacheHitLogResponse::cacheHitTokens).sum();
        long totalCacheWriteTokens = sortedCacheHits.stream().mapToLong(CacheHitLogResponse::cacheWriteTokens).sum();
        long totalSavedInputTokens = sortedCacheHits.stream().mapToLong(CacheHitLogResponse::savedInputTokens).sum();
        TimeWindow sampledWindow = resolveSampledWindow(requestedWindow, sortedRouteDecisions, sortedCacheHits);

        return new AnalyticsOverviewResponse(
                sampledWindow == null ? null : sampledWindow.from(),
                sampledWindow == null ? null : sampledWindow.to(),
                resolvedBucketMinutes,
                sortedRouteDecisions.size(),
                sortedCacheHits.size(),
                activeReferences.size(),
                totalCacheHitTokens,
                totalCacheWriteTokens,
                totalSavedInputTokens,
                providerBreakdown(sortedRouteDecisions, sortedCacheHits),
                protocolBreakdown(sortedRouteDecisions, sortedCacheHits),
                selectionSourceBreakdown(sortedRouteDecisions),
                modelGroupBreakdown(sortedRouteDecisions, sortedCacheHits),
                buildTimeline(sortedRouteDecisions, sortedCacheHits, sampledWindow, resolvedBucketMinutes)
        );
    }

    private List<AnalyticsOverviewResponse.BreakdownItem> providerBreakdown(
            List<RouteDecisionLogResponse> routeDecisions,
            List<CacheHitLogResponse> cacheHits) {
        return buildBreakdown(
                routeDecisions,
                cacheHits,
                RouteDecisionLogResponse::selectedProviderType,
                CacheHitLogResponse::providerType
        );
    }

    private List<AnalyticsOverviewResponse.BreakdownItem> protocolBreakdown(
            List<RouteDecisionLogResponse> routeDecisions,
            List<CacheHitLogResponse> cacheHits) {
        return buildBreakdown(
                routeDecisions,
                cacheHits,
                RouteDecisionLogResponse::protocol,
                CacheHitLogResponse::protocol
        );
    }

    private List<AnalyticsOverviewResponse.BreakdownItem> selectionSourceBreakdown(
            List<RouteDecisionLogResponse> routeDecisions) {
        Map<String, Long> routeCounts = routeDecisions.stream()
                .collect(Collectors.groupingBy(RouteDecisionLogResponse::selectionSource, Collectors.counting()));

        return routeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new AnalyticsOverviewResponse.BreakdownItem(
                        entry.getKey(),
                        entry.getValue(),
                        0,
                        0,
                        0
                ))
                .toList();
    }

    private List<AnalyticsOverviewResponse.BreakdownItem> modelGroupBreakdown(
            List<RouteDecisionLogResponse> routeDecisions,
            List<CacheHitLogResponse> cacheHits) {
        return buildBreakdown(
                routeDecisions,
                cacheHits,
                RouteDecisionLogResponse::modelGroup,
                CacheHitLogResponse::modelGroup
        );
    }

    private <K> List<AnalyticsOverviewResponse.BreakdownItem> buildBreakdown(
            List<RouteDecisionLogResponse> routeDecisions,
            List<CacheHitLogResponse> cacheHits,
            Function<RouteDecisionLogResponse, K> routeKeyExtractor,
            Function<CacheHitLogResponse, K> cacheKeyExtractor) {
        Map<String, Long> routeCounts = routeDecisions.stream()
                .collect(Collectors.groupingBy(entry -> stringify(routeKeyExtractor.apply(entry)), Collectors.counting()));

        Map<String, List<CacheHitLogResponse>> cacheGroups = cacheHits.stream()
                .collect(Collectors.groupingBy(entry -> stringify(cacheKeyExtractor.apply(entry))));

        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.addAll(routeCounts.keySet());
        keys.addAll(cacheGroups.keySet());

        return keys.stream()
                .map(key -> {
                    List<CacheHitLogResponse> hitLogs = cacheGroups.getOrDefault(key, List.of());
                    long hitTokens = hitLogs.stream().mapToLong(CacheHitLogResponse::cacheHitTokens).sum();
                    long writeTokens = hitLogs.stream().mapToLong(CacheHitLogResponse::cacheWriteTokens).sum();
                    long savedTokens = hitLogs.stream().mapToLong(CacheHitLogResponse::savedInputTokens).sum();
                    return new AnalyticsOverviewResponse.BreakdownItem(
                            key,
                            routeCounts.getOrDefault(key, 0L),
                            hitTokens,
                            writeTokens,
                            savedTokens
                    );
                })
                .sorted(Comparator.comparingLong(AnalyticsOverviewResponse.BreakdownItem::count).reversed())
                .toList();
    }

    private List<AnalyticsOverviewResponse.TimelineBucket> buildTimeline(
            List<RouteDecisionLogResponse> routeDecisions,
            List<CacheHitLogResponse> cacheHits,
            TimeWindow sampledWindow,
            int bucketMinutes) {
        if (sampledWindow == null) {
            return List.of();
        }

        Duration bucketSize = Duration.ofMinutes(bucketMinutes);
        Instant start = alignToBucket(sampledWindow.from(), bucketSize);
        Instant end = alignToBucket(sampledWindow.to(), bucketSize);
        long bucketCount = Duration.between(start, end).toMinutes() / bucketMinutes + 1;
        if (bucketCount > MAX_BUCKET_COUNT) {
            throw new IllegalArgumentException("时间窗口过大，请缩小范围或增大 bucketMinutes。");
        }

        Map<Instant, TimelineAccumulator> buckets = new LinkedHashMap<>();
        for (Instant current = start; !current.isAfter(end); current = current.plus(bucketSize)) {
            buckets.put(current, new TimelineAccumulator());
        }

        for (RouteDecisionLogResponse routeDecision : routeDecisions) {
            if (routeDecision.createdAt() == null) {
                continue;
            }
            Instant bucketStart = alignToBucket(routeDecision.createdAt(), bucketSize);
            TimelineAccumulator accumulator = buckets.get(bucketStart);
            if (accumulator != null) {
                accumulator.routeDecisionCount++;
            }
        }

        for (CacheHitLogResponse cacheHit : cacheHits) {
            if (cacheHit.createdAt() == null) {
                continue;
            }
            Instant bucketStart = alignToBucket(cacheHit.createdAt(), bucketSize);
            TimelineAccumulator accumulator = buckets.get(bucketStart);
            if (accumulator != null) {
                accumulator.cacheHitCount++;
                accumulator.cacheHitTokens += cacheHit.cacheHitTokens();
                accumulator.cacheWriteTokens += cacheHit.cacheWriteTokens();
                accumulator.savedInputTokens += cacheHit.savedInputTokens();
            }
        }

        return buckets.entrySet().stream()
                .map(entry -> entry.getValue().toTimelineBucket(entry.getKey()))
                .toList();
    }

    private TimeWindow resolveRequestedWindow(Instant from, Instant to) {
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

    private TimeWindow resolveSampledWindow(
            TimeWindow requestedWindow,
            List<RouteDecisionLogResponse> routeDecisions,
            List<CacheHitLogResponse> cacheHits) {
        if (requestedWindow != null) {
            return requestedWindow;
        }

        List<Instant> timestamps = new ArrayList<>(routeDecisions.stream()
                .map(RouteDecisionLogResponse::createdAt)
                .filter(Objects::nonNull)
                .toList());
        timestamps.addAll(cacheHits.stream()
                .map(CacheHitLogResponse::createdAt)
                .filter(Objects::nonNull)
                .toList());

        if (timestamps.isEmpty()) {
            return null;
        }

        Instant sampledFrom = timestamps.stream().min(Instant::compareTo).orElse(null);
        Instant sampledTo = timestamps.stream().max(Instant::compareTo).orElse(null);
        if (sampledFrom == null || sampledTo == null) {
            return null;
        }
        return new TimeWindow(sampledFrom, sampledTo);
    }

    private int normalizeBucketMinutes(Integer bucketMinutes) {
        int resolved = bucketMinutes == null ? DEFAULT_BUCKET_MINUTES : bucketMinutes;
        if (resolved < MIN_BUCKET_MINUTES || resolved > MAX_BUCKET_MINUTES) {
            throw new IllegalArgumentException("bucketMinutes 必须在 5 到 1440 之间。");
        }
        return resolved;
    }

    private Instant alignToBucket(Instant instant, Duration bucketSize) {
        long bucketSeconds = bucketSize.getSeconds();
        long aligned = Math.floorDiv(instant.getEpochSecond(), bucketSeconds) * bucketSeconds;
        return Instant.ofEpochSecond(aligned);
    }

    private String stringify(Object value) {
        return value == null ? "UNKNOWN" : value.toString();
    }

    private record TimeWindow(Instant from, Instant to) {
    }

    private static final class TimelineAccumulator {
        private long routeDecisionCount;
        private long cacheHitCount;
        private long cacheHitTokens;
        private long cacheWriteTokens;
        private long savedInputTokens;

        private AnalyticsOverviewResponse.TimelineBucket toTimelineBucket(Instant bucketStart) {
            return new AnalyticsOverviewResponse.TimelineBucket(
                    bucketStart,
                    routeDecisionCount,
                    cacheHitCount,
                    cacheHitTokens,
                    cacheWriteTokens,
                    savedInputTokens
            );
        }
    }
}
