package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.PlatformChangePlanService;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.List;

@WebFluxTest(controllers = OperationsChangePlanAdminController.class)
@Import(PermitAllSecurityTestConfig.class)
class OperationsChangePlanAdminControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private PlatformChangePlanService platformChangePlanService;

    @Test
    void shouldCreateChangePlan() {
        Mockito.when(platformChangePlanService.create(Mockito.any()))
                .thenReturn(sampleResponse("PENDING_APPROVAL"));

        webTestClient.post()
                .uri("/admin/operations/change-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "planName":"upgrade-main",
                          "planType":"UPGRADE",
                          "executionClass":"MANUAL",
                          "releaseArtifactId":8,
                          "requestedBy":"ops"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.planName").isEqualTo("upgrade-main")
                .jsonPath("$.status").isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void shouldApproveAndExecuteChangePlan() {
        Mockito.when(platformChangePlanService.approve(Mockito.eq(12L), Mockito.any()))
                .thenReturn(sampleResponse("APPROVED"));
        Mockito.when(platformChangePlanService.execute(Mockito.eq(12L), Mockito.any()))
                .thenReturn(sampleResponse("COMPLETED"));

        webTestClient.post()
                .uri("/admin/operations/change-plans/12/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "approvedBy":"ops",
                          "reason":"窗口已确认"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("APPROVED");

        webTestClient.post()
                .uri("/admin/operations/change-plans/12/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "actor":"ops"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("COMPLETED");
    }

    @Test
    void shouldListChangePlans() {
        Mockito.when(platformChangePlanService.list()).thenReturn(List.of(sampleResponse("READY")));

        webTestClient.get()
                .uri("/admin/operations/change-plans")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].planName").isEqualTo("upgrade-main");
    }

    private ChangePlanResponse sampleResponse(String status) {
        return new ChangePlanResponse(
                12L,
                "upgrade-main",
                "UPGRADE",
                "MANUAL",
                status,
                8L,
                3L,
                2L,
                21L,
                "ops",
                "ops",
                false,
                null,
                null,
                "LOW",
                "PRECHECK",
                "sample",
                List.of(new ChangePlanPreflightCheckResponse("releaseArtifact", "OK", true, "ok")),
                List.of(),
                List.of(),
                new RollbackPlaybookResponse(21L, 3L, 5L, "ACTIVE", "[]", null, null, Instant.now(), Instant.now()),
                Instant.now(),
                Instant.now()
        );
    }
}
