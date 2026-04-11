package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.AnalyticsQueryService;
import java.time.Instant;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = AnalyticsAdminController.class)
@Import(PermitAllSecurityTestConfig.class)
class AnalyticsAdminControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AnalyticsQueryService analyticsQueryService;

    @Test
    void shouldReturnAnalyticsOverview() {
        Instant from = Instant.parse("2026-04-07T08:00:00Z");
        Instant to = Instant.parse("2026-04-07T10:00:00Z");
        Mockito.when(analyticsQueryService.overview(1L, null, from, to, 60))
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
                        List.of(new AnalyticsOverviewResponse.TimelineBucket(
                                Instant.parse("2026-04-07T08:00:00Z"),
                                4,
                                2,
                                600,
                                40,
                                600
                        ))
                ));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/admin/analytics/overview")
                        .queryParam("distributedKeyId", 1)
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .queryParam("bucketMinutes", 60)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.bucketMinutes").isEqualTo(60)
                .jsonPath("$.sampledRouteDecisionCount").isEqualTo(12)
                .jsonPath("$.providerBreakdown[0].key").isEqualTo("OPENAI_DIRECT")
                .jsonPath("$.modelGroupBreakdown[0].savedInputTokens").isEqualTo(1200)
                .jsonPath("$.timeline[0].cacheHitTokens").isEqualTo(600);
    }
}
