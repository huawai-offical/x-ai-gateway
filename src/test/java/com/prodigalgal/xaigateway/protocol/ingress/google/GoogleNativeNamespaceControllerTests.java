package com.prodigalgal.xaigateway.protocol.ingress.google;

import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@WebFluxTest(controllers = GoogleNativeNamespaceController.class)
@Import(PermitAllSecurityTestConfig.class)
class GoogleNativeNamespaceControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private GeminiGenerateContentController generateContentController;

    @MockitoBean
    private GeminiEmbeddingsController embeddingsController;

    @MockitoBean
    private GeminiFilesController filesController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRouteGoogleNamespaceEmbeddingToGovernedController() {
        ObjectNode response = objectMapper.createObjectNode();
        response.putObject("embedding").putArray("values").add(0.1).add(0.2);
        Mockito.when(embeddingsController.embedContent(
                        Mockito.eq("text-embedding-004"),
                        Mockito.eq("sk-gw-test.secret"),
                        Mockito.isNull(),
                        Mockito.any()))
                .thenReturn(ResponseEntity.ok(response));

        webTestClient.post()
                .uri("/google/v1beta/models/text-embedding-004:embedContent")
                .header("x-goog-api-key", "sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"content\":{\"parts\":[{\"text\":\"hello\"}]}}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.embedding.values[0]").isEqualTo(0.1);

        Mockito.verify(embeddingsController).embedContent(
                Mockito.eq("text-embedding-004"),
                Mockito.eq("sk-gw-test.secret"),
                Mockito.isNull(),
                Mockito.any()
        );
    }

    @Test
    void shouldRouteGoogleNamespaceFileListToGovernedController() {
        ObjectNode response = objectMapper.createObjectNode();
        response.putArray("files").addObject().put("name", "files/demo-123");
        Mockito.when(filesController.list("sk-gw-test.secret", null)).thenReturn(response);

        webTestClient.get()
                .uri("/google/v1beta/files")
                .header("x-goog-api-key", "sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.files[0].name").isEqualTo("files/demo-123");

        Mockito.verify(filesController).list("sk-gw-test.secret", null);
    }

    @Test
    void shouldReturnExplicitUnsupportedForUnknownGoogleNamespacePath() {
        webTestClient.get()
                .uri("/google/v1beta/unknown/native/path")
                .exchange()
                .expectStatus().isEqualTo(501)
                .expectBody()
                .jsonPath("$.error").isEqualTo("NATIVE_PATH_UNSUPPORTED");
    }

}
