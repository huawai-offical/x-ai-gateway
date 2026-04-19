package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.MaintenanceWindowService;
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

@WebFluxTest(controllers = OperationsMaintenanceWindowAdminController.class)
@Import(PermitAllSecurityTestConfig.class)
class OperationsMaintenanceWindowAdminControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private MaintenanceWindowService maintenanceWindowService;

    @Test
    void shouldCreateAndListMaintenanceWindow() {
        Mockito.when(maintenanceWindowService.save(Mockito.isNull(), Mockito.any()))
                .thenReturn(sampleResponse());
        Mockito.when(maintenanceWindowService.list(Mockito.isNull()))
                .thenReturn(List.of(sampleResponse()));

        webTestClient.post()
                .uri("/admin/operations/maintenance-windows")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "windowName":"夜间发布窗口",
                          "startsAt":"2026-04-18T14:00:00Z",
                          "endsAt":"2026-04-18T16:00:00Z",
                          "enabled":true
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.windowName").isEqualTo("夜间发布窗口");

        webTestClient.get()
                .uri("/admin/operations/maintenance-windows")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].activeNow").isEqualTo(true);
    }

    private MaintenanceWindowResponse sampleResponse() {
        return new MaintenanceWindowResponse(
                2L,
                "夜间发布窗口",
                null,
                null,
                Instant.parse("2026-04-18T14:00:00Z"),
                Instant.parse("2026-04-18T16:00:00Z"),
                true,
                "night window",
                true,
                Instant.now(),
                Instant.now()
        );
    }
}
