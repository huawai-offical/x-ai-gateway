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
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

class GatewayAsyncResourceVectorStoreFilesTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldAttachVectorStoreFileAndUpdateParentCounts() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayFileRepository fileRepository = Mockito.mock(GatewayFileRepository.class);
        Mockito.when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        GatewayAsyncResourceService service = service(repository, fileRepository);
        GatewayAsyncResourceEntity vectorStore = vectorStore("vs_1", 0);
        Path contentPath = writeTempContent("refund policy renewal credits");
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "vs_1",
                GatewayAsyncResourceType.VECTOR_STORE
        )).thenReturn(Optional.of(vectorStore));
        Mockito.when(repository.findAllByDistributedKeyIdAndUpstreamObjectIdAndDeletedFalse(1L, "vs_1"))
                .thenReturn(List.of());
        Mockito.when(fileRepository.findByFileKeyAndDeletedFalse("file_1"))
                .thenReturn(Optional.of(gatewayFile("file_1", 1L, "finance.txt", contentPath, "text/plain")));

        JsonNode created = service.createVectorStoreFile("vs_1", 1L, objectMapper.readTree("""
                {
                  "file_id": "file_1",
                  "attributes": {"category": "finance", "enabled": true, "score": 7},
                  "chunking_strategy": {"type": "static", "static": {"max_chunk_size_tokens": 1200, "chunk_overlap_tokens": 200}}
                }
                """));

        assertEquals("file_1", created.path("id").asText());
        assertEquals("vector_store.file", created.path("object").asText());
        assertEquals("vs_1", created.path("vector_store_id").asText());
        assertEquals("completed", created.path("status").asText());
        assertEquals("finance", created.path("attributes").path("category").asText());
        assertTrue(created.path("attributes").path("enabled").asBoolean());
        assertEquals("static", created.path("chunking_strategy").path("type").asText());
        assertEquals(Files.size(contentPath), created.path("usage_bytes").asLong());

        ArgumentCaptor<GatewayAsyncResourceEntity> captor = ArgumentCaptor.forClass(GatewayAsyncResourceEntity.class);
        Mockito.verify(repository, Mockito.times(2)).save(captor.capture());
        GatewayAsyncResourceEntity savedAttachment = captor.getAllValues().getFirst();
        assertEquals(GatewayAsyncResourceType.VECTOR_STORE_FILE, savedAttachment.getResourceType());
        assertTrue(savedAttachment.getResourceKey().startsWith("vsf_"));
        JsonNode ingestion = objectMapper.readTree(savedAttachment.getMetadataJson()).path("ingestion");
        assertEquals("gateway_vector_store_file_ingestion", ingestion.path("object_mode").asText());
        assertEquals("local-chunk-v1", ingestion.path("index_version").asText());
        assertEquals("finance.txt", ingestion.path("filename").asText());
        assertEquals(Files.size(contentPath), ingestion.path("bytes").asLong());
        assertEquals(1, ingestion.path("chunk_count").asInt());
        assertEquals("refund policy renewal credits", ingestion.path("chunks").path(0).path("text").asText());
        assertEquals(1, objectMapper.readTree(vectorStore.getResponsePayloadJson()).path("file_counts").path("total").asInt());
        assertTrue(vectorStore.getMetadataJson().contains("file_attached"));
    }

    @Test
    void shouldRejectAttachmentWhenGatewayFileIsUnavailable() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayFileRepository fileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayAsyncResourceService service = service(repository, fileRepository);
        GatewayAsyncResourceEntity vectorStore = vectorStore("vs_1", 0);
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "vs_1",
                GatewayAsyncResourceType.VECTOR_STORE
        )).thenReturn(Optional.of(vectorStore));
        Mockito.when(repository.findAllByDistributedKeyIdAndUpstreamObjectIdAndDeletedFalse(1L, "vs_1"))
                .thenReturn(List.of());
        Mockito.when(fileRepository.findByFileKeyAndDeletedFalse("file_missing")).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.createVectorStoreFile("vs_1", 1L, objectMapper.readTree("{\"file_id\":\"file_missing\"}")));

        assertEquals("未找到指定的网关文件对象。", error.getMessage());
        Mockito.verify(repository, Mockito.never()).save(any());
    }

    @Test
    void shouldRejectDuplicateAttachmentInSameVectorStore() throws Exception {
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
                () -> service.createVectorStoreFile("vs_1", 1L, objectMapper.readTree("{\"file_id\":\"file_1\"}")));

        assertEquals("Vector Store 已关联该 file_id。", error.getMessage());
    }

    @Test
    void shouldListVectorStoreFilesWithStatusFilter() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayAsyncResourceService service = service(repository);
        GatewayAsyncResourceEntity vectorStore = vectorStore("vs_1", 0);
        GatewayAsyncResourceEntity file3 = vectorStoreFile("vsf_3", "vs_1", "file_3", "completed", 3);
        GatewayAsyncResourceEntity file2 = vectorStoreFile("vsf_2", "vs_1", "file_2", "failed", 2);
        GatewayAsyncResourceEntity file1 = vectorStoreFile("vsf_1", "vs_1", "file_1", "completed", 1);
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "vs_1",
                GatewayAsyncResourceType.VECTOR_STORE
        )).thenReturn(Optional.of(vectorStore));
        Mockito.when(repository.findChildResourcesAfterCursorDesc(
                Mockito.eq(1L),
                Mockito.eq(GatewayAsyncResourceType.VECTOR_STORE_FILE),
                Mockito.eq("vs_1"),
                Mockito.<Instant>nullable(Instant.class),
                Mockito.<Long>nullable(Long.class),
                Mockito.any(Pageable.class)
        )).thenReturn(List.of(file3, file2, file1));

        JsonNode list = service.listVectorStoreFiles("vs_1", 1L, null, 10, "desc", "completed");

        assertEquals("list", list.path("object").asText());
        assertEquals(2, list.path("data").size());
        assertEquals("file_3", list.path("data").path(0).path("id").asText());
        assertEquals("file_1", list.path("data").path(1).path("id").asText());
        assertFalse(list.path("has_more").asBoolean());
    }

    @Test
    void shouldRetrieveAndDeleteVectorStoreFileWithinParent() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        Mockito.when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        GatewayAsyncResourceService service = service(repository);
        GatewayAsyncResourceEntity vectorStore = vectorStore("vs_1", 1);
        GatewayAsyncResourceEntity file = vectorStoreFile("vsf_1", "vs_1", "file_1", "completed", 1);
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "vs_1",
                GatewayAsyncResourceType.VECTOR_STORE
        )).thenReturn(Optional.of(vectorStore));
        Mockito.when(repository.findAllByDistributedKeyIdAndUpstreamObjectIdAndDeletedFalse(1L, "vs_1"))
                .thenReturn(List.of(file));

        JsonNode retrieved = service.getVectorStoreFile("vs_1", "file_1", 1L);

        assertEquals("file_1", retrieved.path("id").asText());

        JsonNode deleted = service.deleteVectorStoreFile("vs_1", "file_1", 1L);

        assertEquals("vector_store.file.deleted", deleted.path("object").asText());
        assertTrue(deleted.path("deleted").asBoolean());
        assertTrue(file.isDeleted());
        assertEquals("deleted", file.getStatus());
        assertEquals(0, objectMapper.readTree(vectorStore.getResponsePayloadJson()).path("file_counts").path("total").asInt());
        assertTrue(vectorStore.getMetadataJson().contains("file_detached"));
    }

    @Test
    void shouldReadVectorStoreFileContentAsTextPage() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayFileRepository fileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayAsyncResourceService service = service(repository, fileRepository);
        GatewayAsyncResourceEntity vectorStore = vectorStore("vs_1", 1);
        GatewayAsyncResourceEntity attachment = vectorStoreFile("vsf_1", "vs_1", "file_1", "completed", 1);
        ObjectNode attachmentPayload = (ObjectNode) objectMapper.readTree(attachment.getResponsePayloadJson());
        attachmentPayload.set("attributes", objectMapper.createObjectNode().put("category", "finance"));
        attachment.setResponsePayloadJson(objectMapper.writeValueAsString(attachmentPayload));

        Path contentPath = Files.createTempFile("vector-store-file-content-", ".txt");
        Files.writeString(contentPath, "alpha\nbeta", StandardCharsets.UTF_8);
        GatewayFileEntity gatewayFile = gatewayFile("file_1", 1L, "notes.txt", contentPath, "text/plain");
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "vs_1",
                GatewayAsyncResourceType.VECTOR_STORE
        )).thenReturn(Optional.of(vectorStore));
        Mockito.when(repository.findAllByDistributedKeyIdAndUpstreamObjectIdAndDeletedFalse(1L, "vs_1"))
                .thenReturn(List.of(attachment));
        Mockito.when(fileRepository.findByFileKeyAndDeletedFalse("file_1")).thenReturn(Optional.of(gatewayFile));

        JsonNode page = service.getVectorStoreFileContent("vs_1", "file_1", 1L);

        assertEquals("vector_store.file_content.page", page.path("object").asText());
        assertEquals("file_1", page.path("file_id").asText());
        assertEquals("notes.txt", page.path("filename").asText());
        assertEquals("finance", page.path("attributes").path("category").asText());
        assertEquals("text", page.path("data").path(0).path("type").asText());
        assertEquals("alpha\nbeta", page.path("data").path(0).path("text").asText());
        assertEquals("alpha\nbeta", page.path("content").path(0).path("text").asText());
        assertFalse(page.path("has_more").asBoolean());
        assertTrue(page.path("next_page").isNull());
    }

    @Test
    void shouldRejectVectorStoreFileContentWhenAttachmentMissing() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayAsyncResourceService service = service(repository);
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "vs_1",
                GatewayAsyncResourceType.VECTOR_STORE
        )).thenReturn(Optional.of(vectorStore("vs_1", 0)));
        Mockito.when(repository.findAllByDistributedKeyIdAndUpstreamObjectIdAndDeletedFalse(1L, "vs_1"))
                .thenReturn(List.of());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.getVectorStoreFileContent("vs_1", "file_1", 1L));

        assertEquals("未找到指定的 Vector Store File。", error.getMessage());
    }

    @Test
    void shouldRejectVectorStoreFileContentForAnotherDistributedKeyFile() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayFileRepository fileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayAsyncResourceService service = service(repository, fileRepository);
        GatewayAsyncResourceEntity vectorStore = vectorStore("vs_1", 1);
        GatewayAsyncResourceEntity attachment = vectorStoreFile("vsf_1", "vs_1", "file_1", "completed", 1);
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "vs_1",
                GatewayAsyncResourceType.VECTOR_STORE
        )).thenReturn(Optional.of(vectorStore));
        Mockito.when(repository.findAllByDistributedKeyIdAndUpstreamObjectIdAndDeletedFalse(1L, "vs_1"))
                .thenReturn(List.of(attachment));
        Mockito.when(fileRepository.findByFileKeyAndDeletedFalse("file_1"))
                .thenReturn(Optional.of(gatewayFile("file_1", 2L, "secret.txt", Path.of("secret.txt"), "text/plain")));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.getVectorStoreFileContent("vs_1", "file_1", 1L));

        assertEquals("文件对象不属于当前 DistributedKey。", error.getMessage());
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

    private GatewayFileEntity gatewayFile(
            String fileKey,
            Long distributedKeyId,
            String filename,
            Path storagePath,
            String mimeType) throws Exception {
        GatewayFileEntity entity = new GatewayFileEntity();
        ReflectionTestUtils.setField(entity, "id", 200L);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-05-15T00:00:00Z"));
        entity.setFileKey(fileKey);
        entity.setDistributedKeyId(distributedKeyId);
        entity.setFilename(filename);
        entity.setMimeType(mimeType);
        entity.setPurpose("assistants");
        entity.setSizeBytes(Files.exists(storagePath) ? Files.size(storagePath) : 0);
        entity.setSha256("0".repeat(64));
        entity.setStoragePath(storagePath.toString());
        entity.setStatus("processed");
        return entity;
    }

    private Path writeTempContent(String content) throws Exception {
        Path path = Files.createTempFile("vector-store-file-", ".txt");
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }
}
