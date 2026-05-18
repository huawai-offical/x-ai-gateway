package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceService;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@WebFluxTest(controllers = OpenAiVectorStoresController.class)
@Import(PermitAllSecurityTestConfig.class)
class OpenAiVectorStoresControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DistributedKeyAuthenticationService distributedKeyAuthenticationService;

    @MockitoBean
    private GatewayAsyncResourceService gatewayAsyncResourceService;

    @Test
    void shouldCreateListRetrieveUpdateAndDeleteVectorStore() throws Exception {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        JsonNode vectorStore = objectMapper.readTree("""
                {
                  "id": "vs_1",
                  "object": "vector_store",
                  "created_at": 1778803200,
                  "name": "kb-prod",
                  "status": "completed",
                  "usage_bytes": 0,
                  "file_counts": {"in_progress":0,"completed":0,"failed":0,"cancelled":0,"total":0},
                  "metadata": {"tenant": "alpha"}
                }
                """);
        JsonNode list = objectMapper.readTree("""
                {
                  "object": "list",
                  "data": [
                    {
                      "id": "vs_1",
                      "object": "vector_store",
                      "created_at": 1778803200,
                      "status": "completed",
                      "file_counts": {"total":0}
                    }
                  ],
                  "has_more": false,
                  "first_id": "vs_1",
                  "last_id": "vs_1"
                }
                """);
        Mockito.when(gatewayAsyncResourceService.createVectorStore(Mockito.eq(1L), Mockito.any(JsonNode.class)))
                .thenReturn(vectorStore);
        Mockito.when(gatewayAsyncResourceService.listVectorStores(1L, "vs_prev", 10, "asc"))
                .thenReturn(list);
        Mockito.when(gatewayAsyncResourceService.getVectorStore("vs_1", 1L)).thenReturn(vectorStore);
        Mockito.when(gatewayAsyncResourceService.updateVectorStore(Mockito.eq("vs_1"), Mockito.eq(1L), Mockito.any(JsonNode.class)))
                .thenReturn(objectMapper.readTree("""
                        {
                          "id": "vs_1",
                          "object": "vector_store",
                          "created_at": 1778803200,
                          "name": "kb-updated",
                          "status": "completed",
                          "file_counts": {"total":0},
                          "metadata": {"tenant": "beta"}
                        }
                        """));
        Mockito.when(gatewayAsyncResourceService.deleteVectorStore("vs_1", 1L))
                .thenReturn(objectMapper.readTree("""
                        {"id":"vs_1","object":"vector_store.deleted","deleted":true}
                        """));

        webTestClient.post()
                .uri("/v1/vector_stores")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"kb-prod","metadata":{"tenant":"alpha"}}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("vs_1")
                .jsonPath("$.object").isEqualTo("vector_store")
                .jsonPath("$.metadata.tenant").isEqualTo("alpha");

        webTestClient.get()
                .uri("/v1/vector_stores?after=vs_prev&limit=10&order=asc")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("list")
                .jsonPath("$.first_id").isEqualTo("vs_1");

        webTestClient.get()
                .uri("/v1/vector_stores/vs_1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("vector_store");

        webTestClient.post()
                .uri("/v1/vector_stores/vs_1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"kb-updated","metadata":{"tenant":"beta"}}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("kb-updated")
                .jsonPath("$.metadata.tenant").isEqualTo("beta");

        webTestClient.delete()
                .uri("/v1/vector_stores/vs_1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("vector_store.deleted")
                .jsonPath("$.deleted").isEqualTo(true);
    }

    @Test
    void shouldReturnOpenAiStyleErrorForVectorStorePath() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayAsyncResourceService.listVectorStores(1L, null, 0, null))
                .thenThrow(new IllegalArgumentException("limit 必须在 1 到 100 之间。"));

        webTestClient.get()
                .uri("/v1/vector_stores?limit=0")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.type").isEqualTo("invalid_request_error")
                .jsonPath("$.error.code").isEqualTo("invalid_argument")
                .jsonPath("$.error.message").isEqualTo("limit 必须在 1 到 100 之间。");
    }
}
