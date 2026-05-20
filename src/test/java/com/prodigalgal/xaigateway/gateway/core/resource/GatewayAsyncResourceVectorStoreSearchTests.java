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
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayAsyncResourceVectorStoreSearchTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSearchReadableVectorStoreFilesWithFiltersThresholdAndLimit() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayFileRepository fileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayAsyncResourceService service = service(repository, fileRepository);
        GatewayAsyncResourceEntity vectorStore = vectorStore("vs_1", 3);
        GatewayAsyncResourceEntity finance = vectorStoreFile("vsf_1", "vs_1", "file_finance", "finance", 10, 1);
        GatewayAsyncResourceEntity legal = vectorStoreFile("vsf_2", "vs_1", "file_legal", "legal", 3, 2);
        GatewayAsyncResourceEntity unreadable = vectorStoreFile("vsf_3", "vs_1", "file_missing", "finance", 8, 3);
        Path financePath = writeTempContent("The refund policy allows quarterly renewal credits.");
        Path legalPath = writeTempContent("Vendor agreement and renewal calendar.");
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "vs_1",
                GatewayAsyncResourceType.VECTOR_STORE
        )).thenReturn(Optional.of(vectorStore));
        Mockito.when(repository.findAllByDistributedKeyIdAndUpstreamObjectIdAndDeletedFalse(1L, "vs_1"))
                .thenReturn(List.of(finance, legal, unreadable));
        Mockito.when(fileRepository.findByFileKeyAndDeletedFalse("file_finance"))
                .thenReturn(Optional.of(gatewayFile("file_finance", 1L, "finance.txt", financePath)));
        Mockito.when(fileRepository.findByFileKeyAndDeletedFalse("file_legal"))
                .thenReturn(Optional.of(gatewayFile("file_legal", 1L, "legal.txt", legalPath)));
        Mockito.when(fileRepository.findByFileKeyAndDeletedFalse("file_missing"))
                .thenReturn(Optional.empty());

        JsonNode page = service.searchVectorStore("vs_1", 1L, objectMapper.readTree("""
                {
                  "query": ["refund policy", "renewal"],
                  "filters": {
                    "type": "and",
                    "filters": [
                      {"type": "eq", "key": "category", "value": "finance"},
                      {"type": "gte", "key": "priority", "value": 5}
                    ]
                  },
                  "max_num_results": 1,
                  "ranking_options": {"score_threshold": 0.2, "ranker": "auto"},
                  "rewrite_query": true
                }
                """));

        assertEquals("vector_store.search_results.page", page.path("object").asText());
        assertEquals("refund policy", page.path("search_query").path(0).asText());
        assertEquals(1, page.path("data").size());
        assertEquals("file_finance", page.path("data").path(0).path("file_id").asText());
        assertEquals("finance.txt", page.path("data").path(0).path("filename").asText());
        assertTrue(page.path("data").path(0).path("score").asDouble() >= 0.2d);
        assertEquals("finance", page.path("data").path(0).path("attributes").path("category").asText());
        assertEquals("text", page.path("data").path(0).path("content").path(0).path("type").asText());
        assertTrue(page.path("data").path(0).path("content").path(0).path("text").asText().contains("refund policy"));
        assertFalse(page.path("has_more").asBoolean());
        assertTrue(page.path("next_page").isNull());
        assertEquals("auto", page.path("ranking_options").path("ranker").asText());
        assertTrue(page.path("rewrite_query").asBoolean());

        JsonNode missingNumericAttributePage = service.searchVectorStore("vs_1", 1L, objectMapper.readTree("""
                {
                  "query": "renewal",
                  "filters": {"type": "gte", "key": "missing_priority", "value": 5}
                }
                """));
        assertEquals(0, missingNumericAttributePage.path("data").size());
    }

    @Test
    void shouldExposeHasMoreWhenLocalMatchesExceedMaxResults() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayFileRepository fileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayAsyncResourceService service = service(repository, fileRepository);
        GatewayAsyncResourceEntity vectorStore = vectorStore("vs_1", 2);
        GatewayAsyncResourceEntity first = vectorStoreFile("vsf_1", "vs_1", "file_a", "docs", 1, 1);
        GatewayAsyncResourceEntity second = vectorStoreFile("vsf_2", "vs_1", "file_b", "docs", 1, 2);
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "vs_1",
                GatewayAsyncResourceType.VECTOR_STORE
        )).thenReturn(Optional.of(vectorStore));
        Mockito.when(repository.findAllByDistributedKeyIdAndUpstreamObjectIdAndDeletedFalse(1L, "vs_1"))
                .thenReturn(List.of(first, second));
        Mockito.when(fileRepository.findByFileKeyAndDeletedFalse("file_a"))
                .thenReturn(Optional.of(gatewayFile("file_a", 1L, "a.txt", writeTempContent("alpha keyword"))));
        Mockito.when(fileRepository.findByFileKeyAndDeletedFalse("file_b"))
                .thenReturn(Optional.of(gatewayFile("file_b", 1L, "b.txt", writeTempContent("alpha keyword"))));

        JsonNode page = service.searchVectorStore("vs_1", 1L, objectMapper.readTree("""
                {"query":"alpha","max_num_results":1}
                """));

        assertEquals(1, page.path("data").size());
        assertTrue(page.path("has_more").asBoolean());
        assertFalse(page.path("next_page").isNull());
    }

    @Test
    void shouldSearchPersistedLocalIngestionChunksBeforeReadingRawFileContent() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayFileRepository fileRepository = Mockito.mock(GatewayFileRepository.class);
        GatewayAsyncResourceService service = service(repository, fileRepository);
        GatewayAsyncResourceEntity vectorStore = vectorStore("vs_1", 1);
        GatewayAsyncResourceEntity ingested = vectorStoreFileWithIngestion(
                "vsf_1",
                "vs_1",
                "file_ingested",
                "docs",
                9,
                1,
                "pricing policy and renewal refund matrix"
        );
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "vs_1",
                GatewayAsyncResourceType.VECTOR_STORE
        )).thenReturn(Optional.of(vectorStore));
        Mockito.when(repository.findAllByDistributedKeyIdAndUpstreamObjectIdAndDeletedFalse(1L, "vs_1"))
                .thenReturn(List.of(ingested));

        JsonNode page = service.searchVectorStore("vs_1", 1L, objectMapper.readTree("""
                {"query":"renewal refund","filters":{"type":"eq","key":"category","value":"docs"}}
                """));

        assertEquals(1, page.path("data").size());
        JsonNode result = page.path("data").path(0);
        assertEquals("file_ingested", result.path("file_id").asText());
        assertEquals("ingested.txt", result.path("filename").asText());
        assertEquals("chunk_1", result.path("chunk_id").asText());
        assertEquals(0, result.path("chunk_index").asInt());
        assertTrue(result.path("content").path(0).path("text").asText().contains("renewal refund"));
        Mockito.verify(fileRepository, Mockito.never()).findByFileKeyAndDeletedFalse(Mockito.anyString());
    }

    @Test
    void shouldRejectInvalidSearchRequest() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayAsyncResourceService service = service(repository, Mockito.mock(GatewayFileRepository.class));
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "vs_1",
                GatewayAsyncResourceType.VECTOR_STORE
        )).thenReturn(Optional.of(vectorStore("vs_1", 0)));

        IllegalArgumentException missingQuery = assertThrows(
                IllegalArgumentException.class,
                () -> service.searchVectorStore("vs_1", 1L, objectMapper.readTree("{}")));
        IllegalArgumentException invalidMax = assertThrows(
                IllegalArgumentException.class,
                () -> service.searchVectorStore("vs_1", 1L, objectMapper.readTree("""
                        {"query":"alpha","max_num_results":0}
                        """)));
        IllegalArgumentException invalidFilter = assertThrows(
                IllegalArgumentException.class,
                () -> service.searchVectorStore("vs_1", 1L, objectMapper.readTree("""
                        {"query":"alpha","filters":{"type":"near","key":"category","value":"docs"}}
                        """)));

        assertEquals("query 为必填字段。", missingQuery.getMessage());
        assertEquals("max_num_results 必须在 1 到 50 之间。", invalidMax.getMessage());
        assertEquals("不支持的 filters.type：near", invalidFilter.getMessage());
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
            String category,
            int priority,
            long sequence) throws Exception {
        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        ReflectionTestUtils.setField(entity, "id", sequence);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-05-15T00:00:00Z").plusSeconds(sequence));
        entity.setResourceKey(resourceKey);
        entity.setDistributedKeyId(1L);
        entity.setResourceType(GatewayAsyncResourceType.VECTOR_STORE_FILE);
        entity.setUpstreamObjectId(vectorStoreId);
        entity.setStatus("completed");
        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", fileId);
        response.put("object", "vector_store.file");
        response.put("created_at", 1778803200L + sequence);
        response.put("vector_store_id", vectorStoreId);
        response.put("status", "completed");
        response.put("usage_bytes", 0L);
        response.putNull("last_error");
        response.set("attributes", objectMapper.createObjectNode()
                .put("category", category)
                .put("priority", priority));
        response.set("chunking_strategy", objectMapper.createObjectNode().put("type", "auto"));
        entity.setRequestPayloadJson("{}");
        entity.setResponsePayloadJson(objectMapper.writeValueAsString(response));
        entity.setMetadataJson("{}");
        return entity;
    }

    private GatewayAsyncResourceEntity vectorStoreFileWithIngestion(
            String resourceKey,
            String vectorStoreId,
            String fileId,
            String category,
            int priority,
            long sequence,
            String chunkText) throws Exception {
        GatewayAsyncResourceEntity entity = vectorStoreFile(resourceKey, vectorStoreId, fileId, category, priority, sequence);
        ObjectNode metadata = objectMapper.createObjectNode();
        ObjectNode ingestion = metadata.putObject("ingestion");
        ingestion.put("object_mode", "gateway_vector_store_file_ingestion");
        ingestion.put("status", "completed");
        ingestion.put("index_version", "local-chunk-v1");
        ingestion.put("file_id", fileId);
        ingestion.put("filename", "ingested.txt");
        ingestion.put("content_sha256", "a".repeat(64));
        ingestion.put("bytes", chunkText.getBytes(StandardCharsets.UTF_8).length);
        ingestion.put("estimated_tokens", 10);
        ingestion.put("chunk_count", 1);
        ingestion.put("chunk_size_tokens", 800);
        ingestion.put("chunk_overlap_tokens", 120);
        ingestion.putArray("chunks").addObject()
                .put("chunk_id", "chunk_1")
                .put("chunk_index", 0)
                .put("text", chunkText)
                .put("start_char", 0)
                .put("end_char", chunkText.length())
                .put("estimated_tokens", 10);
        entity.setMetadataJson(objectMapper.writeValueAsString(metadata));
        return entity;
    }

    private GatewayFileEntity gatewayFile(String fileKey, Long distributedKeyId, String filename, Path storagePath) throws Exception {
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

    private Path writeTempContent(String content) throws Exception {
        Path path = Files.createTempFile("vector-store-search-", ".txt");
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }
}
