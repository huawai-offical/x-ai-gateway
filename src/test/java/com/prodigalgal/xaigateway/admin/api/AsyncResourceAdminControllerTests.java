package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.AsyncResourceAdminService;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceArtifact;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceLifecycle;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceLineage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceTransition;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceType;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

@WebFluxTest(controllers = AsyncResourceAdminController.class)
@Import(PermitAllSecurityTestConfig.class)
class AsyncResourceAdminControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AsyncResourceAdminService asyncResourceAdminService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReturnAsyncResourceSummaries() {
        Instant createdAt = Instant.parse("2026-04-15T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-04-15T10:05:00Z");
        Mockito.when(asyncResourceAdminService.listAsyncResources(1L, GatewayAsyncResourceType.UPLOAD, "created", createdAt, updatedAt))
                .thenReturn(List.of(new AsyncResourceSummaryResponse(
                        "upload_1",
                        GatewayAsyncResourceType.UPLOAD,
                        "created",
                        "created",
                        "upstream_object_with_local_lineage",
                        "upload-upstream-1",
                        1,
                        createdAt,
                        updatedAt
                )));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/admin/resources/async")
                        .queryParam("distributedKeyId", 1)
                        .queryParam("resourceType", "UPLOAD")
                        .queryParam("status", "created")
                        .queryParam("from", createdAt)
                        .queryParam("to", updatedAt)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].resourceKey").isEqualTo("upload_1")
                .jsonPath("$[0].normalizedStatus").isEqualTo("created")
                .jsonPath("$[0].objectMode").isEqualTo("upstream_object_with_local_lineage");
    }

    @Test
    void shouldReturnAsyncResourceDetail() throws Exception {
        Instant createdAt = Instant.parse("2026-04-15T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-04-15T10:05:00Z");
        Mockito.when(asyncResourceAdminService.getAsyncResource("batch_1"))
                .thenReturn(new AsyncResourceDetailResponse(
                        new CanonicalResourceLifecycle(
                                "batch_1",
                                GatewayAsyncResourceType.BATCH,
                                "validating",
                                "in_progress",
                                false,
                                false,
                                createdAt,
                                updatedAt,
                                1,
                                new CanonicalResourceTransition("created", "queued", createdAt),
                                null,
                                null
                        ),
                        new CanonicalResourceLineage(
                                "upstream_object_with_local_lineage",
                                "batch_1",
                                "batch-upstream-1",
                                101L,
                                1L,
                                "gpt-4o",
                                List.of(),
                                List.of()
                        ),
                        List.of(new CanonicalResourceArtifact(
                                "gateway_file_binding",
                                "file-local-1",
                                "input.jsonl",
                                Map.of("externalFileId", "file-upstream-1")
                        )),
                        objectMapper.readTree("{\"input_file_id\":\"file-upstream-1\"}"),
                        objectMapper.readTree("{\"id\":\"batch_1\",\"status\":\"validating\"}"),
                        objectMapper.readTree("{\"object_mode\":\"upstream_object_with_local_lineage\"}")
                ));

        webTestClient.get()
                .uri("/admin/resources/async/batch_1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.lifecycle.normalizedStatus").isEqualTo("in_progress")
                .jsonPath("$.lineage.upstreamObjectId").isEqualTo("batch-upstream-1")
                .jsonPath("$.artifacts[0].artifactKind").isEqualTo("gateway_file_binding")
                .jsonPath("$.requestPayloadJson.input_file_id").isEqualTo("file-upstream-1");
    }
}
