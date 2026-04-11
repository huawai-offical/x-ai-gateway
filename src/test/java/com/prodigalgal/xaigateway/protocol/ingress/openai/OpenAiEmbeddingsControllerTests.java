package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayEmbeddingExecutionService;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = OpenAiEmbeddingsController.class)
@Import(PermitAllSecurityTestConfig.class)
class OpenAiEmbeddingsControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DistributedKeyAuthenticationService distributedKeyAuthenticationService;

    @MockitoBean
    private GatewayEmbeddingExecutionService gatewayEmbeddingExecutionService;

    @Test
    void shouldCreateEmbeddings() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("object", "list");
        body.put("model", "writer-fast");
        body.putArray("data").addObject()
                .put("object", "embedding")
                .put("index", 0)
                .putArray("embedding")
                .add(0.1)
                .add(0.2);

        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayEmbeddingExecutionService.executeOpenAiEmbeddings(Mockito.eq("sk-gw-test"), Mockito.any()))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body));

        webTestClient.post()
                .uri("/v1/embeddings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"writer-fast",
                          "input":"hello embeddings"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("list")
                .jsonPath("$.model").isEqualTo("writer-fast")
                .jsonPath("$.data[0].embedding[1]").isEqualTo(0.2);
    }
}
