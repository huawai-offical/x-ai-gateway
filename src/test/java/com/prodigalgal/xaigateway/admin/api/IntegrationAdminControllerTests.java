package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.IntegrationAdminService;
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

@WebFluxTest(controllers = IntegrationAdminController.class)
@Import(PermitAllSecurityTestConfig.class)
class IntegrationAdminControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private IntegrationAdminService integrationAdminService;

    @Test
    void shouldCreateWebhookAndListDeliveries() {
        Mockito.when(integrationAdminService.saveWebhook(Mockito.isNull(), Mockito.any()))
                .thenReturn(new WebhookEndpointResponse(1L, "primary", "https://example.com/hook", "HMAC_SHA256", 5000, true, "fp", Instant.now(), Instant.now()));
        Mockito.when(integrationAdminService.listDeliveries(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(List.of(new OutboundDeliveryResponse(
                        8L,
                        "evt-1",
                        "ALERT_OPENED",
                        2L,
                        "CREDENTIAL",
                        "101",
                        "req-1",
                        null,
                        null,
                        "SUCCEEDED",
                        1,
                        null,
                        null,
                        200,
                        "ok",
                        "{}",
                        Instant.now(),
                        Instant.now(),
                        Instant.now(),
                        Instant.now()
                )));

        webTestClient.post()
                .uri("/admin/integrations/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "endpointName":"primary",
                          "endpointUrl":"https://example.com/hook",
                          "secret":"token",
                          "signingMode":"HMAC_SHA256",
                          "timeoutMs":5000,
                          "enabled":true
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.endpointName").isEqualTo("primary");

        webTestClient.get()
                .uri("/admin/integrations/deliveries?eventType=ALERT_OPENED")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].deliveryStatus").isEqualTo("SUCCEEDED");
    }

    @Test
    void shouldReplayDelivery() {
        Mockito.when(integrationAdminService.replayDelivery(9L))
                .thenReturn(new OutboundDeliveryResponse(
                        9L,
                        "evt-2",
                        "UPGRADE_FAILED",
                        3L,
                        "CHANGE_PLAN",
                        "12",
                        null,
                        null,
                        null,
                        "SUCCEEDED",
                        2,
                        null,
                        null,
                        202,
                        "resent",
                        "{}",
                        Instant.now(),
                        Instant.now(),
                        Instant.now(),
                        Instant.now()
                ));

        webTestClient.post()
                .uri("/admin/integrations/deliveries/9/replay")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.responseSummary").isEqualTo("resent");
    }

    @Test
    void shouldDeleteIntegrationResources() {
        webTestClient.delete()
                .uri("/admin/integrations/webhooks/1")
                .exchange()
                .expectStatus().isOk();
        Mockito.verify(integrationAdminService).deleteWebhook(1L);

        webTestClient.delete()
                .uri("/admin/integrations/channels/2")
                .exchange()
                .expectStatus().isOk();
        Mockito.verify(integrationAdminService).deleteChannel(2L);

        webTestClient.delete()
                .uri("/admin/integrations/runbooks/3")
                .exchange()
                .expectStatus().isOk();
        Mockito.verify(integrationAdminService).deleteRunbook(3L);

        webTestClient.delete()
                .uri("/admin/integrations/subscriptions/4")
                .exchange()
                .expectStatus().isOk();
        Mockito.verify(integrationAdminService).deleteSubscription(4L);
    }
}
