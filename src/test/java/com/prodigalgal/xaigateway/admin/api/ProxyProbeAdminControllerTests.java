package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.NetworkGovernanceService;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.List;

@WebFluxTest(controllers = ProxyProbeAdminController.class)
@Import(PermitAllSecurityTestConfig.class)
class ProxyProbeAdminControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private NetworkGovernanceService networkGovernanceService;

    @Test
    void shouldListGlobalProbeResults() {
        Mockito.when(networkGovernanceService.listProbeResults())
                .thenReturn(List.of(new ProxyProbeResultResponse(
                        10L,
                        1L,
                        "SUCCESS",
                        42L,
                        "https://proxy.example.com",
                        null,
                        Instant.parse("2026-04-18T02:00:00Z")
                )));

        webTestClient.get()
                .uri("/admin/network/probes")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].proxyId").isEqualTo(1)
                .jsonPath("$[0].status").isEqualTo("SUCCESS");
    }
}
