package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileBindingResponse;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileBindingService;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
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

@WebFluxTest(controllers = GatewayFileBindingController.class)
@Import(PermitAllSecurityTestConfig.class)
class GatewayFileBindingControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private GatewayFileBindingService gatewayFileBindingService;

    @Test
    void shouldListBindings() {
        Mockito.when(gatewayFileBindingService.listBindings("file-123"))
                .thenReturn(List.of(bindingResponse()));

        webTestClient.get()
                .uri("/admin/files/file-123/bindings")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].externalFileId").isEqualTo("upstream-file-1");
    }

    @Test
    void shouldCreateBinding() {
        Mockito.when(gatewayFileBindingService.createBinding(
                        Mockito.eq("file-123"),
                        Mockito.eq(ProviderType.OPENAI_DIRECT),
                        Mockito.eq(101L),
                        Mockito.eq("upstream-file-1"),
                        Mockito.eq("doc.pdf")))
                .thenReturn(bindingResponse());

        webTestClient.post()
                .uri("/admin/files/file-123/bindings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "providerType":"OPENAI_DIRECT",
                          "credentialId":101,
                          "externalFileId":"upstream-file-1",
                          "externalFilename":"doc.pdf"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACTIVE")
                .jsonPath("$.providerType").isEqualTo("OPENAI_DIRECT");
    }

    private GatewayFileBindingResponse bindingResponse() {
        return new GatewayFileBindingResponse(
                1L,
                "file-123",
                ProviderType.OPENAI_DIRECT,
                101L,
                "upstream-file-1",
                "doc.pdf",
                "ACTIVE",
                Instant.parse("2026-04-07T12:00:00Z"),
                Instant.parse("2026-04-07T12:00:00Z"),
                Instant.parse("2026-04-07T12:00:00Z")
        );
    }
}
