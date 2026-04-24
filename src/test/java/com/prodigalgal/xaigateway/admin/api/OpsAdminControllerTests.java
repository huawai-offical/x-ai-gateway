package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.OpsAlertService;
import com.prodigalgal.xaigateway.admin.application.OpsCapacityService;
import com.prodigalgal.xaigateway.admin.application.OpsDashboardService;
import com.prodigalgal.xaigateway.admin.application.OpsProbeJobService;
import com.prodigalgal.xaigateway.admin.application.OpsRuntimeLogService;
import com.prodigalgal.xaigateway.admin.application.OpsSloService;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = OpsAdminController.class)
@Import(PermitAllSecurityTestConfig.class)
class OpsAdminControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private OpsDashboardService opsDashboardService;

    @MockitoBean
    private OpsAlertService opsAlertService;

    @MockitoBean
    private OpsProbeJobService opsProbeJobService;

    @MockitoBean
    private OpsRuntimeLogService opsRuntimeLogService;

    @MockitoBean
    private OpsSloService opsSloService;

    @MockitoBean
    private OpsCapacityService opsCapacityService;

    @Test
    void shouldReturnSloSummary() {
        when(opsSloService.summary(any())).thenReturn(new OpsSloSummaryResponse(
                Instant.parse("2026-04-18T02:00:00Z"),
                new OpsSloSummaryResponse.SummaryCards(20, 2, 0.10D, 0.05D, 0.0D, 2.0D, "HIGH", 1L),
                List.of(),
                List.of(new OpsSloSummaryResponse.RiskItem(
                        "GATEWAY",
                        "global",
                        "gateway-availability",
                        2.0D,
                        0.0D,
                        "HIGH",
                        List.of("错误率抬升"),
                        List.of("优先观察 provider 失败分布")
                )),
                List.of("优先观察 provider 失败分布")
        ));

        webTestClient.get()
                .uri("/admin/ops/slo")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.summary.riskLevel").isEqualTo("HIGH")
                .jsonPath("$.risks[0].policyName").isEqualTo("gateway-availability");
    }

    @Test
    void shouldCreateSloPolicy() {
        when(opsSloService.savePolicy(isNull(), any())).thenReturn(new SloPolicyResponse(
                3L,
                "gateway-availability",
                "GATEWAY",
                "global",
                60,
                new BigDecimal("0.05"),
                new BigDecimal("1.00"),
                new BigDecimal("2.00"),
                true,
                "default gateway policy",
                Instant.parse("2026-04-18T02:00:00Z"),
                Instant.parse("2026-04-18T02:00:00Z")
        ));

        webTestClient.post()
                .uri("/admin/ops/slo/policies")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "policyName":"gateway-availability",
                          "scopeType":"GATEWAY",
                          "scopeRef":"global",
                          "windowMinutes":60,
                          "errorBudgetRatio":0.05,
                          "warningBurnRate":1.0,
                          "criticalBurnRate":2.0,
                          "enabled":true
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.policyName").isEqualTo("gateway-availability")
                .jsonPath("$.scopeType").isEqualTo("GATEWAY");
    }

    @Test
    void shouldReturnCapacitySummary() {
        when(opsCapacityService.summary(any())).thenReturn(new OpsCapacitySummaryResponse(
                Instant.parse("2026-04-18T02:00:00Z"),
                List.of(new OpsCapacitySummaryResponse.DistributedKeyPressure(
                        1L,
                        "main-key",
                        "sk-gw-main****",
                        "HIGH",
                        100_000L,
                        90_000L,
                        10_000L,
                        100,
                        85L,
                        15L,
                        10_000,
                        9_000L,
                        1_000L,
                        20,
                        18L,
                        2L,
                        List.of("budget usage is close to the current window limit")
                )),
                List.of(new AnalyticsOverviewResponse.BreakdownItem("OPENAI_DIRECT", 30, 900, 1200, 800)),
                List.of(),
                List.of(),
                List.of(),
                List.of("检查热点 credential 的冗余与配额")
        ));

        webTestClient.get()
                .uri("/admin/ops/capacity")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.distributedKeys[0].pressureLevel").isEqualTo("HIGH")
                .jsonPath("$.recommendedActions[0]").isEqualTo("检查热点 credential 的冗余与配额");
    }

    @Test
    void shouldCreateAndListSilences() {
        when(opsAlertService.listSilences()).thenReturn(List.of(new AlertSilenceResponse(
                7L,
                "mute-request-error-ratio",
                "REQUEST_ERROR_RATIO",
                "HIGH",
                "CREDENTIAL",
                "101",
                Instant.parse("2026-04-18T01:00:00Z"),
                Instant.parse("2026-04-18T03:00:00Z"),
                true,
                "夜间维护窗口",
                Instant.parse("2026-04-18T01:00:00Z"),
                Instant.parse("2026-04-18T01:00:00Z")
        )));
        when(opsAlertService.saveSilence(isNull(), any())).thenReturn(new AlertSilenceResponse(
                7L,
                "mute-request-error-ratio",
                "REQUEST_ERROR_RATIO",
                "HIGH",
                "CREDENTIAL",
                "101",
                Instant.parse("2026-04-18T01:00:00Z"),
                Instant.parse("2026-04-18T03:00:00Z"),
                true,
                "夜间维护窗口",
                Instant.parse("2026-04-18T01:00:00Z"),
                Instant.parse("2026-04-18T01:00:00Z")
        ));

        webTestClient.get()
                .uri("/admin/ops/alerts/silences")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].silenceName").isEqualTo("mute-request-error-ratio");

        webTestClient.post()
                .uri("/admin/ops/alerts/silences")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "silenceName":"mute-request-error-ratio",
                          "eventType":"REQUEST_ERROR_RATIO",
                          "severity":"HIGH",
                          "entityType":"CREDENTIAL",
                          "entityRef":"101",
                          "startsAt":"2026-04-18T01:00:00Z",
                          "endsAt":"2026-04-18T03:00:00Z",
                          "enabled":true,
                          "reason":"夜间维护窗口"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.silenceName").isEqualTo("mute-request-error-ratio")
                .jsonPath("$.eventType").isEqualTo("REQUEST_ERROR_RATIO");
    }

    @Test
    void shouldUpdateRunAndDeleteProbeJob() {
        when(opsProbeJobService.save(eq(7L), any())).thenReturn(new OpsScheduledProbeJobResponse(
                7L,
                "proxy-health",
                "NETWORK_PROXY",
                "1",
                60,
                true,
                null,
                null,
                null,
                Instant.parse("2026-04-18T02:00:00Z"),
                Instant.parse("2026-04-18T02:00:00Z")
        ));
        when(opsProbeJobService.trigger(7L)).thenReturn(new OpsScheduledProbeJobResponse(
                7L,
                "proxy-health",
                "NETWORK_PROXY",
                "1",
                60,
                true,
                Instant.parse("2026-04-18T02:10:00Z"),
                "SUCCESS",
                null,
                Instant.parse("2026-04-18T02:00:00Z"),
                Instant.parse("2026-04-18T02:10:00Z")
        ));

        webTestClient.put()
                .uri("/admin/ops/probes/7")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "jobName":"proxy-health",
                          "probeType":"NETWORK_PROXY",
                          "targetRef":"1",
                          "intervalSeconds":60,
                          "enabled":true
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobName").isEqualTo("proxy-health");

        webTestClient.post()
                .uri("/admin/ops/probes/7/run")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.lastStatus").isEqualTo("SUCCESS");

        webTestClient.delete()
                .uri("/admin/ops/probes/7")
                .exchange()
                .expectStatus().isOk();

        verify(opsProbeJobService).delete(7L);
    }
}
