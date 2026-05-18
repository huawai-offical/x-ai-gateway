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
class OpenAiVectorStoreSearchControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DistributedKeyAuthenticationService distributedKeyAuthenticationService;

    @MockitoBean
    private GatewayAsyncResourceService gatewayAsyncResourceService;

    @Test
    void shouldSearchVectorStore() throws Exception {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        JsonNode page = objectMapper.readTree("""
                {
                  "object": "vector_store.search_results.page",
                  "search_query": "refund policy",
                  "data": [
                    {
                      "file_id": "file_1",
                      "filename": "policy.txt",
                      "score": 1.0,
                      "attributes": {"category":"finance"},
                      "content": [{"type":"text","text":"refund policy"}]
                    }
                  ],
                  "has_more": false,
                  "next_page": null
                }
                """);
        Mockito.when(gatewayAsyncResourceService.searchVectorStore(Mockito.eq("vs_1"), Mockito.eq(1L), Mockito.any(JsonNode.class)))
                .thenReturn(page);

        webTestClient.post()
                .uri("/v1/vector_stores/vs_1/search")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"query":"refund policy","max_num_results":5}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("vector_store.search_results.page")
                .jsonPath("$.search_query").isEqualTo("refund policy")
                .jsonPath("$.data[0].file_id").isEqualTo("file_1")
                .jsonPath("$.data[0].content[0].type").isEqualTo("text")
                .jsonPath("$.has_more").isEqualTo(false);
    }
}
