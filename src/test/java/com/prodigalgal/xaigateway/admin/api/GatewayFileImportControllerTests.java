package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileBindingResponse;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileResponse;
import com.prodigalgal.xaigateway.gateway.core.file.UpstreamFileImportService;
import com.prodigalgal.xaigateway.gateway.core.file.UpstreamImportedFileResponse;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = GatewayFileImportController.class)
@Import(PermitAllSecurityTestConfig.class)
class GatewayFileImportControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private UpstreamFileImportService upstreamFileImportService;

    @Test
    void shouldImportExternalFileReference() {
        Mockito.when(upstreamFileImportService.importExternalReference(
                        1L,
                        ProviderType.OPENAI_DIRECT,
                        101L,
                        "upstream-file-1",
                        "doc.pdf",
                        "application/pdf",
                        "assistants"))
                .thenReturn(importResponse());

        webTestClient.post()
                .uri("/admin/files/import")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "distributedKeyId":1,
                          "providerType":"OPENAI_DIRECT",
                          "credentialId":101,
                          "externalFileId":"upstream-file-1",
                          "externalFilename":"doc.pdf",
                          "mimeType":"application/pdf",
                          "purpose":"assistants"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.file.id").isEqualTo("file-123")
                .jsonPath("$.binding.externalFileId").isEqualTo("upstream-file-1");
    }

    @Test
    void shouldSyncImportedFile() {
        Mockito.when(upstreamFileImportService.syncImportedFile("file-123"))
                .thenReturn(GatewayFileResponse.from(
                        "file-123",
                        "doc.pdf",
                        "assistants",
                        128,
                        Instant.parse("2026-04-07T12:00:00Z"),
                        "processed"
                ));

        webTestClient.post()
                .uri("/admin/files/file-123/sync")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("file-123")
                .jsonPath("$.status").isEqualTo("processed");
    }

    private UpstreamImportedFileResponse importResponse() {
        return new UpstreamImportedFileResponse(
                GatewayFileResponse.from(
                        "file-123",
                        "doc.pdf",
                        "assistants",
                        0,
                        Instant.parse("2026-04-07T12:00:00Z"),
                        "external_only"
                ),
                new GatewayFileBindingResponse(
                        1L,
                        "file-123",
                        ProviderType.OPENAI_DIRECT,
                        101L,
                        "upstream-file-1",
                        "doc.pdf",
                        "ACTIVE",
                        null,
                        Instant.parse("2026-04-07T12:00:00Z"),
                        Instant.parse("2026-04-07T12:00:00Z")
                )
        );
    }
}
