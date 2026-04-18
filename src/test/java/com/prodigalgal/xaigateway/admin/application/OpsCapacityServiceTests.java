package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.DashboardOverviewResponse;
import com.prodigalgal.xaigateway.admin.api.OpsCapacitySummaryResponse;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyGovernanceService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class OpsCapacityServiceTests {

    @Test
    void shouldBuildBudgetPressureFromDistributedKeysAndDashboardSignals() {
        DashboardQueryService dashboardQueryService = Mockito.mock(DashboardQueryService.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        DistributedKeyGovernanceService distributedKeyGovernanceService = Mockito.mock(DistributedKeyGovernanceService.class);
        OpsCapacityService service = new OpsCapacityService(
                dashboardQueryService,
                distributedKeyQueryService,
                distributedKeyGovernanceService
        );

        DistributedKeyView keyView = new DistributedKeyView(
                1L,
                "main-key",
                "sk-gw-main",
                "sk-gw-main****",
                List.of("openai"),
                List.of("gpt-4o"),
                List.of(),
                null,
                100_000L,
                3600,
                100,
                10_000,
                20,
                null,
                List.of(),
                false,
                List.of()
        );

        when(distributedKeyQueryService.listActive()).thenReturn(List.of(keyView));
        when(distributedKeyGovernanceService.snapshot(keyView))
                .thenReturn(new DistributedKeyGovernanceService.GovernanceWindowSnapshot(
                        90_000L,
                        85L,
                        9_000L,
                        18L,
                        0.90D,
                        0.85D,
                        0.90D,
                        0.90D,
                        "HIGH",
                        List.of("budget usage is close to the current window limit")
                ));
        when(dashboardQueryService.overview(Mockito.isNull(), Mockito.isNull(), any(), any(), Mockito.eq(60)))
                .thenReturn(new DashboardOverviewResponse(
                        Instant.parse("2026-04-18T01:00:00Z"),
                        Instant.parse("2026-04-18T02:00:00Z"),
                        60,
                        new DashboardOverviewResponse.SummaryCards(40, 12, 3, 18, 10, 8, 900, 1200, 800, 0.30D, 66.0D),
                        List.of(new com.prodigalgal.xaigateway.admin.api.AnalyticsOverviewResponse.BreakdownItem("OPENAI_DIRECT", 30, 900, 1200, 800)),
                        List.of(),
                        List.of(new com.prodigalgal.xaigateway.admin.api.AnalyticsOverviewResponse.BreakdownItem("openai", 30, 900, 1200, 800)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(new DashboardOverviewResponse.CredentialActivityItem(101L, "OPENAI_DIRECT#101", "https://api.openai.com", "OPENAI_DIRECT", 30, 12, 900, 1200, 800)),
                        List.of(new DashboardOverviewResponse.DashboardAlert(
                                "WARN",
                                "HOT_CREDENTIAL_CONCENTRATION",
                                "流量高度集中到单一 credential",
                                "单点风险偏高。",
                                List.of("OPENAI_DIRECT#101"),
                                List.of("流量集中"),
                                List.of("检查热点 credential 的冗余与配额")
                        )),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                ));

        OpsCapacitySummaryResponse response = service.summary(Instant.parse("2026-04-18T02:00:00Z"));

        assertEquals(1, response.distributedKeys().size());
        assertEquals("HIGH", response.distributedKeys().get(0).pressureLevel());
        assertEquals(1_000L, response.distributedKeys().get(0).remainingTpm());
        assertEquals("OPENAI_DIRECT", response.providerRanking().get(0).key());
        assertFalse(response.recommendedActions().isEmpty());
        assertEquals("检查热点 credential 的冗余与配额", response.recommendedActions().get(0));
    }
}
