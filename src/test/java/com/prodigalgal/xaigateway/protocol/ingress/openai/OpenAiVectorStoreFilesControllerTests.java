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
class OpenAiVectorStoreFilesControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DistributedKeyAuthenticationService distributedKeyAuthenticationService;

    @MockitoBean
    private GatewayAsyncResourceService gatewayAsyncResourceService;

    @Test
    void shouldCreateListRetrieveAndDeleteVectorStoreFile() throws Exception {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        JsonNode file = objectMapper.readTree("""
                {
                  "id": "file_1",
                  "object": "vector_store.file",
                  "created_at": 1778803200,
                  "vector_store_id": "vs_1",
                  "status": "completed",
                  "usage_bytes": 0,
                  "attributes": {"category":"finance"},
                  "chunking_strategy": {"type":"auto"}
                }
                """);
        JsonNode list = objectMapper.readTree("""
                {
                  "object": "list",
                  "data": [
                    {"id":"file_1","object":"vector_store.file","status":"completed","vector_store_id":"vs_1"}
                  ],
                  "has_more": false,
                  "first_id": "file_1",
                  "last_id": "file_1"
                }
                """);
        Mockito.when(gatewayAsyncResourceService.createVectorStoreFile(Mockito.eq("vs_1"), Mockito.eq(1L), Mockito.any(JsonNode.class)))
                .thenReturn(file);
        Mockito.when(gatewayAsyncResourceService.listVectorStoreFiles("vs_1", 1L, "file_prev", 10, "asc", "completed"))
                .thenReturn(list);
        Mockito.when(gatewayAsyncResourceService.getVectorStoreFile("vs_1", "file_1", 1L)).thenReturn(file);
        Mockito.when(gatewayAsyncResourceService.getVectorStoreFileContent("vs_1", "file_1", 1L))
                .thenReturn(objectMapper.readTree("""
                        {
                          "object":"vector_store.file_content.page",
                          "file_id":"file_1",
                          "filename":"notes.txt",
                          "attributes":{"category":"finance"},
                          "data":[{"type":"text","text":"hello"}],
                          "content":[{"type":"text","text":"hello"}],
                          "has_more":false,
                          "next_page":null
                        }
                        """));
        Mockito.when(gatewayAsyncResourceService.deleteVectorStoreFile("vs_1", "file_1", 1L))
                .thenReturn(objectMapper.readTree("""
                        {"id":"file_1","object":"vector_store.file.deleted","deleted":true}
                        """));

        webTestClient.post()
                .uri("/v1/vector_stores/vs_1/files")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"file_id":"file_1","attributes":{"category":"finance"}}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("file_1")
                .jsonPath("$.object").isEqualTo("vector_store.file");

        webTestClient.get()
                .uri("/v1/vector_stores/vs_1/files?after=file_prev&limit=10&order=asc&filter=completed")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("list")
                .jsonPath("$.first_id").isEqualTo("file_1");

        webTestClient.get()
                .uri("/v1/vector_stores/vs_1/files/file_1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.vector_store_id").isEqualTo("vs_1");

        webTestClient.get()
                .uri("/v1/vector_stores/vs_1/files/file_1/content")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("vector_store.file_content.page")
                .jsonPath("$.file_id").isEqualTo("file_1")
                .jsonPath("$.data[0].type").isEqualTo("text")
                .jsonPath("$.data[0].text").isEqualTo("hello")
                .jsonPath("$.content[0].text").isEqualTo("hello")
                .jsonPath("$.has_more").isEqualTo(false);

        webTestClient.delete()
                .uri("/v1/vector_stores/vs_1/files/file_1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("vector_store.file.deleted")
                .jsonPath("$.deleted").isEqualTo(true);
    }

    @Test
    void shouldReturnOpenAiStyleErrorForVectorStoreFilePath() {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayAsyncResourceService.createVectorStoreFile(Mockito.eq("vs_1"), Mockito.eq(1L), Mockito.any(JsonNode.class)))
                .thenThrow(new IllegalArgumentException("file_id 为必填字段。"));

        webTestClient.post()
                .uri("/v1/vector_stores/vs_1/files")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.type").isEqualTo("invalid_request_error")
                .jsonPath("$.error.code").isEqualTo("invalid_argument")
                .jsonPath("$.error.message").isEqualTo("file_id 为必填字段。");
    }
}
