package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AnalyticsOverviewResponse;
import com.prodigalgal.xaigateway.admin.api.CacheHitLogResponse;
import com.prodigalgal.xaigateway.admin.api.RouteDecisionLogResponse;
import com.prodigalgal.xaigateway.admin.api.UpstreamCacheReferenceResponse;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class AnalyticsQueryServiceTests {

    @Test
    void shouldBuildTimelineForRequestedWindowAndKeepCacheOnlyBreakdownKeys() {
        ObservabilityQueryService observabilityQueryService = Mockito.mock(ObservabilityQueryService.class);
        AnalyticsQueryService service = new AnalyticsQueryService(observabilityQueryService);

        Instant from = Instant.parse("2026-04-07T08:15:00Z");
        Instant to = Instant.parse("2026-04-07T10:45:00Z");

        when(observabilityQueryService.listRouteDecisions(1L, ProviderType.OPENAI_DIRECT, from, to))
                .thenReturn(List.of(
                        routeDecision("route-1", "openai", "gpt-4o", "PREFIX_AFFINITY", ProviderType.OPENAI_DIRECT,
                                Instant.parse("2026-04-07T08:20:00Z")),
                        routeDecision("route-2", "openai", "gpt-4o", "WEIGHTED_HASH", ProviderType.OPENAI_DIRECT,
                                Instant.parse("2026-04-07T09:10:00Z"))
                ));
        when(observabilityQueryService.listCacheHits(1L, ProviderType.OPENAI_DIRECT, from, to))
                .thenReturn(List.of(
                        cacheHit("cache-1", "openai", "gpt-4o", ProviderType.OPENAI_DIRECT, 300, 20, 300,
                                Instant.parse("2026-04-07T08:40:00Z")),
                        cacheHit("cache-2", "gemini", "gemini-2.5-pro", ProviderType.GEMINI_DIRECT, 700, 0, 700,
                                Instant.parse("2026-04-07T10:05:00Z"))
                ));
        when(observabilityQueryService.listUpstreamCacheReferences(1L, ProviderType.OPENAI_DIRECT, "ACTIVE", from, to))
                .thenReturn(List.of(new UpstreamCacheReferenceResponse(
                        1L,
                        1L,
                        ProviderType.OPENAI_DIRECT,
                        101L,
                        "gpt-4o",
                        "prefix",
                        "cached-content-1",
                        "ACTIVE",
                        null,
                        Instant.parse("2026-04-07T10:00:00Z"),
                        Instant.parse("2026-04-07T08:00:00Z"),
                        Instant.parse("2026-04-07T10:00:00Z")
                )));

        AnalyticsOverviewResponse response = service.overview(1L, ProviderType.OPENAI_DIRECT, from, to, 60);

        assertEquals(from, response.sampledFrom());
        assertEquals(to, response.sampledTo());
        assertEquals(60, response.bucketMinutes());
        assertEquals(2, response.sampledRouteDecisionCount());
        assertEquals(2, response.sampledCacheHitCount());
        assertEquals(3, response.timeline().size());
        assertEquals(1, response.timeline().get(0).routeDecisionCount());
        assertEquals(1, response.timeline().get(0).cacheHitCount());
        assertEquals(700, response.timeline().get(2).cacheHitTokens());
        assertTrue(response.providerBreakdown().stream().anyMatch(item ->
                item.key().equals("GEMINI_DIRECT")
                        && item.count() == 0
                        && item.cacheHitTokens() == 700));
    }

    private RouteDecisionLogResponse routeDecision(
            String requestId,
            String protocol,
            String modelGroup,
            String selectionSource,
            ProviderType providerType,
            Instant createdAt) {
        return new RouteDecisionLogResponse(
                1L,
                requestId,
                1L,
                "sk-gw-test",
                modelGroup,
                modelGroup,
                modelGroup,
                protocol,
                modelGroup,
                selectionSource,
                101L,
                providerType,
                "https://api.example.com",
                "prefix",
                "fingerprint",
                1,
                "{\"candidates\":[]}",
                createdAt
        );
    }

    private CacheHitLogResponse cacheHit(
            String requestId,
            String protocol,
            String modelGroup,
            ProviderType providerType,
            int cacheHitTokens,
            int cacheWriteTokens,
            int savedInputTokens,
            Instant createdAt) {
        return new CacheHitLogResponse(
                1L,
                requestId,
                1L,
                protocol,
                providerType,
                101L,
                modelGroup,
                "prefix",
                "fingerprint",
                "prompt_cache",
                cacheHitTokens,
                cacheWriteTokens,
                savedInputTokens,
                null,
                createdAt
        );
    }
}
