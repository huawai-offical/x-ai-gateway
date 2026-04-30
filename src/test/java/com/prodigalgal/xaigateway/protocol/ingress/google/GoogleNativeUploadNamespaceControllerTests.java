package com.prodigalgal.xaigateway.protocol.ingress.google;

import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@WebFluxTest(controllers = GoogleNativeUploadNamespaceController.class)
@Import(PermitAllSecurityTestConfig.class)
class GoogleNativeUploadNamespaceControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private GeminiFilesController filesController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRouteGoogleUploadNamespaceToGovernedFileController() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("name", "files/demo-123");
        Mockito.when(filesController.upload(
                        Mockito.eq("sk-gw-test.secret"),
                        Mockito.isNull(),
                        Mockito.any(FilePart.class),
                        Mockito.eq("{\"displayName\":\"demo.txt\"}")))
                .thenReturn(Mono.just(response));

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("metadata", "{\"displayName\":\"demo.txt\"}");
        bodyBuilder.part("file", new ByteArrayResource("hello".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "demo.txt";
            }
        }).header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE);

        webTestClient.post()
                .uri("/google/upload/v1beta/files")
                .header("x-goog-api-key", "sk-gw-test.secret")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(bodyBuilder.build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("files/demo-123");

        Mockito.verify(filesController).upload(
                Mockito.eq("sk-gw-test.secret"),
                Mockito.isNull(),
                Mockito.any(FilePart.class),
                Mockito.eq("{\"displayName\":\"demo.txt\"}")
        );
    }

    @Test
    void shouldReturnExplicitUnsupportedForUnknownGoogleUploadPath() {
        webTestClient.get()
                .uri("/google/upload/v1beta/unknown")
                .exchange()
                .expectStatus().isEqualTo(501)
                .expectBody()
                .jsonPath("$.error").isEqualTo("NATIVE_PATH_UNSUPPORTED");
    }
}
