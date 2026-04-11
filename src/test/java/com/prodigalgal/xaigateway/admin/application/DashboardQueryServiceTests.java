package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AnalyticsOverviewResponse;
import com.prodigalgal.xaigateway.admin.api.CacheHitLogResponse;
import com.prodigalgal.xaigateway.admin.api.DashboardOverviewResponse;
import com.prodigalgal.xaigateway.admin.api.RouteDecisionLogResponse;
import com.prodigalgal.xaigateway.admin.api.UpstreamCacheReferenceResponse;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.Mockito.when;

class DashboardQueryServiceTests {

    @Test
    void shouldAggregateDashboardCardsAndTrimRecentLists() {
        AnalyticsQueryService analyticsQueryService = Mockito.mock(AnalyticsQueryService.class);
        ObservabilityQueryService observabilityQueryService = Mockito.mock(ObservabilityQueryService.class);
        DashboardQueryService service = new DashboardQueryService(analyticsQueryService, observabilityQueryService);

        Instant from = Instant.parse("2026-04-07T08:00:00Z");
        Instant to = Instant.parse("2026-04-07T10:00:00Z");

        when(analyticsQueryService.overview(1L, ProviderType.OPENAI_DIRECT, from, to, 60))
                .thenReturn(new AnalyticsOverviewResponse(
                        from,
                        to,
                        60,
                        12,
                        6,
                        2,
                        1800,
                        120,
                        1800,
                        List.of(new AnalyticsOverviewResponse.BreakdownItem("OPENAI_DIRECT", 8, 1200, 0, 1200)),
                        List.of(new AnalyticsOverviewResponse.BreakdownItem("openai", 7, 1200, 0, 1200)),
                        List.of(new AnalyticsOverviewResponse.BreakdownItem("PREFIX_AFFINITY", 9, 0, 0, 0)),
                        List.of(new AnalyticsOverviewResponse.BreakdownItem("gpt-4o", 7, 1200, 0, 1200)),
                        List.of(new AnalyticsOverviewResponse.TimelineBucket(from, 4, 2, 600, 40, 600))
                ));
        when(observabilityQueryService.listRouteDecisions(1L, ProviderType.OPENAI_DIRECT, from, to))
                .thenReturn(IntStream.range(0, 6)
                        .mapToObj(index -> routeDecision("route-" + index, 101L + index, from.plusSeconds(index * 60L)))
                        .toList());
        when(observabilityQueryService.listCacheHits(1L, ProviderType.OPENAI_DIRECT, from, to))
                .thenReturn(IntStream.range(0, 6)
                        .mapToObj(index -> cacheHit("cache-" + index, 101L + index, from.plusSeconds(index * 60L)))
                        .toList());
        when(observabilityQueryService.listUpstreamCacheReferences(1L, ProviderType.OPENAI_DIRECT, "ACTIVE", from, to))
                .thenReturn(IntStream.range(0, 6)
                        .mapToObj(index -> upstreamCacheReference("ref-" + index, null, from.plusSeconds(index * 60L), to))
                        .toList());

        DashboardOverviewResponse response = service.overview(1L, ProviderType.OPENAI_DIRECT, from, to, 60);

        assertEquals(from, response.sampledFrom());
        assertEquals(to, response.sampledTo());
        assertEquals(60, response.bucketMinutes());
        assertEquals(12, response.summary().routeDecisionCount());
        assertEquals(0.5, response.summary().cacheHitRatio());
        assertEquals(300.0, response.summary().averageSavedInputTokensPerHit());
        assertIterableEquals(List.of("OPENAI_DIRECT"), response.providerRanking().stream().map(AnalyticsOverviewResponse.BreakdownItem::key).toList());
        assertEquals(5, response.credentialRanking().size());
        assertEquals("OPENAI_DIRECT#101", response.credentialRanking().get(0).displayKey());
        assertEquals(0, response.alerts().size());
        assertEquals(5, response.recentRouteDecisions().size());
        assertEquals("route-0", response.recentRouteDecisions().get(0).requestId());
        assertEquals(5, response.recentCacheHits().size());
        assertEquals("cache-0", response.recentCacheHits().get(0).requestId());
        assertEquals(5, response.activeUpstreamCacheReferences().size());
        assertEquals("ref-0", response.activeUpstreamCacheReferences().get(0).externalCacheRef());
        assertEquals(0, response.expiringUpstreamCacheReferences().size());
    }

    @Test
    void shouldProduceAlertsForLowHitRatioAndExpiringReferences() {
        AnalyticsQueryService analyticsQueryService = Mockito.mock(AnalyticsQueryService.class);
        ObservabilityQueryService observabilityQueryService = Mockito.mock(ObservabilityQueryService.class);
        DashboardQueryService service = new DashboardQueryService(analyticsQueryService, observabilityQueryService);

        Instant from = Instant.parse("2026-04-07T08:00:00Z");
        Instant to = Instant.parse("2026-04-07T10:00:00Z");

        when(analyticsQueryService.overview(1L, ProviderType.OPENAI_DIRECT, from, to, 60))
                .thenReturn(new AnalyticsOverviewResponse(
                        from,
                        to,
                        60,
                        20,
                        2,
                        2,
                        0,
                        2000,
                        0,
                        List.of(new AnalyticsOverviewResponse.BreakdownItem("OPENAI_DIRECT", 20, 0, 2000, 0)),
                        List.of(new AnalyticsOverviewResponse.BreakdownItem("openai", 20, 0, 2000, 0)),
                        List.of(new AnalyticsOverviewResponse.BreakdownItem("PREFIX_AFFINITY", 20, 0, 0, 0)),
                        List.of(new AnalyticsOverviewResponse.BreakdownItem("gpt-4o", 20, 0, 2000, 0)),
                        List.of()
                ));
        when(observabilityQueryService.listRouteDecisions(1L, ProviderType.OPENAI_DIRECT, from, to))
                .thenReturn(IntStream.range(0, 20)
                        .mapToObj(index -> routeDecision("route-" + index, 101L, from.plusSeconds(index * 60L)))
                        .toList());
        when(observabilityQueryService.listCacheHits(1L, ProviderType.OPENAI_DIRECT, from, to))
                .thenReturn(List.of(
                        cacheHit("cache-0", 101L, from.plusSeconds(60L)),
                        cacheHit("cache-1", 101L, from.plusSeconds(120L))
                ));
        when(observabilityQueryService.listUpstreamCacheReferences(1L, ProviderType.OPENAI_DIRECT, "ACTIVE", from, to))
                .thenReturn(List.of(
                        upstreamCacheReference("ref-expiring", to.plusSeconds(10 * 60L), from, to.plusSeconds(10 * 60L)),
                        upstreamCacheReference("ref-later", to.plusSeconds(2 * 3600L), from.plusSeconds(60L), to.plusSeconds(2 * 3600L))
                ));

        DashboardOverviewResponse response = service.overview(1L, ProviderType.OPENAI_DIRECT, from, to, 60);

        assertEquals(4, response.alerts().size());
        assertIterableEquals(
                List.of(
                        "LOW_CACHE_HIT_RATIO",
                        "CACHE_WRITE_WITHOUT_HIT",
                        "HOT_CREDENTIAL_CONCENTRATION",
                        "UPSTREAM_CACHE_REFERENCE_EXPIRING"
                ),
                response.alerts().stream().map(DashboardOverviewResponse.DashboardAlert::code).toList()
        );
        assertEquals("OPENAI_DIRECT", response.alerts().get(0).affectedEntities().get(0));
        assertEquals("OPENAI_DIRECT#101", response.alerts().get(2).affectedEntities().get(0));
        assertEquals("ref-expiring@gpt-4o", response.alerts().get(3).affectedEntities().get(0));
        assertEquals(3, response.alerts().get(0).suspectedCauses().size());
        assertEquals(3, response.alerts().get(0).suggestedActions().size());
        assertEquals(2, response.expiringUpstreamCacheReferences().size());
        assertEquals("ref-expiring", response.expiringUpstreamCacheReferences().get(0).externalCacheRef());
    }

    private RouteDecisionLogResponse routeDecision(String requestId, Long credentialId, Instant createdAt) {
        return new RouteDecisionLogResponse(
                1L,
                requestId,
                1L,
                "sk-gw-test",
                "gpt-4o",
                "gpt-4o",
                "gpt-4o",
                "openai",
                "gpt-4o",
                "PREFIX_AFFINITY",
                credentialId,
                ProviderType.OPENAI_DIRECT,
                "https://api.openai.com",
                "prefix",
                "fingerprint",
                1,
                "{\"candidates\":[]}",
                createdAt
        );
    }

    private CacheHitLogResponse cacheHit(String requestId, Long credentialId, Instant createdAt) {
        return new CacheHitLogResponse(
                1L,
                requestId,
                1L,
                "openai",
                ProviderType.OPENAI_DIRECT,
                credentialId,
                "gpt-4o",
                "prefix",
                "fingerprint",
                "prompt_cache",
                300,
                0,
                300,
                null,
                createdAt
        );
    }

    private UpstreamCacheReferenceResponse upstreamCacheReference(
            String externalCacheRef,
            Instant expireAt,
            Instant createdAt,
            Instant updatedAt) {
        return new UpstreamCacheReferenceResponse(
                1L,
                1L,
                ProviderType.OPENAI_DIRECT,
                101L,
                "gpt-4o",
                "prefix",
                externalCacheRef,
                "ACTIVE",
                expireAt,
                createdAt,
                createdAt,
                updatedAt
        );
    }
}
