package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.DashboardOverviewResponse;
import com.prodigalgal.xaigateway.admin.api.OpsSloSummaryResponse;
import com.prodigalgal.xaigateway.admin.api.SloPolicyRequest;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestStatus;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.RequestLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SloPolicyEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.OpsAlertEventRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RequestLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SloPolicyRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class OpsSloServiceTests {

    @Test
    void shouldComputeErrorBudgetRiskAndRecommendations() {
        SloPolicyRepository sloPolicyRepository = Mockito.mock(SloPolicyRepository.class);
        RequestLogRepository requestLogRepository = Mockito.mock(RequestLogRepository.class);
        OpsAlertEventRepository opsAlertEventRepository = Mockito.mock(OpsAlertEventRepository.class);
        DashboardQueryService dashboardQueryService = Mockito.mock(DashboardQueryService.class);
        OpsSloService service = new OpsSloService(
                sloPolicyRepository,
                requestLogRepository,
                opsAlertEventRepository,
                dashboardQueryService
        );

        Instant now = Instant.parse("2026-04-18T02:20:00Z");
        SloPolicyEntity gatewayPolicy = new SloPolicyEntity();
        gatewayPolicy.setPolicyName("gateway-availability");
        gatewayPolicy.setScopeType("GATEWAY");
        gatewayPolicy.setWindowMinutes(60);
        gatewayPolicy.setErrorBudgetRatio(new BigDecimal("0.05"));
        gatewayPolicy.setWarningBurnRate(new BigDecimal("1.00"));
        gatewayPolicy.setCriticalBurnRate(new BigDecimal("2.00"));
        gatewayPolicy.setEnabled(true);

        when(sloPolicyRepository.findAllByEnabledTrueOrderByCreatedAtAsc()).thenReturn(List.of(gatewayPolicy));
        when(requestLogRepository.searchWithinWindow(Mockito.isNull(), Mockito.isNull(), any(), any()))
                .thenReturn(List.of(
                        request("req-1", GatewayRequestStatus.COMPLETED, 1L, ProviderType.OPENAI_DIRECT, "openai"),
                        request("req-2", GatewayRequestStatus.COMPLETED, 1L, ProviderType.OPENAI_DIRECT, "openai"),
                        request("req-3", GatewayRequestStatus.FAILED, 1L, ProviderType.OPENAI_DIRECT, "openai"),
                        request("req-4", GatewayRequestStatus.FAILED, 1L, ProviderType.OPENAI_DIRECT, "openai"),
                        request("req-5", GatewayRequestStatus.FAILED, 1L, ProviderType.OPENAI_DIRECT, "openai")
                ));
        when(opsAlertEventRepository.countByStatus("SILENCED")).thenReturn(2L);
        when(dashboardQueryService.overview(Mockito.isNull(), Mockito.isNull(), any(), any(), Mockito.eq(15)))
                .thenReturn(new DashboardOverviewResponse(
                        now.minusSeconds(3600),
                        now,
                        15,
                        new DashboardOverviewResponse.SummaryCards(5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(new DashboardOverviewResponse.DashboardAlert(
                                "HIGH",
                                "HOT_DISTRIBUTED_KEY",
                                "热点 key 风险",
                                "某个 distributed key 承压过高。",
                                List.of("dk-main"),
                                List.of("流量集中"),
                                List.of("限流热点 distributed key")
                        )),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                ));

        OpsSloSummaryResponse response = service.summary(now);

        assertEquals(5, response.summary().requestCount());
        assertEquals(3, response.summary().failedRequestCount());
        assertEquals(0.60D, response.summary().errorRate(), 0.0001D);
        assertEquals(12.0D, response.summary().burnRate(), 0.0001D);
        assertEquals(0.0D, response.summary().errorBudgetRemainingRatio(), 0.0001D);
        assertEquals("CRITICAL", response.summary().riskLevel());
        assertEquals(2L, response.summary().silencedAlertCount());
        assertEquals(1, response.risks().size());
        assertEquals("gateway-availability", response.risks().get(0).policyName());
        assertFalse(response.recommendedActions().isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(response.recommendedActions().contains("限流热点 distributed key"));
    }

    @Test
    void shouldSaveSloPolicy() {
        SloPolicyRepository sloPolicyRepository = Mockito.mock(SloPolicyRepository.class);
        RequestLogRepository requestLogRepository = Mockito.mock(RequestLogRepository.class);
        OpsAlertEventRepository opsAlertEventRepository = Mockito.mock(OpsAlertEventRepository.class);
        DashboardQueryService dashboardQueryService = Mockito.mock(DashboardQueryService.class);
        OpsSloService service = new OpsSloService(
                sloPolicyRepository,
                requestLogRepository,
                opsAlertEventRepository,
                dashboardQueryService
        );

        when(sloPolicyRepository.save(any())).thenAnswer(invocation -> {
            return invocation.getArgument(0);
        });

        var response = service.savePolicy(null, new SloPolicyRequest(
                "provider-openai",
                "PROVIDER",
                "OPENAI_DIRECT",
                30,
                new BigDecimal("0.02"),
                new BigDecimal("1.50"),
                new BigDecimal("3.00"),
                true,
                "provider scoped policy"
        ));

        assertEquals("provider-openai", response.policyName());
        assertEquals("PROVIDER", response.scopeType());
        assertEquals("OPENAI_DIRECT", response.scopeRef());
        assertEquals(30, response.windowMinutes());
    }

    private RequestLogEntity request(
            String requestId,
            GatewayRequestStatus status,
            Long distributedKeyId,
            ProviderType providerType,
            String modelGroup) {
        RequestLogEntity entity = new RequestLogEntity();
        entity.setRequestId(requestId);
        entity.setDistributedKeyId(distributedKeyId);
        entity.setDistributedKeyPrefix("gw-test");
        entity.setProtocol("openai");
        entity.setRequestPath("/v1/chat/completions");
        entity.setRequestedModel("gpt-4o");
        entity.setPublicModel("gpt-4o");
        entity.setResolvedModelKey("gpt-4o");
        entity.setModelGroup(modelGroup);
        entity.setProviderType(providerType);
        entity.setCredentialId(101L);
        entity.setSelectionSource("PREFIX_AFFINITY");
        entity.setStatus(status);
        return entity;
    }
}
