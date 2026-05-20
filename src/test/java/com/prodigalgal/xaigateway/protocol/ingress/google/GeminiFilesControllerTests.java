package com.prodigalgal.xaigateway.protocol.ingress.google;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.canonical.GoogleNativeNonChatCanonicalRenderer;
import com.prodigalgal.xaigateway.gateway.core.canonical.NonChatCanonicalRenderService;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileResponse;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
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
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = GeminiFilesController.class)
@Import({
        PermitAllSecurityTestConfig.class,
        GeminiEmbeddingsEncoder.class,
        GeminiGenerateContentResourceEncoder.class,
        GeminiFilesEncoder.class,
        GoogleNativeNonChatCanonicalRenderer.class,
        NonChatCanonicalRenderService.class
})
class GeminiFilesControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private DistributedKeyAuthenticationService distributedKeyAuthenticationService;

    @MockitoBean
    private GatewayFileService gatewayFileService;

    @Test
    void shouldUploadGoogleNativeFile() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayFileService.createGoogleNativeFile(Mockito.eq(1L), Mockito.any(), Mockito.isNull(), Mockito.eq("demo.txt")))
                .thenReturn(Mono.just(view("file-123", "files/demo-123")));

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("metadata", "{\"displayName\":\"demo.txt\"}");
        bodyBuilder.part("file", new ByteArrayResource("hello".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "demo.txt";
            }
        }).header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE);

        webTestClient.post()
                .uri("/upload/v1beta/files")
                .header("x-goog-api-key", "sk-gw-test.secret")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(bodyBuilder.build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("files/demo-123")
                .jsonPath("$.displayName").isEqualTo("demo.txt");
    }

    @Test
    void shouldListGoogleNativeFiles() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayFileService.listGoogleNativeFiles(1L))
                .thenReturn(List.of(view("file-123", "files/demo-123")));

        webTestClient.get()
                .uri("/v1beta/files")
                .header("x-goog-api-key", "sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.files[0].name").isEqualTo("files/demo-123");
    }

    @Test
    void shouldGetAndDeleteGoogleNativeFile() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayFileService.getGoogleNativeFile("files/demo-123", 1L))
                .thenReturn(view("file-123", "files/demo-123"));

        webTestClient.get()
                .uri("/v1beta/files/demo-123")
                .header("x-goog-api-key", "sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("files/demo-123");

        webTestClient.delete()
                .uri("/v1beta/files/demo-123")
                .header("x-goog-api-key", "sk-gw-test.secret")
                .exchange()
                .expectStatus().isNoContent();

        Mockito.verify(gatewayFileService).deleteGoogleNativeFile("files/demo-123", 1L);
    }

    private GatewayFileService.GoogleNativeFileView view(String fileKey, String externalFileId) {
        return new GatewayFileService.GoogleNativeFileView(
                GatewayFileResponse.from(fileKey, "demo.txt", null, 12, Instant.parse("2026-04-16T02:00:00Z"), "processed"),
                externalFileId,
                "demo.txt",
                "text/plain",
                Instant.parse("2026-04-16T02:00:00Z"),
                Instant.parse("2026-04-16T02:05:00Z"),
                "abc",
                101L,
                201L,
                "processed"
        );
    }
}
