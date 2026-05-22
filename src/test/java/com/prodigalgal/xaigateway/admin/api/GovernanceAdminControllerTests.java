package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.GovernanceAdminService;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceActionType;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernancePolicyMode;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceRecoveryMode;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceTargetType;
import com.prodigalgal.xaigateway.gateway.core.governance.QuarantineStatus;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = GovernanceAdminController.class)
@Import(PermitAllSecurityTestConfig.class)
class GovernanceAdminControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private GovernanceAdminService governanceAdminService;

    @Test
    void shouldCreateRouteGuard() {
        Mockito.when(governanceAdminService.saveRouteGuard(Mockito.isNull(), Mockito.any()))
                .thenReturn(new RouteGuardPolicyResponse(
                        1L,
                        "guard-openai",
                        GovernanceTargetType.PROVIDER_TYPE,
                        ProviderType.OPENAI_DIRECT,
                        null,
                        null,
                        null,
                        null,
                        GovernancePolicyMode.ENFORCE,
                        GovernanceActionType.QUARANTINE,
                        300,
                        Instant.parse("2026-04-17T08:10:00Z"),
                        100,
                        true,
                        "sample",
                        "{\"maxAttempts\":2}",
                        "{\"order\":[\"same_site\"]}",
                        "{\"failureThreshold\":3}",
                        "{\"rpm\":60}",
                        Instant.parse("2026-04-17T08:00:00Z"),
                        Instant.parse("2026-04-17T08:05:00Z")
                ));

        webTestClient.post()
                .uri("/admin/ops/policies/route-guards")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "policyName":"guard-openai",
                          "targetType":"PROVIDER_TYPE",
                          "providerType":"OPENAI_DIRECT",
                          "policyMode":"ENFORCE",
                          "actionType":"QUARANTINE",
                          "ttlSeconds":300,
                          "priority":100,
                          "enabled":true
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.policyName").isEqualTo("guard-openai")
                .jsonPath("$.actionType").isEqualTo("QUARANTINE")
                .jsonPath("$.retryPolicy").isEqualTo("{\"maxAttempts\":2}");
    }

    @Test
    void shouldReturnRoutingRuntimePlan() {
        Mockito.when(governanceAdminService.routingRuntimePlan())
                .thenReturn(new RoutingPolicyRuntimePlanResponse(
                        2,
                        true,
                        List.of("score", "priority"),
                        true,
                        3,
                        true,
                        60,
                        List.of(11L),
                        List.of()
                ));

        webTestClient.get()
                .uri("/admin/ops/policies/routing-runtime-plan")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.maxAttempts").isEqualTo(2)
                .jsonPath("$.fallbackEnabled").isEqualTo(true)
                .jsonPath("$.requestsPerMinute").isEqualTo(60);
    }

    @Test
    void shouldReturnQuarantines() {
        Mockito.when(governanceAdminService.listQuarantines("ACTIVE"))
                .thenReturn(List.of(new QuarantineRecordResponse(
                        1L,
                        GovernanceTargetType.CREDENTIAL,
                        ProviderType.GEMINI_DIRECT,
                        null,
                        101L,
                        null,
                        null,
                        2L,
                        3L,
                        GovernanceActionType.COOLDOWN,
                        GovernanceRecoveryMode.AUTO_RESUME,
                        "告警事件触发自动治理动作",
                        QuarantineStatus.ACTIVE,
                        Instant.parse("2026-04-17T08:00:00Z"),
                        Instant.parse("2026-04-17T08:05:00Z"),
                        null,
                        null,
                        Instant.parse("2026-04-17T08:00:00Z"),
                        Instant.parse("2026-04-17T08:00:00Z")
                )));

        webTestClient.get()
                .uri("/admin/ops/quarantines?status=ACTIVE")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].credentialId").isEqualTo(101)
                .jsonPath("$[0].status").isEqualTo("ACTIVE");
    }

    @Test
    void shouldReleaseQuarantine() {
        Mockito.when(governanceAdminService.releaseQuarantine(Mockito.eq(9L), Mockito.eq("manual-release")))
                .thenReturn(new QuarantineRecordResponse(
                        9L,
                        GovernanceTargetType.CREDENTIAL,
                        ProviderType.OPENAI_DIRECT,
                        null,
                        101L,
                        null,
                        null,
                        null,
                        null,
                        GovernanceActionType.QUARANTINE,
                        GovernanceRecoveryMode.MANUAL_RESUME,
                        "manual block",
                        QuarantineStatus.RELEASED,
                        Instant.parse("2026-04-17T08:00:00Z"),
                        null,
                        Instant.parse("2026-04-17T08:10:00Z"),
                        "manual-release",
                        Instant.parse("2026-04-17T08:00:00Z"),
                        Instant.parse("2026-04-17T08:10:00Z")
                ));

        webTestClient.post()
                .uri("/admin/ops/quarantines/9/release")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"releaseReason":"manual-release"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("RELEASED")
                .jsonPath("$.releaseReason").isEqualTo("manual-release");
    }

    @Test
    void shouldDeleteRouteGuard() {
        webTestClient.delete()
                .uri("/admin/ops/policies/route-guards/7")
                .exchange()
                .expectStatus().isOk();

        Mockito.verify(governanceAdminService).deleteRouteGuard(7L);
    }

    @Test
    void shouldDeleteAutoAction() {
        webTestClient.delete()
                .uri("/admin/ops/policies/auto-actions/8")
                .exchange()
                .expectStatus().isOk();

        Mockito.verify(governanceAdminService).deleteAutoAction(8L);
    }

    @Test
    void shouldReturnHealthScores() {
        Mockito.when(governanceAdminService.listHealthScores())
                .thenReturn(new GovernanceHealthScoreResponse(
                        List.of(new SiteHealthScoreResponse(
                                1L,
                                "openai-main",
                                "OpenAI 主站",
                                com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily.OPENAI,
                                com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind.OPENAI_DIRECT,
                                true,
                                63,
                                "DEGRADED",
                                "站点下存在被治理阻断或冷却的凭证。",
                                2,
                                1,
                                Instant.parse("2026-04-17T08:10:00Z")
                        )),
                        List.of(new CredentialHealthScoreResponse(
                                "API_KEY",
                                101L,
                                101L,
                                null,
                                "openai-primary",
                                "openai-primary",
                                ProviderType.OPENAI_DIRECT,
                                1L,
                                null,
                                true,
                                null,
                                100,
                                "HEALTHY",
                                null,
                                null,
                                Instant.parse("2026-04-17T08:00:00Z"),
                                List.of(),
                                List.of()
                        ))
                ));

        webTestClient.get()
                .uri("/admin/ops/health-scores")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.sites[0].displayName").isEqualTo("OpenAI 主站")
                .jsonPath("$.credentials[0].credentialName").isEqualTo("openai-primary");
    }
}
