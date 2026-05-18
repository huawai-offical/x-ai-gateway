package com.prodigalgal.xaigateway.gateway.core.catalog;

import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceType;
import com.prodigalgal.xaigateway.infra.config.web.ApiResourceNotFoundException;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayAsyncResourceEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayAsyncResourceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiFineTunedModelDeletionServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeleteRegisteredFineTunedModelByModelId() throws Exception {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        ModelCatalogQueryService modelCatalogQueryService = Mockito.mock(ModelCatalogQueryService.class);
        FineTunedModelRegistrationService registrationService = Mockito.mock(FineTunedModelRegistrationService.class);
        OpenAiFineTunedModelDeletionService service = new OpenAiFineTunedModelDeletionService(
                repository,
                modelCatalogQueryService,
                registrationService,
                objectMapper
        );
        GatewayAsyncResourceEntity entity = tuningEntity("""
                {
                  "site_profile_id": 7,
                  "registered_model_key": "ft:gpt-4o-mini:org:suffix:abc123",
                  "registered_model_name": "ft:gpt-4o-mini:org:suffix:abc123",
                  "registered_aliases": ["demo-ft"],
                  "events": []
                }
                """);
        Mockito.when(repository.findAllByDistributedKeyIdAndResourceTypeAndDeletedFalse(1L, GatewayAsyncResourceType.TUNING))
                .thenReturn(List.of(entity));
        Mockito.when(registrationService.unregister(7L, "ft:gpt-4o-mini:org:suffix:abc123", List.of("demo-ft"), "ftjob_1"))
                .thenReturn(new FineTunedModelRegistrationService.CleanupResult(1, 1));

        var result = service.deleteRegisteredFineTunedModel(
                distributedKey(),
                "ft:gpt-4o-mini:org:suffix:abc123"
        );

        assertEquals("ft:gpt-4o-mini:org:suffix:abc123", result.modelId());
        assertEquals("ftjob_1", result.tuningResourceKey());
        assertEquals(1, result.removedCapabilities());
        assertEquals(1, result.disabledAliases());

        ArgumentCaptor<GatewayAsyncResourceEntity> captor = ArgumentCaptor.forClass(GatewayAsyncResourceEntity.class);
        Mockito.verify(repository).save(captor.capture());
        JsonNode metadata = objectMapper.readTree(captor.getValue().getMetadataJson());
        assertFalse(metadata.has("registered_model_key"));
        assertFalse(metadata.has("registered_aliases"));
        assertEquals("ft:gpt-4o-mini:org:suffix:abc123", metadata.path("model_delete_requested_id").asText());
        assertTrue(metadata.path("model_deleted_at").isNumber());
        assertEquals("model_deleted", metadata.path("events").path(0).path("type").asText());
    }

    @Test
    void shouldDeleteRegisteredFineTunedModelByAutoAlias() {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        ModelCatalogQueryService modelCatalogQueryService = Mockito.mock(ModelCatalogQueryService.class);
        FineTunedModelRegistrationService registrationService = Mockito.mock(FineTunedModelRegistrationService.class);
        OpenAiFineTunedModelDeletionService service = new OpenAiFineTunedModelDeletionService(
                repository,
                modelCatalogQueryService,
                registrationService,
                objectMapper
        );
        GatewayAsyncResourceEntity entity = tuningEntity("""
                {
                  "site_profile_id": 7,
                  "registered_model_key": "ft:gpt-4o-mini:org:suffix:abc123",
                  "registered_model_name": "ft:gpt-4o-mini:org:suffix:abc123",
                  "registered_aliases": ["demo-ft"]
                }
                """);
        Mockito.when(repository.findAllByDistributedKeyIdAndResourceTypeAndDeletedFalse(1L, GatewayAsyncResourceType.TUNING))
                .thenReturn(List.of(entity));
        Mockito.when(registrationService.unregister(7L, "ft:gpt-4o-mini:org:suffix:abc123", List.of("demo-ft"), "ftjob_1"))
                .thenReturn(new FineTunedModelRegistrationService.CleanupResult(1, 1));

        var result = service.deleteRegisteredFineTunedModel(distributedKey(), "demo-ft");

        assertEquals("demo-ft", result.modelId());
        Mockito.verify(registrationService).unregister(7L, "ft:gpt-4o-mini:org:suffix:abc123", List.of("demo-ft"), "ftjob_1");
        Mockito.verify(repository).save(entity);
    }

    @Test
    void shouldRejectPublicModelDeletion() {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        ModelCatalogQueryService modelCatalogQueryService = Mockito.mock(ModelCatalogQueryService.class);
        FineTunedModelRegistrationService registrationService = Mockito.mock(FineTunedModelRegistrationService.class);
        OpenAiFineTunedModelDeletionService service = new OpenAiFineTunedModelDeletionService(
                repository,
                modelCatalogQueryService,
                registrationService,
                objectMapper
        );
        Mockito.when(repository.findAllByDistributedKeyIdAndResourceTypeAndDeletedFalse(1L, GatewayAsyncResourceType.TUNING))
                .thenReturn(List.of());
        Mockito.when(modelCatalogQueryService.findAccessiblePublicModel(distributedKey(), "openai", "gpt-4o"))
                .thenReturn(Optional.of(new GatewayPublicModelView("gpt-4o", "gpt-4o", false)));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.deleteRegisteredFineTunedModel(distributedKey(), "gpt-4o")
        );

        assertTrue(exception.getMessage().contains("公共模型不能删除"));
        Mockito.verify(registrationService, Mockito.never()).unregister(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldReturnNotFoundForOtherDistributedKeyLineage() {
        GatewayAsyncResourceRepository repository = Mockito.mock(GatewayAsyncResourceRepository.class);
        ModelCatalogQueryService modelCatalogQueryService = Mockito.mock(ModelCatalogQueryService.class);
        FineTunedModelRegistrationService registrationService = Mockito.mock(FineTunedModelRegistrationService.class);
        OpenAiFineTunedModelDeletionService service = new OpenAiFineTunedModelDeletionService(
                repository,
                modelCatalogQueryService,
                registrationService,
                objectMapper
        );
        Mockito.when(repository.findAllByDistributedKeyIdAndResourceTypeAndDeletedFalse(1L, GatewayAsyncResourceType.TUNING))
                .thenReturn(List.of());
        Mockito.when(modelCatalogQueryService.findAccessiblePublicModel(distributedKey(), "openai", "ft:other"))
                .thenReturn(Optional.empty());

        assertThrows(
                ApiResourceNotFoundException.class,
                () -> service.deleteRegisteredFineTunedModel(distributedKey(), "ft:other")
        );
        Mockito.verify(registrationService, Mockito.never()).unregister(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    private GatewayAsyncResourceEntity tuningEntity(String metadataJson) {
        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey("ftjob_1");
        entity.setDistributedKeyId(1L);
        entity.setResourceType(GatewayAsyncResourceType.TUNING);
        entity.setStatus("succeeded");
        entity.setMetadataJson(metadataJson);
        return entity;
    }

    private DistributedKeyView distributedKey() {
        return new DistributedKeyView(
                1L,
                "test-key",
                "sk-gw-test",
                "masked",
                List.of("openai"),
                List.of(),
                List.of()
        );
    }
}
