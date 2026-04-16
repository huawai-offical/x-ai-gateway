package com.prodigalgal.xaigateway.protocol.ingress.google;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.canonical.GoogleNativeNonChatCanonicalRenderer;
import com.prodigalgal.xaigateway.gateway.core.canonical.NonChatCanonicalRenderService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceResponse;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionResult;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionService;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.util.List;
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

@WebFluxTest(controllers = GeminiEmbeddingsController.class)
@Import({
        PermitAllSecurityTestConfig.class,
        GeminiEmbeddingsRequestMapper.class,
        GeminiEmbeddingsEncoder.class,
        GeminiGenerateContentResourceEncoder.class,
        GeminiFilesEncoder.class,
        GeminiBatchesEncoder.class,
        GoogleNativeNonChatCanonicalRenderer.class,
        NonChatCanonicalRenderService.class
})
class GeminiEmbeddingsControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private DistributedKeyAuthenticationService distributedKeyAuthenticationService;

    @MockitoBean
    private GatewayResourceExecutionService gatewayResourceExecutionService;

    @Test
    void shouldEncodeSingleEmbeddingAsGoogleShape() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode response = mapper.createObjectNode();
        response.putArray("data").addObject().putArray("embedding").add(0.1).add(0.2);
        Mockito.when(gatewayResourceExecutionService.executeDetailedJson(Mockito.any(), Mockito.eq(1L), Mockito.eq("text-embedding-004")))
                .thenReturn(GatewayResourceExecutionResult.json(
                        ResponseEntity.ok(response),
                        new CanonicalResourceResponse(null, null, null, null, null, null, List.of(), List.of(), response, null, java.util.Map.of())
                ));

        webTestClient.post()
                .uri("/v1beta/models/text-embedding-004:embedContent")
                .header("x-goog-api-key", "sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "content": {
                            "parts": [{"text":"hello"}]
                          }
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.embedding.values[0]").isEqualTo(0.1)
                .jsonPath("$.embedding.values[1]").isEqualTo(0.2);
    }

    @Test
    void shouldEncodeBatchEmbeddingsAsGoogleShape() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode response = mapper.createObjectNode();
        var data = response.putArray("data");
        data.addObject().putArray("embedding").add(0.1).add(0.2);
        data.addObject().putArray("embedding").add(0.3).add(0.4);
        Mockito.when(gatewayResourceExecutionService.executeDetailedJson(Mockito.any(), Mockito.eq(1L), Mockito.eq("text-embedding-004")))
                .thenReturn(GatewayResourceExecutionResult.json(
                        ResponseEntity.ok(response),
                        new CanonicalResourceResponse(null, null, null, null, null, null, List.of(), List.of(), response, null, java.util.Map.of())
                ));

        webTestClient.post()
                .uri("/v1beta/models/text-embedding-004:batchEmbedContents")
                .header("x-goog-api-key", "sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "requests": [
                            {"content":{"parts":[{"text":"hello"}]}},
                            {"content":{"parts":[{"text":"world"}]}}
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.embeddings[0].values[0]").isEqualTo(0.1)
                .jsonPath("$.embeddings[1].values[1]").isEqualTo(0.4);
    }
}
