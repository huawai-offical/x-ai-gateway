package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.databind.node.JsonNodeFactory;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileResponse;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionService;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
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
    private GatewayResourceExecutionService gatewayResourceExecutionService;

    @Test
    void shouldListFiles() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayResourceExecutionService.listFiles(1L))
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
                .jsonPath("$.object").isEqualTo("list")
                .jsonPath("$.data[0].id").isEqualTo("file-123")
                .jsonPath("$.data[0].filename").isEqualTo("demo.txt");
    }

    @Test
    void shouldUploadFile() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayResourceExecutionService.createFile(Mockito.eq("sk-gw-test"), Mockito.eq(1L), Mockito.eq("assistants"), Mockito.any()))
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
        Mockito.when(gatewayResourceExecutionService.getFile("file-789", 1L))
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
        Mockito.when(gatewayResourceExecutionService.getFileContent("file-789", 1L))
                .thenReturn(org.springframework.http.ResponseEntity.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("hello".getBytes(StandardCharsets.UTF_8)));

        webTestClient.get()
                .uri("/v1/files/file-789/content")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.TEXT_PLAIN)
                .expectBody(String.class).isEqualTo("hello");
    }

    @Test
    void shouldDeleteFileAndReturnDeleteObject() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayResourceExecutionService.deleteFile("sk-gw-test", 1L, "file-789"))
                .thenReturn(JsonNodeFactory.instance.objectNode()
                        .put("id", "file-789")
                        .put("object", "file.deleted")
                        .put("deleted", true));

        webTestClient.delete()
                .uri("/v1/files/file-789")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("file-789")
                .jsonPath("$.object").isEqualTo("file.deleted")
                .jsonPath("$.deleted").isEqualTo(true);
    }
}
