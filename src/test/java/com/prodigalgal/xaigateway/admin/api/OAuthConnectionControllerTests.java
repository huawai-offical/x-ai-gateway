package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.OAuthConnectionService;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = OAuthConnectionController.class)
@Import(PermitAllSecurityTestConfig.class)
class OAuthConnectionControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private OAuthConnectionService oauthConnectionService;

    @Test
    void shouldStartOAuthFlow() {
        Mockito.when(oauthConnectionService.start(Mockito.any(), Mockito.eq(1L), Mockito.any()))
                .thenReturn(new OauthStartResponse("oas_1", "https://auth.example.com"));

        webTestClient.post()
                .uri("/admin/oauth/openai_oauth/start")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "poolId":1
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.authorizationUrl").isEqualTo("https://auth.example.com");
    }
}
