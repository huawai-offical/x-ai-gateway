package com.prodigalgal.xaigateway.gateway.core.resource;

import com.prodigalgal.xaigateway.gateway.core.catalog.FineTunedModelRegistrationService;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayAsyncResourceEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCacheReferenceEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayAsyncResourceRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayFileRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

class GatewayPublicResourceServiceTests {

    @Test
    void shouldExposeOperationWrapperForAsyncResource() {
        Fixture fixture = new Fixture();
        GatewayAsyncResourceEntity entity = asyncEntity("ftjob_1", GatewayAsyncResourceType.TUNING, "running");
        Mockito.when(fixture.asyncRepository.findByResourceKeyAndDistributedKeyIdAndDeletedFalse("ftjob_1", 1L))
                .thenReturn(Optional.of(entity));

        ObjectNode response = fixture.service.getOperation(1L, "operations/ftjob_1");

        assertEquals("operation", response.path("object").asText());
        assertEquals("operations/ftjob_1", response.path("name").asText());
        assertEquals("TUNING", response.path("metadata").path("resource_type").asText());
    }

    @Test
    void shouldBuildLineageGraphFromAsyncResourceMetadata() {
        Fixture fixture = new Fixture();
        GatewayAsyncResourceEntity entity = asyncEntity("ftjob_1", GatewayAsyncResourceType.TUNING, "succeeded");
        entity.setMetadataJson("""
                {
                  "object_mode": "upstream_object_with_local_lineage",
                  "upstream_object_id": "operations/tune-1",
                  "credential_id": 11,
                  "site_profile_id": 22,
                  "events": [{"type":"created","status":"queued","at":1770000000}]
                }
                """);
        Mockito.when(fixture.asyncRepository.findByResourceKeyAndDistributedKeyIdAndDeletedFalse("ftjob_1", 1L))
                .thenReturn(Optional.of(entity));

        ObjectNode response = fixture.service.lineage(1L, "tunings", "ftjob_1");

        assertEquals("resource.lineage", response.path("object").asText());
        assertEquals("gateway:ftjob_1", response.path("root").asText());
        assertTrue(response.path("nodes").size() >= 7);
        assertTrue(response.path("edges").size() >= 6);
        assertEquals("tuning", response.path("summary").path("resource_type").asText());
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
    void shouldImportTunedModelAndPersistRegistrationMetadata() {
        Fixture fixture = new Fixture();
        GatewayAsyncResourceEntity entity = asyncEntity("ftjob_1", GatewayAsyncResourceType.TUNING, "succeeded");
        entity.setRequestPayloadJson("{\"model\":\"gemini-2.5-pro\",\"suffix\":\"demo\"}");
        entity.setResponsePayloadJson("{\"id\":\"ftjob_1\",\"fine_tuned_model\":\"tunedModels/demo\",\"status\":\"succeeded\"}");
        entity.setMetadataJson("{\"credential_id\":11,\"site_profile_id\":22,\"events\":[]}");
        UpstreamCredentialEntity credential = new UpstreamCredentialEntity();
        credential.setProviderType(ProviderType.GEMINI_DIRECT);

        Mockito.when(fixture.asyncRepository.findByResourceKeyAndDistributedKeyIdAndDeletedFalse("ftjob_1", 1L))
                .thenReturn(Optional.of(entity));
        Mockito.when(fixture.credentialRepository.findById(11L)).thenReturn(Optional.of(credential));
        Mockito.when(fixture.registrationService.register(22L, ProviderType.GEMINI_DIRECT, "gemini-2.5-pro", "tunedModels/demo", "friendly-demo", "ftjob_1"))
                .thenReturn(new FineTunedModelRegistrationService.RegistrationResult(
                        "tunedModels/demo",
                        "tunedmodels/demo",
                        List.of("friendly-demo")));
        Mockito.when(fixture.asyncRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ObjectNode request = fixture.objectMapper.createObjectNode();
        request.put("alias", "friendly-demo");
        ObjectNode response = fixture.service.importTuning(1L, "ftjob_1", request);

        assertEquals("tuning.import_result", response.path("object").asText());
        assertEquals("tunedmodels/demo", response.path("model_key").asText());
        ArgumentCaptor<GatewayAsyncResourceEntity> captor = ArgumentCaptor.forClass(GatewayAsyncResourceEntity.class);
        Mockito.verify(fixture.asyncRepository).save(captor.capture());
        assertTrue(captor.getValue().getMetadataJson().contains("registered_model_key"));
        assertTrue(captor.getValue().getMetadataJson().contains("friendly-demo"));
    }

    @Test
    void shouldMarkOperationDeleted() {
        Fixture fixture = new Fixture();
        GatewayAsyncResourceEntity entity = asyncEntity("ftjob_1", GatewayAsyncResourceType.TUNING, "running");
        Mockito.when(fixture.asyncRepository.findByResourceKeyAndDistributedKeyIdAndDeletedFalse("ftjob_1", 1L))
                .thenReturn(Optional.of(entity));
        Mockito.when(fixture.asyncRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ObjectNode response = fixture.service.deleteOperation(1L, "ftjob_1");

        assertTrue(response.path("deleted").asBoolean());
        assertTrue(entity.isDeleted());
        assertTrue(entity.getMetadataJson().contains("operation_deleted"));
    }

    @Test
    void shouldWaitOperationImmediately() {
        Fixture fixture = new Fixture();
        GatewayAsyncResourceEntity entity = asyncEntity("ftjob_1", GatewayAsyncResourceType.TUNING, "running");
        Mockito.when(fixture.asyncRepository.findByResourceKeyAndDistributedKeyIdAndDeletedFalse("ftjob_1", 1L))
                .thenReturn(Optional.of(entity));

        ObjectNode response = fixture.service.waitOperation(1L, "operations/ftjob_1");

        assertTrue(response.path("waited").asBoolean());
        assertEquals("immediate", response.path("wait_mode").asText());
    }

    @Test
    void shouldListOperationsWithStringResourceType() {
        Fixture fixture = new Fixture();
        GatewayAsyncResourceEntity entity = asyncEntity("ftjob_1", GatewayAsyncResourceType.TUNING, "running");
        Mockito.when(fixture.asyncRepository.search(
                        Mockito.eq(1L),
                        Mockito.eq(GatewayAsyncResourceType.TUNING),
                        Mockito.eq("running"),
                        any()))
                .thenReturn(List.of(entity));

        ObjectNode response = fixture.service.listOperations(1L, "tunings", "RUNNING");

        assertEquals("list", response.path("object").asText());
        assertEquals("operations/ftjob_1", response.path("data").path(0).path("name").asText());
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
        private final UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        private final FineTunedModelRegistrationService registrationService = Mockito.mock(FineTunedModelRegistrationService.class);
        private final GatewayPublicResourceService service = new GatewayPublicResourceService(
                asyncRepository,
                asyncService,
                new GatewayAsyncResourceCanonicalizer(
                        Mockito.mock(GatewayFileBindingRepository.class),
                        Mockito.mock(GatewayFileRepository.class),
                        objectMapper),
                cacheService,
                credentialRepository,
                registrationService,
                objectMapper);
    }
}
