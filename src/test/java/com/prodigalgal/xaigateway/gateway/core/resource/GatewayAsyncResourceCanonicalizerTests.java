package com.prodigalgal.xaigateway.gateway.core.resource;

import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayAsyncResourceEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayFileBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayFileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayAsyncResourceCanonicalizerTests {

    @Test
    void shouldNormalizeLifecycleAcrossAsyncResourceFamilies() {
        GatewayFileBindingRepository gatewayFileBindingRepository = Mockito.mock(GatewayFileBindingRepository.class);
        GatewayFileRepository gatewayFileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayAsyncResourceCanonicalizer canonicalizer = new GatewayAsyncResourceCanonicalizer(
                gatewayFileBindingRepository,
                gatewayFileRepository,
                new ObjectMapper()
        );

        List<GatewayAsyncResourceEntity> entities = List.of(
                entity(GatewayAsyncResourceType.RESPONSE, "resp_1", "completed", """
                        {"object_mode":"gateway_response_object","events":[{"type":"stored","status":"completed","at":1713150000}]}
                        """),
                entity(GatewayAsyncResourceType.UPLOAD, "upload_1", "created", """
                        {"object_mode":"upstream_object_with_local_lineage","events":[{"type":"created","status":"created","at":1713150000}]}
                        """),
                entity(GatewayAsyncResourceType.BATCH, "batch_1", "validating", """
                        {"object_mode":"upstream_object_with_local_lineage","events":[{"type":"synced","status":"validating","at":1713150000}]}
                        """),
                entity(GatewayAsyncResourceType.TUNING, "ftjob_1", "cancelled", """
                        {"object_mode":"upstream_object_with_local_lineage","events":[{"type":"status_changed","status":"cancelled","at":1713150000}]}
                        """),
                entity(GatewayAsyncResourceType.REALTIME_SESSION, "sess_1", "failed", """
                        {"object_mode":"upstream_object_with_local_lineage","failure_reason":"expired","events":[{"type":"synced","status":"failed","at":1713150000}]}
                        """)
        );

        assertEquals("completed", canonicalizer.toLifecycle(entities.get(0)).normalizedStatus());
        assertEquals("created", canonicalizer.toLifecycle(entities.get(1)).normalizedStatus());
        assertEquals("in_progress", canonicalizer.toLifecycle(entities.get(2)).normalizedStatus());
        assertEquals("cancelled", canonicalizer.toLifecycle(entities.get(3)).normalizedStatus());
        assertEquals("failed", canonicalizer.toLifecycle(entities.get(4)).normalizedStatus());
        assertEquals("expired", canonicalizer.toLifecycle(entities.get(4)).failureReason());
        assertEquals("synced", canonicalizer.toTransitions(entities.get(4)).get(0).eventType());
    }

    @Test
    void shouldBuildUploadLineageAndArtifactsFromMetadata() {
        GatewayFileBindingRepository gatewayFileBindingRepository = Mockito.mock(GatewayFileBindingRepository.class);
        GatewayFileRepository gatewayFileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayAsyncResourceCanonicalizer canonicalizer = new GatewayAsyncResourceCanonicalizer(
                gatewayFileBindingRepository,
                gatewayFileRepository,
                new ObjectMapper()
        );

        GatewayAsyncResourceEntity entity = entity(GatewayAsyncResourceType.UPLOAD, "upload_1", "in_progress", """
                {
                  "object_mode":"upstream_object_with_local_lineage",
                  "upstream_object_id":"upload-upstream-1",
                  "credential_id":101,
                  "site_profile_id":1,
                  "parts":["part-upstream-1"],
                  "part_bindings":[{"upstream_part_id":"part-upstream-1","filename":"segment.bin","synced_at":1713150000}],
                  "events":[{"type":"part_added","status":"in_progress","at":1713150000}]
                }
                """);

        var lineage = canonicalizer.toLineage(entity);
        var artifacts = canonicalizer.toArtifacts(entity);

        assertEquals("upstream_object_with_local_lineage", lineage.objectMode());
        assertEquals("upload-upstream-1", lineage.upstreamObjectId());
        assertEquals(1, lineage.parts().size());
        assertEquals(1, artifacts.size());
        assertEquals("upload_part", artifacts.get(0).artifactKind());
        assertEquals("segment.bin", artifacts.get(0).displayName());
    }

    @Test
    void shouldBuildGatewayLocalUploadLineageWithoutUpstreamObjectId() {
        GatewayFileBindingRepository gatewayFileBindingRepository = Mockito.mock(GatewayFileBindingRepository.class);
        GatewayFileRepository gatewayFileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayAsyncResourceCanonicalizer canonicalizer = new GatewayAsyncResourceCanonicalizer(
                gatewayFileBindingRepository,
                gatewayFileRepository,
                new ObjectMapper()
        );

        GatewayAsyncResourceEntity entity = entity(GatewayAsyncResourceType.UPLOAD, "upload_2", "completed", """
                {
                  "object_mode":"gateway_upload_object",
                  "credential_id":201,
                  "site_profile_id":2,
                  "parts":["part-local-1"],
                  "part_bindings":[{"filename":"segment.bin","synced_at":1713150000}],
                  "events":[{"type":"created","status":"created","at":1713150000},{"type":"status_changed","status":"completed","at":1713150300}]
                }
                """);

        var lineage = canonicalizer.toLineage(entity);
        var artifacts = canonicalizer.toArtifacts(entity);

        assertEquals("gateway_upload_object", lineage.objectMode());
        assertEquals("upload_2", lineage.gatewayResourceKey());
        assertEquals(null, lineage.upstreamObjectId());
        assertEquals(1, lineage.parts().size());
        assertEquals(1, artifacts.size());
        assertEquals("upload_part", artifacts.get(0).artifactKind());
    }

    @Test
    void shouldResolveGatewayFileBindingArtifactsForBatchAndTuningRequests() {
        GatewayFileBindingRepository gatewayFileBindingRepository = Mockito.mock(GatewayFileBindingRepository.class);
        GatewayFileRepository gatewayFileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayAsyncResourceCanonicalizer canonicalizer = new GatewayAsyncResourceCanonicalizer(
                gatewayFileBindingRepository,
                gatewayFileRepository,
                new ObjectMapper()
        );

        GatewayFileBindingEntity inputBinding = new GatewayFileBindingEntity();
        inputBinding.setGatewayFileId(1L);
        inputBinding.setCredentialId(101L);
        inputBinding.setExternalFileId("file-upstream-1");
        inputBinding.setExternalFilename("input.jsonl");
        inputBinding.setStatus("ACTIVE");
        GatewayFileBindingEntity trainingBinding = new GatewayFileBindingEntity();
        trainingBinding.setGatewayFileId(2L);
        trainingBinding.setCredentialId(101L);
        trainingBinding.setExternalFileId("file-upstream-2");
        trainingBinding.setExternalFilename("training.jsonl");
        trainingBinding.setStatus("ACTIVE");
        GatewayFileBindingEntity validationBinding = new GatewayFileBindingEntity();
        validationBinding.setGatewayFileId(3L);
        validationBinding.setCredentialId(101L);
        validationBinding.setExternalFileId("file-upstream-3");
        validationBinding.setExternalFilename("validation.jsonl");
        validationBinding.setStatus("ACTIVE");
        GatewayFileBindingEntity outputBinding = new GatewayFileBindingEntity();
        outputBinding.setGatewayFileId(4L);
        outputBinding.setCredentialId(101L);
        outputBinding.setExternalFileId("file-upstream-4");
        outputBinding.setExternalFilename("output.jsonl");
        outputBinding.setStatus("ACTIVE");
        GatewayFileBindingEntity errorBinding = new GatewayFileBindingEntity();
        errorBinding.setGatewayFileId(5L);
        errorBinding.setCredentialId(101L);
        errorBinding.setExternalFileId("file-upstream-5");
        errorBinding.setExternalFilename("error.jsonl");
        errorBinding.setStatus("ACTIVE");
        GatewayFileBindingEntity resultBinding = new GatewayFileBindingEntity();
        resultBinding.setGatewayFileId(6L);
        resultBinding.setCredentialId(101L);
        resultBinding.setExternalFileId("file-upstream-6");
        resultBinding.setExternalFilename("result.jsonl");
        resultBinding.setStatus("ACTIVE");

        GatewayFileEntity inputFile = gatewayFile(1L, "file-local-1", "input.jsonl");
        GatewayFileEntity trainingFile = gatewayFile(2L, "file-local-2", "training.jsonl");
        GatewayFileEntity validationFile = gatewayFile(3L, "file-local-3", "validation.jsonl");
        GatewayFileEntity outputFile = gatewayFile(4L, "file-local-4", "output.jsonl");
        GatewayFileEntity errorFile = gatewayFile(5L, "file-local-5", "error.jsonl");
        GatewayFileEntity resultFile = gatewayFile(6L, "file-local-6", "result.jsonl");

        Mockito.when(gatewayFileBindingRepository.findAllByCredentialIdAndExternalFileIdOrderByCreatedAtDesc(101L, "file-upstream-1"))
                .thenReturn(List.of(inputBinding));
        Mockito.when(gatewayFileBindingRepository.findAllByCredentialIdAndExternalFileIdOrderByCreatedAtDesc(101L, "file-upstream-2"))
                .thenReturn(List.of(trainingBinding));
        Mockito.when(gatewayFileBindingRepository.findAllByCredentialIdAndExternalFileIdOrderByCreatedAtDesc(101L, "file-upstream-3"))
                .thenReturn(List.of(validationBinding));
        Mockito.when(gatewayFileBindingRepository.findAllByCredentialIdAndExternalFileIdOrderByCreatedAtDesc(101L, "file-upstream-4"))
                .thenReturn(List.of(outputBinding));
        Mockito.when(gatewayFileBindingRepository.findAllByCredentialIdAndExternalFileIdOrderByCreatedAtDesc(101L, "file-upstream-5"))
                .thenReturn(List.of(errorBinding));
        Mockito.when(gatewayFileBindingRepository.findAllByCredentialIdAndExternalFileIdOrderByCreatedAtDesc(101L, "file-upstream-6"))
                .thenReturn(List.of(resultBinding));
        Mockito.when(gatewayFileRepository.findById(1L)).thenReturn(Optional.of(inputFile));
        Mockito.when(gatewayFileRepository.findById(2L)).thenReturn(Optional.of(trainingFile));
        Mockito.when(gatewayFileRepository.findById(3L)).thenReturn(Optional.of(validationFile));
        Mockito.when(gatewayFileRepository.findById(4L)).thenReturn(Optional.of(outputFile));
        Mockito.when(gatewayFileRepository.findById(5L)).thenReturn(Optional.of(errorFile));
        Mockito.when(gatewayFileRepository.findById(6L)).thenReturn(Optional.of(resultFile));

        GatewayAsyncResourceEntity entity = entityWithRequest(
                GatewayAsyncResourceType.BATCH,
                "batch_1",
                "queued",
                """
                        {"credential_id":101,"object_mode":"upstream_object_with_local_lineage","events":[{"type":"created","status":"queued","at":1713150000}]}
                        """,
                """
                        {"input_file_id":"file-upstream-1","training_file":"file-upstream-2","validation_file":"file-upstream-3"}
                        """
        );
        entity.setResponsePayloadJson("""
                {"output_file_id":"file-upstream-4","error_file_id":"file-upstream-5","result_files":["file-upstream-6"]}
                """);

        var artifacts = canonicalizer.toArtifacts(entity);
        assertEquals(6, artifacts.size());
        assertTrue(artifacts.stream().allMatch(item -> item.artifactKind().equals("gateway_file_binding")));
        assertEquals("file-local-1", artifacts.get(0).artifactId());
        assertEquals("file-local-2", artifacts.get(1).artifactId());
        assertEquals("file-local-3", artifacts.get(2).artifactId());
        assertEquals("file-local-4", artifacts.get(3).artifactId());
        assertEquals("file-local-5", artifacts.get(4).artifactId());
        assertEquals("file-local-6", artifacts.get(5).artifactId());
        assertNotNull(artifacts.get(5).attributes().get("sourceIndex"));
    }

    private GatewayAsyncResourceEntity entity(
            GatewayAsyncResourceType type,
            String resourceKey,
            String status,
            String metadataJson) {
        return entityWithRequest(type, resourceKey, status, metadataJson, "{}");
    }

    private GatewayAsyncResourceEntity entityWithRequest(
            GatewayAsyncResourceType type,
            String resourceKey,
            String status,
            String metadataJson,
            String requestPayloadJson) {
        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey(resourceKey);
        entity.setResourceType(type);
        entity.setStatus(status);
        entity.setRequestModel("model-x");
        entity.setRequestPayloadJson(requestPayloadJson);
        entity.setResponsePayloadJson("{}");
        entity.setMetadataJson(metadataJson);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-04-15T10:00:00Z"));
        ReflectionTestUtils.setField(entity, "updatedAt", Instant.parse("2026-04-15T10:05:00Z"));
        return entity;
    }

    private GatewayFileEntity gatewayFile(Long id, String fileKey, String filename) {
        GatewayFileEntity entity = new GatewayFileEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setFileKey(fileKey);
        entity.setFilename(filename);
        entity.setMimeType("application/json");
        entity.setSizeBytes(128L);
        entity.setStoragePath("D:/tmp/" + filename);
        entity.setStatus("processed");
        return entity;
    }
}
