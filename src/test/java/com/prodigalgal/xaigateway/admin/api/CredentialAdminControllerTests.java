package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.CredentialAdminService;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialAuthKind;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = CredentialAdminController.class)
@Import(PermitAllSecurityTestConfig.class)
class CredentialAdminControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CredentialAdminService credentialAdminService;

    @Test
    void shouldListAndGetCredential() {
        CredentialResponse response = credentialResponse(7L, "OpenAI Primary");
        Mockito.when(credentialAdminService.list()).thenReturn(List.of(response));
        Mockito.when(credentialAdminService.get(7L)).thenReturn(response);

        webTestClient.get()
                .uri("/admin/credentials")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].credentialName").isEqualTo("OpenAI Primary");

        webTestClient.get()
                .uri("/admin/credentials/7")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(7)
                .jsonPath("$.providerType").isEqualTo("OPENAI_DIRECT");
    }

    @Test
    void shouldCreateAndToggleCredential() {
        Mockito.when(credentialAdminService.create(Mockito.any()))
                .thenReturn(credentialResponse(11L, "Gemini Key"));
        Mockito.when(credentialAdminService.toggle(11L, false))
                .thenReturn(new CredentialResponse(
                        11L,
                        "Gemini Key",
                        ProviderType.GEMINI_DIRECT,
                        "https://generativelanguage.googleapis.com",
                        CredentialAuthKind.API_KEY,
                        "fp",
                        Map.of(),
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        21L,
                        Instant.now(),
                        Instant.now()
                ));

        webTestClient.post()
                .uri("/admin/credentials")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "credentialName":"Gemini Key",
                          "providerType":"GEMINI_DIRECT",
                          "baseUrl":"https://generativelanguage.googleapis.com",
                          "authKind":"API_KEY",
                          "secret":"token"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.credentialName").isEqualTo("Gemini Key");

        webTestClient.post()
                .uri("/admin/credentials/11/status?active=false")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.active").isEqualTo(false);
    }

    private CredentialResponse credentialResponse(Long id, String name) {
        return new CredentialResponse(
                id,
                name,
                ProviderType.OPENAI_DIRECT,
                "https://api.openai.com",
                CredentialAuthKind.API_KEY,
                "fp",
                Map.of("tenant", "prod"),
                true,
                null,
                null,
                null,
                null,
                Instant.now(),
                null,
                null,
                15L,
                Instant.now(),
                Instant.now()
        );
    }
}
