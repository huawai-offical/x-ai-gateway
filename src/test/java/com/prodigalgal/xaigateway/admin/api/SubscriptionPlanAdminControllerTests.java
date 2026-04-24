package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.SubscriptionPlanAdminService;
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

@WebFluxTest(controllers = SubscriptionPlanAdminController.class)
@Import(PermitAllSecurityTestConfig.class)
class SubscriptionPlanAdminControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private SubscriptionPlanAdminService subscriptionPlanAdminService;

    @Test
    void shouldListGetAndCreatePlan() {
        SubscriptionPlanResponse response = planResponse(5L, "starter", true);
        Mockito.when(subscriptionPlanAdminService.list("start", true)).thenReturn(List.of(response));
        Mockito.when(subscriptionPlanAdminService.get(5L)).thenReturn(response);
        Mockito.when(subscriptionPlanAdminService.create(Mockito.any())).thenReturn(response);

        webTestClient.get()
                .uri("/admin/plans?keyword=start&active=true")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].planName").isEqualTo("starter")
                .jsonPath("$[0].defaultDurationDays").isEqualTo(30);

        webTestClient.get()
                .uri("/admin/plans/5")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.rpmLimit").isEqualTo(60);

        webTestClient.post()
                .uri("/admin/plans")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "planName":"starter",
                          "active":true,
                          "defaultDurationDays":30
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(5);
    }

    @Test
    void shouldUpdateAndDeletePlan() {
        SubscriptionPlanResponse response = planResponse(7L, "pro", false);
        Mockito.when(subscriptionPlanAdminService.update(Mockito.eq(7L), Mockito.any())).thenReturn(response);

        webTestClient.put()
                .uri("/admin/plans/7")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "planName":"pro",
                          "active":false,
                          "defaultDurationDays":90
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.active").isEqualTo(false);

        webTestClient.delete()
                .uri("/admin/plans/7")
                .exchange()
                .expectStatus().isOk();
        Mockito.verify(subscriptionPlanAdminService).delete(7L);
    }

    private SubscriptionPlanResponse planResponse(Long id, String planName, boolean active) {
        Instant now = Instant.now();
        return new SubscriptionPlanResponse(
                id,
                planName,
                "demo",
                active,
                30,
                3,
                60,
                120000,
                2,
                1_000_000L,
                4L,
                now,
                now
        );
    }
}
