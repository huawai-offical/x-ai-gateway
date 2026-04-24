package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.UserSubscriptionAdminService;
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

@WebFluxTest(controllers = UserSubscriptionAdminController.class)
@Import(PermitAllSecurityTestConfig.class)
class UserSubscriptionAdminControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private UserSubscriptionAdminService userSubscriptionAdminService;

    @Test
    void shouldListGetAndCreateSubscription() {
        UserSubscriptionResponse response = subscriptionResponse(8L, "ACTIVE");
        Mockito.when(userSubscriptionAdminService.list("ACTIVE", 3L, 5L)).thenReturn(List.of(response));
        Mockito.when(userSubscriptionAdminService.get(8L)).thenReturn(response);
        Mockito.when(userSubscriptionAdminService.create(Mockito.any())).thenReturn(response);

        webTestClient.get()
                .uri("/admin/subscriptions?status=ACTIVE&userId=3&planId=5")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].userEmail").isEqualTo("alpha@example.com")
                .jsonPath("$[0].planName").isEqualTo("starter");

        webTestClient.get()
                .uri("/admin/subscriptions/8")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(8);

        webTestClient.post()
                .uri("/admin/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "userId":3,
                          "planId":5,
                          "status":"ACTIVE"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACTIVE");
    }

    @Test
    void shouldUpdateAndDeleteSubscription() {
        UserSubscriptionResponse response = subscriptionResponse(8L, "PAUSED");
        Mockito.when(userSubscriptionAdminService.update(Mockito.eq(8L), Mockito.any())).thenReturn(response);

        webTestClient.put()
                .uri("/admin/subscriptions/8")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "userId":3,
                          "planId":5,
                          "status":"PAUSED"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PAUSED");

        webTestClient.delete()
                .uri("/admin/subscriptions/8")
                .exchange()
                .expectStatus().isOk();
        Mockito.verify(userSubscriptionAdminService).delete(8L);
    }

    private UserSubscriptionResponse subscriptionResponse(Long id, String status) {
        Instant now = Instant.now();
        return new UserSubscriptionResponse(
                id,
                3L,
                "alpha@example.com",
                5L,
                "starter",
                status,
                now,
                now.plusSeconds(86_400),
                false,
                null,
                now,
                now
        );
    }
}
