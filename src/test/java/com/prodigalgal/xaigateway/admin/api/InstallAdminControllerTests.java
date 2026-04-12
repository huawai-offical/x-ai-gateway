package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.PlatformOperationsService;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;

@WebFluxTest(controllers = InstallAdminController.class)
@Import(PermitAllSecurityTestConfig.class)
class InstallAdminControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private PlatformOperationsService platformOperationsService;

    @Test
    void shouldBootstrapInstallState() {
        Mockito.when(platformOperationsService.bootstrap(Mockito.any()))
                .thenReturn(new InstallationStateResponse(1L, "READY", null, true, Instant.now(), "{}", Instant.now(), Instant.now()));

        webTestClient.post()
                .uri("/admin/install/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "adminEmail":"admin@example.com",
                          "environmentName":"local"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("READY");
    }
}
