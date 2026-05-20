package com.prodigalgal.xaigateway.gateway.core.resource;

import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthService;
import com.prodigalgal.xaigateway.gateway.core.site.UpstreamSitePolicyService;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayAsyncResourceEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayFileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayAsyncResourceRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteCapabilitySnapshotRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamSiteProfileRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

class GatewayAsyncResourceVectorStoreFileBatchesTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateCompletedFileBatchAndAttachFilesAtomicallyAfterValidation() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayFileRepository fileRepository = Mockito.mock(GatewayFileRepository.class);
        Mockito.when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        GatewayAsyncResourceService service = service(repository, fileRepository);
        GatewayAsyncResourceEntity vectorStore = vectorStore("vs_1", 0);
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "vs_1",
                GatewayAsyncResourceType.VECTOR_STORE
        )).thenReturn(Optional.of(vectorStore));
        Mockito.when(repository.findAllByDistributedKeyIdAndUpstreamObjectIdAndDeletedFalse(1L, "vs_1"))
                .thenReturn(List.of());
        stubGatewayFile(fileRepository, "file_1", "first.txt", "first batch file");
        stubGatewayFile(fileRepository, "file_2", "second.txt", "second batch file");

        JsonNode created = service.createVectorStoreFileBatch("vs_1", 1L, objectMapper.readTree("""
                {
                  "file_ids": ["file_1", "file_2"],
                  "attributes": {"category": "finance"},
                  "chunking_strategy": {"type": "auto"}
                }
                """));

        assertTrue(created.path("id").asText().startsWith("vsfb_"));
        assertEquals("vector_store.file_batch", created.path("object").asText());
        assertEquals("completed", created.path("status").asText());
        assertEquals(2, created.path("file_counts").path("total").asInt());
        assertEquals(2, objectMapper.readTree(vectorStore.getResponsePayloadJson()).path("file_counts").path("total").asInt());

        ArgumentCaptor<GatewayAsyncResourceEntity> captor = ArgumentCaptor.forClass(GatewayAsyncResourceEntity.class);
        Mockito.verify(repository, Mockito.times(4)).save(captor.capture());
        assertEquals(GatewayAsyncResourceType.VECTOR_STORE_FILE_BATCH, captor.getAllValues().get(0).getResourceType());
        assertEquals(GatewayAsyncResourceType.VECTOR_STORE_FILE, captor.getAllValues().get(1).getResourceType());
        assertEquals(GatewayAsyncResourceType.VECTOR_STORE_FILE, captor.getAllValues().get(2).getResourceType());
        assertTrue(captor.getAllValues().get(0).getMetadataJson().contains("file_1"));
        assertTrue(captor.getAllValues().get(1).getMetadataJson().contains("gateway_vector_store_file_ingestion"));
        assertTrue(captor.getAllValues().get(2).getMetadataJson().contains("gateway_vector_store_file_ingestion"));
        assertTrue(vectorStore.getMetadataJson().contains("file_batch_completed"));
    }

    @Test
    void shouldRejectDuplicateInputBeforeSavingAnyBatch() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayAsyncResourceService service = service(repository);
        GatewayAsyncResourceEntity vectorStore = vectorStore("vs_1", 0);
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "vs_1",
                GatewayAsyncResourceType.VECTOR_STORE
        )).thenReturn(Optional.of(vectorStore));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.createVectorStoreFileBatch("vs_1", 1L, objectMapper.readTree("""
                        {"files":[{"file_id":"file_1"},{"file_id":"file_1"}]}
                        """)));

        assertEquals("files 不能包含重复 file_id。", error.getMessage());
        Mockito.verify(repository, Mockito.never()).save(any());
    }

    @Test
    void shouldRejectAlreadyAttachedFileBeforeSavingAnyBatch() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayAsyncResourceService service = service(repository);
        GatewayAsyncResourceEntity vectorStore = vectorStore("vs_1", 0);
        GatewayAsyncResourceEntity existing = vectorStoreFile("vsf_1", "vs_1", "file_1", "completed", 1);
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "vs_1",
                GatewayAsyncResourceType.VECTOR_STORE
        )).thenReturn(Optional.of(vectorStore));
        Mockito.when(repository.findAllByDistributedKeyIdAndUpstreamObjectIdAndDeletedFalse(1L, "vs_1"))
                .thenReturn(List.of(existing));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.createVectorStoreFileBatch("vs_1", 1L, objectMapper.readTree("""
                        {"file_ids":["file_1", "file_2"]}
                        """)));

        assertEquals("Vector Store 已关联该 file_id：file_1", error.getMessage());
        Mockito.verify(repository, Mockito.never()).save(any());
    }

    @Test
    void shouldRetrieveListBatchFilesAndRejectCompletedCancel() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayAsyncResourceService service = service(repository);
        GatewayAsyncResourceEntity vectorStore = vectorStore("vs_1", 2);
        GatewayAsyncResourceEntity batch = vectorStoreFileBatch("vsfb_1", "vs_1", List.of("file_1", "file_2"), 2);
        GatewayAsyncResourceEntity file1 = vectorStoreFile("vsf_1", "vs_1", "file_1", "completed", 1);
        GatewayAsyncResourceEntity file2 = vectorStoreFile("vsf_2", "vs_1", "file_2", "completed", 2);
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "vs_1",
                GatewayAsyncResourceType.VECTOR_STORE
        )).thenReturn(Optional.of(vectorStore));
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "vsfb_1",
                GatewayAsyncResourceType.VECTOR_STORE_FILE_BATCH
        )).thenReturn(Optional.of(batch));
        Mockito.when(repository.findAllByDistributedKeyIdAndUpstreamObjectIdAndDeletedFalse(1L, "vs_1"))
                .thenReturn(List.of(file1, file2));

        JsonNode retrieved = service.getVectorStoreFileBatch("vs_1", "vsfb_1", 1L);
        JsonNode list = service.listVectorStoreFileBatchFiles("vs_1", "vsfb_1", 1L, "file_1", 1, "asc", "completed");
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.cancelVectorStoreFileBatch("vs_1", "vsfb_1", 1L));

        assertEquals("vsfb_1", retrieved.path("id").asText());
        assertEquals("list", list.path("object").asText());
        assertEquals(1, list.path("data").size());
        assertEquals("file_2", list.path("data").path(0).path("id").asText());
        assertEquals("已完成的 Vector Store File Batch 不能取消。", error.getMessage());
    }

    private GatewayAsyncResourceService service(GatewayAsyncResourceRepository repository) {
        return service(repository, Mockito.mock(GatewayFileRepository.class));
    }

    private GatewayAsyncResourceService service(
            GatewayAsyncResourceRepository repository,
            GatewayFileRepository fileRepository) {
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        return new GatewayAsyncResourceService(
                repository,
                Mockito.mock(DistributedKeyQueryService.class),
                Mockito.mock(UpstreamCredentialRepository.class),
                Mockito.mock(UpstreamSiteProfileRepository.class),
                snapshotRepository,
                fileRepository,
                Mockito.mock(GatewayFileBindingRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                objectMapper,
                Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC),
                WebClient.builder()
        );
    }

    private void stubGatewayFile(
            GatewayFileRepository fileRepository,
            String fileKey,
            String filename,
            String content) throws Exception {
        Path contentPath = Files.createTempFile("vector-store-batch-", ".txt");
        Files.writeString(contentPath, content, StandardCharsets.UTF_8);
        Mockito.when(fileRepository.findByFileKeyAndDeletedFalse(fileKey))
                .thenReturn(Optional.of(gatewayFile(fileKey, 1L, filename, contentPath)));
    }

    private GatewayFileEntity gatewayFile(
            String fileKey,
            Long distributedKeyId,
            String filename,
            Path storagePath) throws Exception {
        GatewayFileEntity entity = new GatewayFileEntity();
        ReflectionTestUtils.setField(entity, "id", 200L);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-05-15T00:00:00Z"));
        entity.setFileKey(fileKey);
        entity.setDistributedKeyId(distributedKeyId);
        entity.setFilename(filename);
        entity.setMimeType("text/plain");
        entity.setPurpose("assistants");
        entity.setSizeBytes(Files.size(storagePath));
        entity.setSha256("0".repeat(64));
        entity.setStoragePath(storagePath.toString());
        entity.setStatus("processed");
        return entity;
    }

    private GatewayAsyncResourceEntity vectorStore(String id, int fileCount) throws Exception {
        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        ReflectionTestUtils.setField(entity, "id", 100L);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-05-15T00:00:00Z"));
        entity.setResourceKey(id);
        entity.setDistributedKeyId(1L);
        entity.setResourceType(GatewayAsyncResourceType.VECTOR_STORE);
        entity.setStatus("completed");
        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", id);
        response.put("object", "vector_store");
        response.put("created_at", 1778803200L);
        response.put("last_active_at", 1778803200L);
        response.put("status", "completed");
        response.put("usage_bytes", 0L);
        response.set("file_counts", objectMapper.createObjectNode()
                .put("in_progress", 0)
                .put("completed", fileCount)
                .put("failed", 0)
                .put("cancelled", 0)
                .put("total", fileCount));
        response.set("metadata", objectMapper.createObjectNode());
        entity.setRequestPayloadJson("{}");
        entity.setResponsePayloadJson(objectMapper.writeValueAsString(response));
        entity.setMetadataJson("{}");
        return entity;
    }

    private GatewayAsyncResourceEntity vectorStoreFileBatch(
            String batchId,
            String vectorStoreId,
            List<String> fileIds,
            long sequence) throws Exception {
        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        ReflectionTestUtils.setField(entity, "id", sequence);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-05-15T00:00:00Z").plusSeconds(sequence));
        entity.setResourceKey(batchId);
        entity.setDistributedKeyId(1L);
        entity.setResourceType(GatewayAsyncResourceType.VECTOR_STORE_FILE_BATCH);
        entity.setUpstreamObjectId(vectorStoreId);
        entity.setStatus("completed");
        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", batchId);
        response.put("object", "vector_store.file_batch");
        response.put("created_at", 1778803200L + sequence);
        response.put("vector_store_id", vectorStoreId);
        response.put("status", "completed");
        response.set("file_counts", objectMapper.createObjectNode()
                .put("in_progress", 0)
                .put("completed", fileIds.size())
                .put("failed", 0)
                .put("cancelled", 0)
                .put("total", fileIds.size()));
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("vector_store_id", vectorStoreId);
        fileIds.forEach(metadata.putArray("file_ids")::add);
        entity.setRequestPayloadJson("{}");
        entity.setResponsePayloadJson(objectMapper.writeValueAsString(response));
        entity.setMetadataJson(objectMapper.writeValueAsString(metadata));
        return entity;
    }

    private GatewayAsyncResourceEntity vectorStoreFile(
            String resourceKey,
            String vectorStoreId,
            String fileId,
            String status,
            long sequence) throws Exception {
        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        ReflectionTestUtils.setField(entity, "id", sequence);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-05-15T00:00:00Z").plusSeconds(sequence));
        entity.setResourceKey(resourceKey);
        entity.setDistributedKeyId(1L);
        entity.setResourceType(GatewayAsyncResourceType.VECTOR_STORE_FILE);
        entity.setUpstreamObjectId(vectorStoreId);
        entity.setStatus(status);
        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", fileId);
        response.put("object", "vector_store.file");
        response.put("created_at", 1778803200L + sequence);
        response.put("vector_store_id", vectorStoreId);
        response.put("status", status);
        response.put("usage_bytes", 0L);
        response.putNull("last_error");
        response.set("attributes", objectMapper.createObjectNode());
        response.set("chunking_strategy", objectMapper.createObjectNode().put("type", "auto"));
        entity.setRequestPayloadJson("{}");
        entity.setResponsePayloadJson(objectMapper.writeValueAsString(response));
        entity.setMetadataJson("{}");
        return entity;
    }
}
