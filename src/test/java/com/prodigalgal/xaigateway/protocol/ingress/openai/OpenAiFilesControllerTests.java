package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileContent;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileResponse;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = OpenAiFilesController.class)
@Import(PermitAllSecurityTestConfig.class)
class OpenAiFilesControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private DistributedKeyAuthenticationService distributedKeyAuthenticationService;

    @MockitoBean
    private GatewayFileService gatewayFileService;

    @Test
    void shouldListFiles() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayFileService.listFiles(1L))
                .thenReturn(List.of(GatewayFileResponse.from(
                        "file-123",
                        "demo.txt",
                        "assistants",
                        12,
                        Instant.parse("2026-04-07T12:00:00Z"),
                        "processed"
                )));

        webTestClient.get()
                .uri("/v1/files")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo("file-123")
                .jsonPath("$[0].filename").isEqualTo("demo.txt");
    }

    @Test
    void shouldUploadFile() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayFileService.createFile(Mockito.eq(1L), Mockito.any(), Mockito.eq("assistants")))
                .thenReturn(reactor.core.publisher.Mono.just(GatewayFileResponse.from(
                        "file-456",
                        "demo.txt",
                        "assistants",
                        12,
                        Instant.parse("2026-04-07T12:00:00Z"),
                        "processed"
                )));

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("purpose", "assistants");
        bodyBuilder.part("file", new ByteArrayResource("hello".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "demo.txt";
            }
        }).header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE);

        webTestClient.post()
                .uri("/v1/files")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(bodyBuilder.build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("file-456")
                .jsonPath("$.purpose").isEqualTo("assistants");
    }

    @Test
    void shouldGetFileMetadata() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayFileService.getFile("file-789", 1L))
                .thenReturn(GatewayFileResponse.from(
                        "file-789",
                        "doc.pdf",
                        "assistants",
                        100,
                        Instant.parse("2026-04-07T12:00:00Z"),
                        "processed"
                ));

        webTestClient.get()
                .uri("/v1/files/file-789")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("file-789")
                .jsonPath("$.bytes").isEqualTo(100);
    }

    @Test
    void shouldGetFileContent() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayFileService.getFileContent("file-789", 1L))
                .thenReturn(new GatewayFileContent(
                        GatewayFileResponse.from(
                                "file-789",
                                "doc.pdf",
                                "assistants",
                                100,
                                Instant.parse("2026-04-07T12:00:00Z"),
                                "processed"
                        ),
                        "hello".getBytes(StandardCharsets.UTF_8),
                        MediaType.TEXT_PLAIN_VALUE
                ));

        webTestClient.get()
                .uri("/v1/files/file-789/content")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.TEXT_PLAIN)
                .expectBody(String.class).isEqualTo("hello");
    }
}
