package com.prodigalgal.xaigateway.gateway.core.resource;

import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayAsyncResourceEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCacheReferenceEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayAsyncResourceRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileRepository;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

class GatewayPublicResourceServiceTests {

    @Test
    void shouldExposeOperationWrapperForAsyncResource() {
        Fixture fixture = new Fixture();
        GatewayAsyncResourceEntity entity = asyncEntity("video_1", GatewayAsyncResourceType.VIDEO, "running");
        Mockito.when(fixture.asyncRepository.findByResourceKeyAndDistributedKeyIdAndDeletedFalse("video_1", 1L))
                .thenReturn(Optional.of(entity));

        ObjectNode response = fixture.service.getOperation(1L, "operations/video_1");

        assertEquals("operation", response.path("object").asText());
        assertEquals("operations/video_1", response.path("name").asText());
        assertEquals("VIDEO", response.path("metadata").path("resource_type").asText());
    }

    @Test
    void shouldBuildLineageGraphFromAsyncResourceMetadata() {
        Fixture fixture = new Fixture();
        GatewayAsyncResourceEntity entity = asyncEntity("video_1", GatewayAsyncResourceType.VIDEO, "succeeded");
        entity.setMetadataJson("""
                {
                  "object_mode": "upstream_object_with_local_lineage",
                  "upstream_object_id": "operations/video-1",
                  "credential_id": 11,
                  "site_profile_id": 22,
                  "events": [{"type":"created","status":"queued","at":1770000000}]
                }
                """);
        Mockito.when(fixture.asyncRepository.findByResourceKeyAndDistributedKeyIdAndDeletedFalse("video_1", 1L))
                .thenReturn(Optional.of(entity));

        ObjectNode response = fixture.service.lineage(1L, "videos", "video_1");

        assertEquals("resource.lineage", response.path("object").asText());
        assertEquals("gateway:video_1", response.path("root").asText());
        assertTrue(response.path("nodes").size() >= 7);
        assertTrue(response.path("edges").size() >= 6);
        assertEquals("video", response.path("summary").path("resource_type").asText());
        assertEquals(response.path("nodes").size(), response.path("summary").path("node_count").asInt());
    }

    @Test
    void shouldBuildLineageGraphFromCacheReference() {
        Fixture fixture = new Fixture();
        UpstreamCacheReferenceEntity cache = new UpstreamCacheReferenceEntity();
        ReflectionTestUtils.setField(cache, "id", 42L);
        cache.setDistributedKeyId(1L);
        cache.setProviderType(ProviderType.GEMINI_DIRECT);
        cache.setCredentialId(11L);
        cache.setModelGroup("gemini-2.5-pro");
        cache.setPrefixHash("prefix-1");
        cache.setExternalCacheRef("cachedContents/demo");
        cache.setStatus("ACTIVE");

        Mockito.when(fixture.cacheService.resolve(1L, "cache_42")).thenReturn(cache);

        ObjectNode response = fixture.service.lineage(1L, "cache", "cache_42");

        assertEquals("resource.lineage", response.path("object").asText());
        assertEquals("cache", response.path("summary").path("resource_type").asText());
        assertTrue(response.path("nodes").size() >= 6);
        assertTrue(response.path("edges").size() >= 5);
    }

    @Test
    void shouldRejectUnsupportedPublicResourceTypes() {
        Fixture fixture = new Fixture();

        assertThrows(IllegalArgumentException.class, () -> fixture.service.listOperations(1L, "legacy_jobs", "RUNNING"));
        assertThrows(IllegalArgumentException.class, () -> fixture.service.lineage(1L, "legacy_jobs", "job_1"));
    }

    @Test
    void shouldMarkOperationDeleted() {
        Fixture fixture = new Fixture();
        GatewayAsyncResourceEntity entity = asyncEntity("video_1", GatewayAsyncResourceType.VIDEO, "running");
        Mockito.when(fixture.asyncRepository.findByResourceKeyAndDistributedKeyIdAndDeletedFalse("video_1", 1L))
                .thenReturn(Optional.of(entity));
        Mockito.when(fixture.asyncRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ObjectNode response = fixture.service.deleteOperation(1L, "video_1");

        assertTrue(response.path("deleted").asBoolean());
        assertTrue(entity.isDeleted());
        assertTrue(entity.getMetadataJson().contains("operation_deleted"));
    }

    @Test
    void shouldWaitOperationImmediately() {
        Fixture fixture = new Fixture();
        GatewayAsyncResourceEntity entity = asyncEntity("video_1", GatewayAsyncResourceType.VIDEO, "running");
        Mockito.when(fixture.asyncRepository.findByResourceKeyAndDistributedKeyIdAndDeletedFalse("video_1", 1L))
                .thenReturn(Optional.of(entity));

        ObjectNode response = fixture.service.waitOperation(1L, "operations/video_1");

        assertTrue(response.path("waited").asBoolean());
        assertEquals("immediate", response.path("wait_mode").asText());
    }

    @Test
    void shouldPollOperationUntilDoneWhenWaitTimeoutProvided() {
        Fixture fixture = new Fixture();
        GatewayAsyncResourceEntity running = asyncEntity("video_1", GatewayAsyncResourceType.VIDEO, "running");
        GatewayAsyncResourceEntity succeeded = asyncEntity("video_1", GatewayAsyncResourceType.VIDEO, "succeeded");
        Mockito.when(fixture.asyncRepository.findByResourceKeyAndDistributedKeyIdAndDeletedFalse("video_1", 1L))
                .thenReturn(Optional.of(running))
                .thenReturn(Optional.of(succeeded));
        ObjectNode request = fixture.objectMapper.createObjectNode();
        request.put("timeoutMs", 150);

        ObjectNode response = fixture.service.waitOperation(1L, "operations/video_1", request);

        assertTrue(response.path("waited").asBoolean());
        assertTrue(response.path("done").asBoolean());
        assertEquals("polling", response.path("wait_mode").asText());
        assertEquals(false, response.path("timeout").asBoolean());
    }

    @Test
    void shouldListAndCancelVideoOperations() {
        Fixture fixture = new Fixture();
        GatewayAsyncResourceEntity entity = asyncEntity("video_1", GatewayAsyncResourceType.VIDEO, "queued");
        Mockito.when(fixture.asyncRepository.search(
                        Mockito.eq(1L),
                        Mockito.eq(GatewayAsyncResourceType.VIDEO),
                        Mockito.eq("queued"),
                        any()))
                .thenReturn(List.of(entity));
        Mockito.when(fixture.asyncRepository.findByResourceKeyAndDistributedKeyIdAndDeletedFalse("video_1", 1L))
                .thenReturn(Optional.of(entity));
        Mockito.when(fixture.asyncService.cancelVideoTask("video_1", 1L))
                .thenAnswer(invocation -> {
                    entity.setStatus("cancelled");
                    entity.setResponsePayloadJson("{\"id\":\"video_1\",\"object\":\"video.generation\",\"status\":\"cancelled\"}");
                    ObjectNode response = fixture.objectMapper.createObjectNode();
                    response.put("id", "video_1");
                    response.put("status", "cancelled");
                    return response;
                });

        ObjectNode listResponse = fixture.service.listOperations(1L, "videos", "QUEUED");
        ObjectNode cancelResponse = fixture.service.cancelOperation(1L, "operations/video_1");

        assertEquals("operations/video_1", listResponse.path("data").path(0).path("name").asText());
        assertEquals("VIDEO", cancelResponse.path("metadata").path("resource_type").asText());
        assertEquals("cancelled", cancelResponse.path("metadata").path("status").asText());
    }

    private GatewayAsyncResourceEntity asyncEntity(String resourceKey, GatewayAsyncResourceType type, String status) {
        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        ReflectionTestUtils.setField(entity, "id", 99L);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-04-30T10:00:00Z"));
        ReflectionTestUtils.setField(entity, "updatedAt", Instant.parse("2026-04-30T10:05:00Z"));
        entity.setDistributedKeyId(1L);
        entity.setResourceKey(resourceKey);
        entity.setResourceType(type);
        entity.setStatus(status);
        entity.setRequestModel("gemini-2.5-pro");
        entity.setRequestPayloadJson("{}");
        entity.setResponsePayloadJson("{\"id\":\"%s\",\"status\":\"%s\"}".formatted(resourceKey, status));
        entity.setMetadataJson("{\"events\":[]}");
        return entity;
    }

    private static class Fixture {
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final GatewayAsyncResourceRepository asyncRepository = Mockito.mock(GatewayAsyncResourceRepository.class);
        private final GatewayAsyncResourceService asyncService = Mockito.mock(GatewayAsyncResourceService.class);
        private final GatewayCacheResourceService cacheService = Mockito.mock(GatewayCacheResourceService.class);
        private final GatewayPublicResourceService service = new GatewayPublicResourceService(
                asyncRepository,
                asyncService,
                new GatewayAsyncResourceCanonicalizer(
                        Mockito.mock(GatewayFileBindingRepository.class),
                        Mockito.mock(GatewayFileRepository.class),
                        objectMapper),
                cacheService,
                objectMapper);
    }
}
