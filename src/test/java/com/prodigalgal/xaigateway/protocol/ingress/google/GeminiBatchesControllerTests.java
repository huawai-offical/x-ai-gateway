package com.prodigalgal.xaigateway.protocol.ingress.google;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.canonical.GoogleNativeNonChatCanonicalRenderer;
import com.prodigalgal.xaigateway.gateway.core.canonical.NonChatCanonicalRenderService;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceService;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceType;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayAsyncResourceEntity;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@WebFluxTest(controllers = GeminiBatchesController.class)
@Import({
        PermitAllSecurityTestConfig.class,
        GeminiBatchesRequestMapper.class,
        GeminiEmbeddingsEncoder.class,
        GeminiGenerateContentResourceEncoder.class,
        GeminiFilesEncoder.class,
        GeminiBatchesEncoder.class,
        GoogleNativeNonChatCanonicalRenderer.class,
        NonChatCanonicalRenderService.class
})
class GeminiBatchesControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private DistributedKeyAuthenticationService distributedKeyAuthenticationService;

    @MockitoBean
    private GatewayFileService gatewayFileService;

    @MockitoBean
    private GatewayAsyncResourceService gatewayAsyncResourceService;

    @Test
    void shouldCreateGetAndCancelGoogleNativeBatch() {
        Mockito.when(distributedKeyAuthenticationService.authenticateRawToken("sk-gw-test.secret"))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayFileService.resolveGatewayFileKeyByGoogleFileName("files/input-123", 1L)).thenReturn("file-local-1");
        Mockito.when(gatewayFileService.resolveGoogleCredentialIdForFileName("files/input-123", 1L)).thenReturn(101L);

        ObjectNode createResponse = new ObjectMapper().createObjectNode();
        createResponse.put("id", "batch_local_1");
        Mockito.when(gatewayAsyncResourceService.createBatch(Mockito.eq(1L), Mockito.any(), Mockito.eq(101L))).thenReturn(createResponse);
        Mockito.when(gatewayAsyncResourceService.getBatchView("batch_local_1", 1L)).thenReturn(view("batch_local_1", "batches/upstream-1"));
        Mockito.when(gatewayAsyncResourceService.getBatchByUpstreamObjectId("batches/upstream-1", 1L)).thenReturn(view("batch_local_1", "batches/upstream-1"));
        Mockito.when(gatewayAsyncResourceService.cancelBatchByUpstreamObjectId("batches/upstream-1", 1L)).thenReturn(view("batch_local_1", "batches/upstream-1"));

        webTestClient.post()
                .uri("/v1beta/models/gemini-2.5-pro:batchGenerateContent")
                .header("x-goog-api-key", "sk-gw-test.secret")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "src": {
                            "fileName": "files/input-123"
                          },
                          "config": {
                            "displayName": "batch one"
                          }
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("batches/upstream-1");

        webTestClient.get()
                .uri("/v1beta/batches/upstream-1")
                .header("x-goog-api-key", "sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("batches/upstream-1");

        webTestClient.post()
                .uri("/v1beta/batches/upstream-1:cancel")
                .header("x-goog-api-key", "sk-gw-test.secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("batches/upstream-1");
    }

    private GatewayAsyncResourceService.GoogleNativeBatchView view(String resourceKey, String upstreamObjectId) {
        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey(resourceKey);
        entity.setDistributedKeyId(1L);
        entity.setResourceType(GatewayAsyncResourceType.BATCH);
        entity.setRequestModel("gemini-2.5-pro");
        entity.setStatus("validating");
        entity.setUpstreamObjectId(upstreamObjectId);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-04-16T02:00:00Z"));
        ReflectionTestUtils.setField(entity, "updatedAt", Instant.parse("2026-04-16T02:05:00Z"));

        ObjectNode responsePayload = new ObjectMapper().createObjectNode();
        responsePayload.put("model", "gemini-2.5-pro");
        responsePayload.put("status", "validating");
        responsePayload.put("input_file_id", "files/input-123");
        ObjectNode metadata = new ObjectMapper().createObjectNode();
        metadata.put("upstream_object_id", upstreamObjectId);
        return new GatewayAsyncResourceService.GoogleNativeBatchView(entity, responsePayload, metadata);
    }
}
