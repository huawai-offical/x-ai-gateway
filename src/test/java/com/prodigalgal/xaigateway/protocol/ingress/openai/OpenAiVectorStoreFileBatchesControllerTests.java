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
class OpenAiVectorStoreFileBatchesControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DistributedKeyAuthenticationService distributedKeyAuthenticationService;

    @MockitoBean
    private GatewayAsyncResourceService gatewayAsyncResourceService;

    @Test
    void shouldCreateRetrieveCancelAndListVectorStoreFileBatchFiles() throws Exception {
        Mockito.when(distributedKeyAuthenticationService.authenticateBearerToken("Bearer sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        JsonNode batch = objectMapper.readTree("""
                {
                  "id": "vsfb_1",
                  "object": "vector_store.file_batch",
                  "created_at": 1778803200,
                  "vector_store_id": "vs_1",
                  "status": "completed",
                  "file_counts": {"in_progress":0,"completed":2,"failed":0,"cancelled":0,"total":2}
                }
                """);
        JsonNode files = objectMapper.readTree("""
                {
                  "object": "list",
                  "data": [
                    {"id":"file_2","object":"vector_store.file","status":"completed","vector_store_id":"vs_1"}
                  ],
                  "has_more": false,
                  "first_id": "file_2",
                  "last_id": "file_2"
                }
                """);
        Mockito.when(gatewayAsyncResourceService.createVectorStoreFileBatch(Mockito.eq("vs_1"), Mockito.eq(1L), Mockito.any(JsonNode.class)))
                .thenReturn(batch);
        Mockito.when(gatewayAsyncResourceService.getVectorStoreFileBatch("vs_1", "vsfb_1", 1L)).thenReturn(batch);
        Mockito.when(gatewayAsyncResourceService.listVectorStoreFileBatchFiles("vs_1", "vsfb_1", 1L, "file_1", 1, "asc", "completed"))
                .thenReturn(files);
        Mockito.when(gatewayAsyncResourceService.cancelVectorStoreFileBatch("vs_1", "vsfb_1", 1L))
                .thenThrow(new IllegalArgumentException("已完成的 Vector Store File Batch 不能取消。"));

        webTestClient.post()
                .uri("/v1/vector_stores/vs_1/file_batches")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"file_ids":["file_1","file_2"]}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("vsfb_1")
                .jsonPath("$.object").isEqualTo("vector_store.file_batch");

        webTestClient.get()
                .uri("/v1/vector_stores/vs_1/file_batches/vsfb_1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("completed");

        webTestClient.get()
                .uri("/v1/vector_stores/vs_1/file_batches/vsfb_1/files?after=file_1&limit=1&order=asc&filter=completed")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("list")
                .jsonPath("$.first_id").isEqualTo("file_2");

        webTestClient.post()
                .uri("/v1/vector_stores/vs_1/file_batches/vsfb_1/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.type").isEqualTo("invalid_request_error")
                .jsonPath("$.error.code").isEqualTo("invalid_argument")
                .jsonPath("$.error.message").isEqualTo("已完成的 Vector Store File Batch 不能取消。");
    }
}
