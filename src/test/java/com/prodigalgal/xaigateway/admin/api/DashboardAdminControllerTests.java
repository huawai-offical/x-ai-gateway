package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.DashboardQueryService;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = DashboardAdminController.class)
@Import(PermitAllSecurityTestConfig.class)
class DashboardAdminControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private DashboardQueryService dashboardQueryService;

    @Test
    void shouldReturnDashboardOverview() {
        Instant from = Instant.parse("2026-04-07T08:00:00Z");
        Instant to = Instant.parse("2026-04-07T10:00:00Z");

        Mockito.when(dashboardQueryService.overview(1L, ProviderType.OPENAI_DIRECT, from, to, 60))
                .thenReturn(new DashboardOverviewResponse(
                        from,
                        to,
                        60,
                        new DashboardOverviewResponse.SummaryCards(
                                12,
                                6,
                                2,
                                5,
                                4,
                                1,
                                1800,
                                120,
                                1800,
                                0.5,
                                300.0
                        ),
                        List.of(new AnalyticsOverviewResponse.BreakdownItem("OPENAI_DIRECT", 8, 1200, 0, 1200)),
                        List.of(new AnalyticsOverviewResponse.BreakdownItem("openai", 7, 1200, 0, 1200)),
                        List.of(new AnalyticsOverviewResponse.BreakdownItem("gpt-4o", 7, 1200, 0, 1200)),
                        List.of(new AnalyticsOverviewResponse.BreakdownItem("PREFIX_AFFINITY", 9, 0, 0, 0)),
                        List.of(new AnalyticsOverviewResponse.BreakdownItem("prompt_cache", 6, 1800, 120, 1800)),
                        List.of(new AnalyticsOverviewResponse.CountBreakdownItem("FINAL", 4)),
                        List.of(new DashboardOverviewResponse.CredentialActivityItem(
                                101L,
                                "OPENAI_DIRECT#101",
                                "https://api.openai.com",
                                "OPENAI_DIRECT",
                                8,
                                3,
                                1200,
                                0,
                                1200
                        )),
                        List.of(new DashboardOverviewResponse.DashboardAlert(
                                "WARN",
                                "UPSTREAM_CACHE_REFERENCE_EXPIRING",
                                "存在即将过期的上游缓存引用",
                                "未来 30 分钟内预计有 1 个活跃 cache reference 过期。",
                                List.of("cached-content-1@gpt-4o"),
                                List.of("当前 active cache reference 的 TTL 较短，且已接近过期边界"),
                                List.of("优先检查即将过期的 cached content 是否仍处于热点模型和热点 prefix 上")
                        )),
                        List.of(new AnalyticsOverviewResponse.TimelineBucket(
                                from,
                                4,
                                2,
                                600,
                                40,
                                600
                        )),
                        List.of(new RouteDecisionLogResponse(
                                1L,
                                "req-1",
                                1L,
                                "sk-gw-test",
                                "gpt-4o",
                                "gpt-4o",
                                "gpt-4o",
                                "openai",
                                "gpt-4o",
                                "PREFIX_AFFINITY",
                                101L,
                                ProviderType.OPENAI_DIRECT,
                                "https://api.openai.com",
                                "prefix",
                                "fingerprint",
                                1,
                                "{\"candidates\":[]}",
                                from
                        )),
                        List.of(new CacheHitLogResponse(
                                1L,
                                "req-1",
                                1L,
                                "openai",
                                ProviderType.OPENAI_DIRECT,
                                101L,
                                "gpt-4o",
                                "prefix",
                                "fingerprint",
                                "prompt_cache",
                                300,
                                0,
                                300,
                                null,
                                from
                        )),
                        List.of(new UpstreamCacheReferenceResponse(
                                1L,
                                1L,
                                ProviderType.OPENAI_DIRECT,
                                101L,
                                "gpt-4o",
                                "prefix",
                                "cached-content-1",
                                "ACTIVE",
                                null,
                                from,
                                from,
                                to
                        )),
                        List.of(new UpstreamCacheReferenceResponse(
                                1L,
                                1L,
                                ProviderType.OPENAI_DIRECT,
                                101L,
                                "gpt-4o",
                                "prefix",
                                "cached-content-1",
                                "ACTIVE",
                                to,
                                from,
                                from,
                                to
                        ))
                ));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/admin/dashboard/overview")
                        .queryParam("distributedKeyId", 1)
                        .queryParam("providerType", "OPENAI_DIRECT")
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .queryParam("bucketMinutes", 60)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.summary.routeDecisionCount").isEqualTo(12)
                .jsonPath("$.summary.usageRecordCount").isEqualTo(5)
                .jsonPath("$.summary.cacheHitRatio").isEqualTo(0.5)
                .jsonPath("$.providerRanking[0].key").isEqualTo("OPENAI_DIRECT")
                .jsonPath("$.cacheSourceRanking[0].key").isEqualTo("prompt_cache")
                .jsonPath("$.credentialRanking[0].displayKey").isEqualTo("OPENAI_DIRECT#101")
                .jsonPath("$.alerts[0].code").isEqualTo("UPSTREAM_CACHE_REFERENCE_EXPIRING")
                .jsonPath("$.alerts[0].affectedEntities[0]").isEqualTo("cached-content-1@gpt-4o")
                .jsonPath("$.alerts[0].suggestedActions[0]").exists()
                .jsonPath("$.recentRouteDecisions[0].requestId").isEqualTo("req-1")
                .jsonPath("$.activeUpstreamCacheReferences[0].status").isEqualTo("ACTIVE")
                .jsonPath("$.expiringUpstreamCacheReferences[0].externalCacheRef").isEqualTo("cached-content-1");
    }
}
