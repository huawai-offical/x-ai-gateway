package com.prodigalgal.xaigateway.gateway.core.resource;

import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthService;
import com.prodigalgal.xaigateway.gateway.core.site.UpstreamSitePolicyService;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayAsyncResourceEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayAsyncResourceRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteCapabilitySnapshotRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamSiteProfileRepository;
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

class GatewayAsyncResourceVectorStoresTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateVectorStoreWithOpenAiShapeAndLocalLineage() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        Mockito.when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        GatewayAsyncResourceService service = service(repository);

        JsonNode created = service.createVectorStore(1L, objectMapper.readTree("""
                {
                  "name": "kb-prod",
                  "metadata": {"tenant": "alpha"},
                  "file_ids": ["file_1", "file_2"],
                  "expires_after": {"anchor": "last_active_at", "days": 7},
                  "expires_at": 1779408000
                }
                """));

        assertTrue(created.path("id").asText().startsWith("vs_"));
        assertEquals("vector_store", created.path("object").asText());
        assertEquals(1778803200L, created.path("created_at").asLong());
        assertEquals("completed", created.path("status").asText());
        assertEquals(0L, created.path("usage_bytes").asLong());
        assertEquals(2, created.path("file_counts").path("completed").asInt());
        assertEquals(2, created.path("file_counts").path("total").asInt());
        assertEquals("alpha", created.path("metadata").path("tenant").asText());
        assertEquals("last_active_at", created.path("expires_after").path("anchor").asText());

        ArgumentCaptor<GatewayAsyncResourceEntity> captor = ArgumentCaptor.forClass(GatewayAsyncResourceEntity.class);
        Mockito.verify(repository, Mockito.times(3)).save(captor.capture());
        List<GatewayAsyncResourceEntity> savedEntities = captor.getAllValues();
        GatewayAsyncResourceEntity saved = savedEntities.getFirst();
        assertEquals(GatewayAsyncResourceType.VECTOR_STORE, saved.getResourceType());
        assertEquals(1L, saved.getDistributedKeyId());
        assertEquals("completed", saved.getStatus());
        assertTrue(saved.getResourceKey().startsWith("vs_"));
        assertTrue(saved.getMetadataJson().contains("gateway_vector_store"));
        assertTrue(saved.getRequestPayloadJson().contains("file_1"));
        assertEquals(GatewayAsyncResourceType.VECTOR_STORE_FILE, savedEntities.get(1).getResourceType());
        assertEquals(created.path("id").asText(), savedEntities.get(1).getUpstreamObjectId());
        assertTrue(savedEntities.get(1).getResponsePayloadJson().contains("\"id\":\"file_1\""));
        assertTrue(savedEntities.get(2).getResponsePayloadJson().contains("\"id\":\"file_2\""));
    }

    @Test
    void shouldListVectorStoresWithDescendingCursorAndLimit() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayAsyncResourceService service = service(repository);
        GatewayAsyncResourceEntity store3 = vectorStore("vs_3", 3);
        GatewayAsyncResourceEntity store2 = vectorStore("vs_2", 2);
        GatewayAsyncResourceEntity store1 = vectorStore("vs_1", 1);
        Mockito.when(repository.findStoredResourcesAfterCursorDesc(
                Mockito.eq(1L),
                Mockito.eq(GatewayAsyncResourceType.VECTOR_STORE),
                Mockito.eq("vs_"),
                Mockito.isNull(),
                Mockito.<Instant>nullable(Instant.class),
                Mockito.<Long>nullable(Long.class),
                Mockito.any(Pageable.class)
        )).thenReturn(List.of(store3, store2));
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDistributedKeyIdAndDeletedFalse(
                "vs_3",
                GatewayAsyncResourceType.VECTOR_STORE,
                1L
        )).thenReturn(Optional.of(store3));

        JsonNode firstPage = service.listVectorStores(1L, null, 1, null);

        assertEquals("list", firstPage.path("object").asText());
        assertEquals("vs_3", firstPage.path("data").path(0).path("id").asText());
        assertTrue(firstPage.path("has_more").asBoolean());
        assertEquals("vs_3", firstPage.path("first_id").asText());

        Mockito.when(repository.findStoredResourcesAfterCursorDesc(
                Mockito.eq(1L),
                Mockito.eq(GatewayAsyncResourceType.VECTOR_STORE),
                Mockito.eq("vs_"),
                Mockito.isNull(),
                Mockito.eq(store3.getCreatedAt()),
                Mockito.eq(store3.getId()),
                Mockito.any(Pageable.class)
        )).thenReturn(List.of(store2, store1));

        JsonNode secondPage = service.listVectorStores(1L, "vs_3", 10, "desc");

        assertEquals(2, secondPage.path("data").size());
        assertEquals("vs_2", secondPage.path("data").path(0).path("id").asText());
        assertEquals("vs_1", secondPage.path("data").path(1).path("id").asText());
        assertFalse(secondPage.path("has_more").asBoolean());
    }

    @Test
    void shouldUpdateRetrieveAndDeleteVectorStoreWithinDistributedKey() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        Mockito.when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        GatewayAsyncResourceService service = service(repository);
        GatewayAsyncResourceEntity store = vectorStore("vs_1", 1);
        Mockito.when(repository.findByResourceKeyAndResourceTypeAndDeletedFalse(
                "vs_1",
                GatewayAsyncResourceType.VECTOR_STORE
        )).thenReturn(Optional.of(store));

        JsonNode retrieved = service.getVectorStore("vs_1", 1L);

        assertEquals("vs_1", retrieved.path("id").asText());

        JsonNode updated = service.updateVectorStore("vs_1", 1L, objectMapper.readTree("""
                {
                  "name": "kb-updated",
                  "metadata": {"tenant": "beta"},
                  "expires_after": null,
                  "expires_at": null
                }
                """));

        assertEquals("kb-updated", updated.path("name").asText());
        assertEquals("beta", updated.path("metadata").path("tenant").asText());
        assertTrue(updated.path("expires_after").isNull());
        assertTrue(updated.path("expires_at").isNull());
        assertTrue(store.getMetadataJson().contains("updated"));

        JsonNode deleted = service.deleteVectorStore("vs_1", 1L);

        assertEquals("vector_store.deleted", deleted.path("object").asText());
        assertTrue(deleted.path("deleted").asBoolean());
        assertTrue(store.isDeleted());
        assertEquals("deleted", store.getStatus());
    }

    @Test
    void shouldRejectInvalidVectorStoreInputAndCursor() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        GatewayAsyncResourceService service = service(repository);

        IllegalArgumentException invalidFileIds = assertThrows(
                IllegalArgumentException.class,
                () -> service.createVectorStore(1L, objectMapper.readTree("{\"file_ids\":\"file_1\"}")));
        assertEquals("file_ids 必须是 JSON array。", invalidFileIds.getMessage());

        IllegalArgumentException invalidExpiresAfter = assertThrows(
                IllegalArgumentException.class,
                () -> service.createVectorStore(1L, objectMapper.readTree("{\"expires_after\":\"tomorrow\"}")));
        assertEquals("expires_after 必须是 JSON object 或 null。", invalidExpiresAfter.getMessage());

        JsonNode empty = service.listVectorStores(1L, "vs_missing", 20, "desc");
        assertEquals(0, empty.path("data").size());
        assertFalse(empty.path("has_more").asBoolean());
    }

    private GatewayAsyncResourceService service(GatewayAsyncResourceRepository repository) {
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        return new GatewayAsyncResourceService(
                repository,
                Mockito.mock(DistributedKeyQueryService.class),
                Mockito.mock(UpstreamCredentialRepository.class),
                Mockito.mock(UpstreamSiteProfileRepository.class),
                snapshotRepository,
                Mockito.mock(GatewayFileRepository.class),
                Mockito.mock(GatewayFileBindingRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                new SiteCapabilityTruthService(new UpstreamSitePolicyService(), snapshotRepository),
                objectMapper,
                Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC),
                WebClient.builder()
        );
    }

    private GatewayAsyncResourceEntity vectorStore(String id, long sequence) throws Exception {
        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        ReflectionTestUtils.setField(entity, "id", sequence);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-05-15T00:00:00Z").plusSeconds(sequence));
        entity.setResourceKey(id);
        entity.setDistributedKeyId(1L);
        entity.setResourceType(GatewayAsyncResourceType.VECTOR_STORE);
        entity.setStatus("completed");
        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", id);
        response.put("object", "vector_store");
        response.put("created_at", 1778803200L + sequence);
        response.put("last_active_at", 1778803200L + sequence);
        response.put("status", "completed");
        response.put("usage_bytes", 0L);
        response.put("name", "kb-" + sequence);
        response.set("metadata", objectMapper.createObjectNode().put("tenant", "alpha"));
        response.putNull("expires_after");
        response.putNull("expires_at");
        response.set("file_counts", objectMapper.createObjectNode()
                .put("in_progress", 0)
                .put("completed", 0)
                .put("failed", 0)
                .put("cancelled", 0)
                .put("total", 0));
        entity.setRequestPayloadJson("{}");
        entity.setResponsePayloadJson(objectMapper.writeValueAsString(response));
        entity.setMetadataJson("{}");
        return entity;
    }
}
