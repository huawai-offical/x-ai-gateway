package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.NetworkGovernanceService;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = ProxyAdminController.class)
@Import(PermitAllSecurityTestConfig.class)
class ProxyAdminControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private NetworkGovernanceService networkGovernanceService;

    @Test
    void shouldCreateProxy() {
        Mockito.when(networkGovernanceService.saveProxy(Mockito.isNull(), Mockito.any()))
                .thenReturn(new ProxyResponse(1L, "proxy-a", "https://proxy.example.com:443", true, null, null, null, null, null, null, null));

        webTestClient.post()
                .uri("/admin/network/proxies")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "proxyName":"proxy-a",
                          "proxyUrl":"https://proxy.example.com:443"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.proxyName").isEqualTo("proxy-a");
    }
}
