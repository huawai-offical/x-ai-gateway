package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AnalyticsOverviewResponse;
import com.prodigalgal.xaigateway.admin.api.CacheHitLogResponse;
import com.prodigalgal.xaigateway.admin.api.DashboardOverviewResponse;
import com.prodigalgal.xaigateway.admin.api.RouteDecisionLogResponse;
import com.prodigalgal.xaigateway.admin.api.UpstreamCacheReferenceResponse;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardQueryService {

    private static final int DEFAULT_RECENT_LIMIT = 5;
    private static final int MIN_ROUTE_DECISIONS_FOR_RATIO_ALERT = 10;
    private static final double LOW_CACHE_HIT_RATIO_THRESHOLD = 0.20D;
    private static final double HOT_CREDENTIAL_SHARE_THRESHOLD = 0.80D;
    private static final Duration EXPIRING_REFERENCE_WARNING_WINDOW = Duration.ofMinutes(30);

    private final AnalyticsQueryService analyticsQueryService;
    private final ObservabilityQueryService observabilityQueryService;

    public DashboardQueryService(
            AnalyticsQueryService analyticsQueryService,
            ObservabilityQueryService observabilityQueryService) {
        this.analyticsQueryService = analyticsQueryService;
        this.observabilityQueryService = observabilityQueryService;
    }

    public DashboardOverviewResponse overview(
            Long distributedKeyId,
            ProviderType providerType,
            Instant from,
            Instant to,
            Integer bucketMinutes) {
        AnalyticsOverviewResponse analyticsOverview = analyticsQueryService.overview(
                distributedKeyId,
                providerType,
                from,
                to,
                bucketMinutes);
        List<RouteDecisionLogResponse> allRouteDecisions =
                observabilityQueryService.listRouteDecisions(distributedKeyId, providerType, from, to);
        List<CacheHitLogResponse> allCacheHits =
                observabilityQueryService.listCacheHits(distributedKeyId, providerType, from, to);
        List<UpstreamCacheReferenceResponse> activeReferencesAll =
                observabilityQueryService.listUpstreamCacheReferences(distributedKeyId, providerType, "ACTIVE", from, to);

        List<RouteDecisionLogResponse> recentRouteDecisions = limit(allRouteDecisions, DEFAULT_RECENT_LIMIT);
        List<CacheHitLogResponse> recentCacheHits = limit(allCacheHits, DEFAULT_RECENT_LIMIT);
        List<UpstreamCacheReferenceResponse> activeReferences = limit(activeReferencesAll, DEFAULT_RECENT_LIMIT);

        long routeDecisionCount = analyticsOverview.sampledRouteDecisionCount();
        long cacheHitCount = analyticsOverview.sampledCacheHitCount();
        long activeReferenceCount = analyticsOverview.sampledActiveCacheReferenceCount();
        long usageRecordCount = analyticsOverview.sampledUsageRecordCount();
        long finalUsageRecordCount = analyticsOverview.sampledFinalUsageRecordCount();
        long partialUsageRecordCount = analyticsOverview.sampledPartialUsageRecordCount();
        long totalSavedInputTokens = analyticsOverview.totalSavedInputTokens();
        double cacheHitRatio = ratio(cacheHitCount, routeDecisionCount);
        double averageSavedInputTokensPerHit = ratio(totalSavedInputTokens, cacheHitCount);
        List<UpstreamCacheReferenceResponse> expiringReferences = expiringReferences(activeReferencesAll);
        List<DashboardOverviewResponse.CredentialActivityItem> credentialRanking =
                buildCredentialRanking(allRouteDecisions, allCacheHits);
        Instant referenceTime = analyticsOverview.sampledTo() == null ? Instant.now() : analyticsOverview.sampledTo();

        return new DashboardOverviewResponse(
                analyticsOverview.sampledFrom(),
                analyticsOverview.sampledTo(),
                analyticsOverview.bucketMinutes(),
                new DashboardOverviewResponse.SummaryCards(
                        routeDecisionCount,
                        cacheHitCount,
                        activeReferenceCount,
                        usageRecordCount,
                        finalUsageRecordCount,
                        partialUsageRecordCount,
                        analyticsOverview.totalCacheHitTokens(),
                        analyticsOverview.totalCacheWriteTokens(),
                        totalSavedInputTokens,
                        cacheHitRatio,
                        averageSavedInputTokensPerHit
                ),
                analyticsOverview.providerBreakdown(),
                analyticsOverview.protocolBreakdown(),
                analyticsOverview.modelGroupBreakdown(),
                analyticsOverview.selectionSourceBreakdown(),
                analyticsOverview.cacheSourceBreakdown(),
                analyticsOverview.usageCompletenessBreakdown(),
                credentialRanking,
                buildAlerts(
                        routeDecisionCount,
                        cacheHitRatio,
                        analyticsOverview.totalCacheHitTokens(),
                        analyticsOverview.totalCacheWriteTokens(),
                        analyticsOverview.providerBreakdown(),
                        analyticsOverview.modelGroupBreakdown(),
                        credentialRanking,
                        expiringReferences,
                        referenceTime
                ),
                analyticsOverview.timeline(),
                recentRouteDecisions,
                recentCacheHits,
                activeReferences,
                expiringReferences
        );
    }

    private <T> List<T> limit(List<T> items, int maxSize) {
        if (items.size() <= maxSize) {
            return items;
        }
        return items.subList(0, maxSize);
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return (double) numerator / denominator;
    }

    private List<DashboardOverviewResponse.CredentialActivityItem> buildCredentialRanking(
            List<RouteDecisionLogResponse> allRouteDecisions,
            List<CacheHitLogResponse> allCacheHits) {
        Map<Long, List<RouteDecisionLogResponse>> routeGroups = allRouteDecisions.stream()
                .filter(item -> item.selectedCredentialId() != null)
                .collect(Collectors.groupingBy(RouteDecisionLogResponse::selectedCredentialId));
        Map<Long, List<CacheHitLogResponse>> cacheGroups = allCacheHits.stream()
                .filter(item -> item.credentialId() != null)
                .collect(Collectors.groupingBy(CacheHitLogResponse::credentialId));

        LinkedHashSet<Long> credentialIds = new LinkedHashSet<>();
        credentialIds.addAll(routeGroups.keySet());
        credentialIds.addAll(cacheGroups.keySet());

        return credentialIds.stream()
                .map(credentialId -> buildCredentialActivityItem(
                        credentialId,
                        routeGroups.getOrDefault(credentialId, List.of()),
                        cacheGroups.getOrDefault(credentialId, List.of())
                ))
                .sorted(Comparator
                        .comparingLong(DashboardOverviewResponse.CredentialActivityItem::routeDecisionCount)
                        .reversed()
                        .thenComparing(
                                Comparator.comparingLong(DashboardOverviewResponse.CredentialActivityItem::cacheHitTokens)
                                        .reversed()))
                .limit(DEFAULT_RECENT_LIMIT)
                .toList();
    }

    private DashboardOverviewResponse.CredentialActivityItem buildCredentialActivityItem(
            Long credentialId,
            List<RouteDecisionLogResponse> routeItems,
            List<CacheHitLogResponse> cacheItems) {
        RouteDecisionLogResponse routeSample = routeItems.isEmpty() ? null : routeItems.get(0);
        CacheHitLogResponse cacheSample = cacheItems.isEmpty() ? null : cacheItems.get(0);
        String providerType = routeSample != null && routeSample.selectedProviderType() != null
                ? routeSample.selectedProviderType().name()
                : cacheSample != null && cacheSample.providerType() != null
                ? cacheSample.providerType().name()
                : "UNKNOWN";
        String baseUrl = routeSample == null ? null : routeSample.selectedBaseUrl();

        return new DashboardOverviewResponse.CredentialActivityItem(
                credentialId,
                providerType + "#" + credentialId,
                baseUrl,
                providerType,
                routeItems.size(),
                cacheItems.size(),
                cacheItems.stream().mapToLong(CacheHitLogResponse::cacheHitTokens).sum(),
                cacheItems.stream().mapToLong(CacheHitLogResponse::cacheWriteTokens).sum(),
                cacheItems.stream().mapToLong(CacheHitLogResponse::savedInputTokens).sum()
        );
    }

    private List<UpstreamCacheReferenceResponse> expiringReferences(List<UpstreamCacheReferenceResponse> activeReferences) {
        return activeReferences.stream()
                .filter(reference -> reference.expireAt() != null)
                .sorted(Comparator.comparing(UpstreamCacheReferenceResponse::expireAt))
                .limit(DEFAULT_RECENT_LIMIT)
                .toList();
    }

    private List<DashboardOverviewResponse.DashboardAlert> buildAlerts(
            long routeDecisionCount,
            double cacheHitRatio,
            long totalCacheHitTokens,
            long totalCacheWriteTokens,
            List<AnalyticsOverviewResponse.BreakdownItem> providerRanking,
            List<AnalyticsOverviewResponse.BreakdownItem> modelGroupRanking,
            List<DashboardOverviewResponse.CredentialActivityItem> credentialRanking,
            List<UpstreamCacheReferenceResponse> expiringReferences,
            Instant referenceTime) {
        List<DashboardOverviewResponse.DashboardAlert> alerts = new java.util.ArrayList<>();

        if (routeDecisionCount >= MIN_ROUTE_DECISIONS_FOR_RATIO_ALERT && cacheHitRatio < LOW_CACHE_HIT_RATIO_THRESHOLD) {
            alerts.add(buildAlert(
                    "WARN",
                    "LOW_CACHE_HIT_RATIO",
                    "缓存命中率偏低",
                    "当前窗口内 cache hit ratio 为 " + formatRatio(cacheHitRatio)
                            + "，低于阈值 " + formatRatio(LOW_CACHE_HIT_RATIO_THRESHOLD) + "。",
                    topKeys(providerRanking, 3),
                    List.of(
                            "相同前缀请求的稳定性不足，导致 prefix/fingerprint affinity 未充分复用",
                            "请求虽然发生了 cache write，但后续重放流量不足或 prompt 形状漂移",
                            "当前热点 provider 的请求主要集中在低重复场景，天然不利于缓存回收"
                    ),
                    List.of(
                            "对照 provider / modelGroup ranking，优先检查热点模型的 prompt 头部是否稳定",
                            "结合 route decisions 与 cache hits，确认是否频繁切换 credential 或 baseUrl",
                            "必要时提高重复请求样本，验证 prefix affinity 与 upstream native cache 是否真正生效"
                    )
            ));
        }

        if (totalCacheWriteTokens > 0 && totalCacheHitTokens <= 0) {
            alerts.add(buildAlert(
                    "WARN",
                    "CACHE_WRITE_WITHOUT_HIT",
                    "存在缓存写入但尚未观察到命中",
                    "当前窗口内已有 cache write tokens=" + totalCacheWriteTokens
                            + "，但 cache hit tokens 仍为 0，建议观察前缀稳定性和重放流量。",
                    topKeys(modelGroupRanking, 3),
                    List.of(
                            "当前窗口仍处于缓存预热期，写入后尚未出现足够重复流量",
                            "请求在工具、附件或 metadata 上存在差异，导致缓存无法复用",
                            "上游缓存创建成功，但后续请求被路由到不同 credential"
                    ),
                    List.of(
                            "先检查热点 modelGroup 的 prompt 形状和 richer content 拼装是否稳定",
                            "确认相同 distributed key 是否持续命中同一 credential",
                            "对 expiring cache refs 进行巡检，避免刚创建即过期导致看不到命中"
                    )
            ));
        }

        if (!credentialRanking.isEmpty() && routeDecisionCount >= MIN_ROUTE_DECISIONS_FOR_RATIO_ALERT) {
            DashboardOverviewResponse.CredentialActivityItem hottestCredential = credentialRanking.get(0);
            double hottestCredentialShare = ratio(hottestCredential.routeDecisionCount(), routeDecisionCount);
            if (hottestCredentialShare >= HOT_CREDENTIAL_SHARE_THRESHOLD) {
                alerts.add(buildAlert(
                        "INFO",
                        "HOT_CREDENTIAL_CONCENTRATION",
                        "流量高度集中到单一 credential",
                        hottestCredential.displayKey() + " 承担了 " + formatRatio(hottestCredentialShare)
                                + " 的 route decisions，建议关注单点风险与冷却策略。",
                        List.of(hottestCredential.displayKey()),
                        List.of(
                                "当前 key 粘性与 prefix affinity 效果很强，导致大部分流量稳定回到单一 credential",
                                "候选 credential 的健康度、权重或模型覆盖范围差异过大，形成单点倾斜",
                                "存在缓存收益驱动的自然集中，但尚未设置额外的分摊保护"
                        ),
                        List.of(
                                "检查同模型组下其他 credential 的权重、健康状态和冷却状态",
                                "确认该热点 credential 是否具备足够配额与连接冗余",
                                "如需降低单点风险，可在后续加入更细的热度上限或探索性分流"
                        )
                ));
            }
        }

        List<UpstreamCacheReferenceResponse> expiringSoonReferences = expiringReferences.stream()
                .filter(reference -> !reference.expireAt().isAfter(referenceTime.plus(EXPIRING_REFERENCE_WARNING_WINDOW)))
                .toList();
        long expiringSoonCount = expiringSoonReferences.size();
        if (expiringSoonCount > 0) {
            alerts.add(buildAlert(
                    "WARN",
                    "UPSTREAM_CACHE_REFERENCE_EXPIRING",
                    "存在即将过期的上游缓存引用",
                    "未来 " + EXPIRING_REFERENCE_WARNING_WINDOW.toMinutes()
                            + " 分钟内预计有 " + expiringSoonCount + " 个活跃 cache reference 过期。",
                    expiringSoonReferences.stream()
                            .map(reference -> reference.externalCacheRef() + "@" + reference.modelGroup())
                            .limit(3)
                            .toList(),
                    List.of(
                            "当前 active cache reference 的 TTL 较短，且已接近过期边界",
                            "部分缓存引用可能只被创建未被持续复用，导致生命周期无法延长",
                            "请求窗口与缓存刷新节奏错位，可能在下一轮高峰前失效"
                    ),
                    List.of(
                            "优先检查即将过期的 cached content 是否仍处于热点模型和热点 prefix 上",
                            "如这些引用仍有高复用价值，尽快安排 refresh / reuse 路径接入",
                            "值班巡检时结合 credential ranking 判断是否需要提前切换或预热"
                    )
            ));
        }

        return alerts;
    }

    private DashboardOverviewResponse.DashboardAlert buildAlert(
            String severity,
            String code,
            String title,
            String detail,
            List<String> affectedEntities,
            List<String> suspectedCauses,
            List<String> suggestedActions) {
        return new DashboardOverviewResponse.DashboardAlert(
                severity,
                code,
                title,
                detail,
                affectedEntities,
                suspectedCauses,
                suggestedActions
        );
    }

    private List<String> topKeys(List<AnalyticsOverviewResponse.BreakdownItem> breakdownItems, int limit) {
        return breakdownItems.stream()
                .map(AnalyticsOverviewResponse.BreakdownItem::key)
                .limit(limit)
                .toList();
    }

    private String formatRatio(double ratio) {
        return String.format("%.1f%%", ratio * 100);
    }
}
