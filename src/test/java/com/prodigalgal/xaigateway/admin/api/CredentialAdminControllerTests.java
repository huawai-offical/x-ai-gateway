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
                .thenReturn(credentialResponse(
                        11L,
                        "Gemini Key",
                        false,
                        ProviderType.GEMINI_DIRECT,
                        "https://generativelanguage.googleapis.com"
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
        return credentialResponse(id, name, true, ProviderType.OPENAI_DIRECT, "https://api.openai.com");
    }

    private CredentialResponse credentialResponse(
            Long id,
            String name,
            boolean active,
            ProviderType providerType,
            String baseUrl) {
        Instant now = Instant.now();
        return new CredentialResponse(
                id,
                name,
                providerType,
                baseUrl,
                CredentialAuthKind.API_KEY,
                List.of("gpt-4o"),
                "fp",
                Map.of("tenant", "prod"),
                active,
                null,
                null,
                null,
                null,
                now,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0D,
                0D,
                0L,
                0L,
                0D,
                0L,
                0L,
                0D,
                null,
                null,
                null,
                null,
                null,
                15L,
                15L,
                "default",
                now,
                now
        );
    }
}
