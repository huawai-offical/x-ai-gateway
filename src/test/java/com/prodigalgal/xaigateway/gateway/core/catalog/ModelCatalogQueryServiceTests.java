package com.prodigalgal.xaigateway.gateway.core.catalog;

import com.prodigalgal.xaigateway.gateway.core.alias.ModelAliasQueryService;
import com.prodigalgal.xaigateway.gateway.core.alias.ModelAliasRuleView;
import com.prodigalgal.xaigateway.gateway.core.alias.ModelAliasView;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedCredentialBindingView;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.infra.persistence.entity.CredentialModelCatalogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.CredentialModelCatalogRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

class ModelCatalogQueryServiceTests {

    @Test
    void shouldListDirectModelsAndAccessibleAliasesForDistributedKey() {
        CredentialModelCatalogRepository credentialModelCatalogRepository = Mockito.mock(CredentialModelCatalogRepository.class);
        ModelAliasQueryService modelAliasQueryService = Mockito.mock(ModelAliasQueryService.class);
        ModelCatalogQueryService service = new ModelCatalogQueryService(credentialModelCatalogRepository, modelAliasQueryService);

        when(credentialModelCatalogRepository.findAllByCredentialIdInAndActiveTrue(argThat(ids ->
                ids != null && ids.containsAll(List.of(101L, 102L)) && ids.size() == 2)))
                .thenReturn(List.of(
                        catalogEntity(101L, "gpt-4o", List.of("openai")),
                        catalogEntity(102L, "gpt-4.1", List.of("openai")),
                        catalogEntity(101L, "text-embedding-3-large", List.of("openai"))
                ));
        when(modelAliasQueryService.listEnabledAliases())
                .thenReturn(List.of(new ModelAliasView(
                        1L,
                        "writer-fast",
                        "writer-fast",
                        List.of(new ModelAliasRuleView(
                                11L,
                                "openai",
                                "gpt-4o",
                                "gpt-4o",
                                ProviderType.OPENAI_DIRECT,
                                null,
                                1
                        ))
                )));

        DistributedKeyView distributedKeyView = new DistributedKeyView(
                1L,
                "test-key",
                "sk-gw-test",
                "masked",
                List.of("openai"),
                List.of("gpt-4o", "writer-fast"),
                List.of(
                        new DistributedCredentialBindingView(1L, 101L, "openai-1", ProviderType.OPENAI_DIRECT, "https://api.openai.com", 10, 100),
                        new DistributedCredentialBindingView(2L, 102L, "openai-2", ProviderType.OPENAI_DIRECT, "https://api.openai.com", 20, 50)
                )
        );

        List<GatewayPublicModelView> models = service.listAccessiblePublicModels(distributedKeyView, "openai");

        assertEquals(2, models.size());
        assertIterableEquals(
                List.of("gpt-4o", "writer-fast"),
                models.stream().map(GatewayPublicModelView::publicModelId).toList()
        );
    }

    @Test
    void shouldRejectWhenProtocolIsNotAllowed() {
        CredentialModelCatalogRepository credentialModelCatalogRepository = Mockito.mock(CredentialModelCatalogRepository.class);
        ModelAliasQueryService modelAliasQueryService = Mockito.mock(ModelAliasQueryService.class);
        ModelCatalogQueryService service = new ModelCatalogQueryService(credentialModelCatalogRepository, modelAliasQueryService);

        DistributedKeyView distributedKeyView = new DistributedKeyView(
                1L,
                "test-key",
                "sk-gw-test",
                "masked",
                List.of("anthropic"),
                List.of(),
                List.of(new DistributedCredentialBindingView(
                        1L,
                        101L,
                        "openai-1",
                        ProviderType.OPENAI_DIRECT,
                        "https://api.openai.com",
                        10,
                        100
                ))
        );

        assertThrows(IllegalArgumentException.class, () -> service.listAccessiblePublicModels(distributedKeyView, "openai"));
    }

    @Test
    void shouldFindAccessibleModelDetailByAliasNameIgnoringCase() {
        CredentialModelCatalogRepository credentialModelCatalogRepository = Mockito.mock(CredentialModelCatalogRepository.class);
        ModelAliasQueryService modelAliasQueryService = Mockito.mock(ModelAliasQueryService.class);
        ModelCatalogQueryService service = new ModelCatalogQueryService(credentialModelCatalogRepository, modelAliasQueryService);

        when(credentialModelCatalogRepository.findAllByCredentialIdInAndActiveTrue(argThat(ids ->
                ids != null && ids.contains(101L) && ids.size() == 1)))
                .thenReturn(List.of(catalogEntity(101L, "gpt-4o", List.of("openai"))));
        when(modelAliasQueryService.listEnabledAliases())
                .thenReturn(List.of(new ModelAliasView(
                        1L,
                        "Writer-Fast",
                        "writer-fast",
                        List.of(new ModelAliasRuleView(
                                11L,
                                "openai",
                                "gpt-4o",
                                "gpt-4o",
                                ProviderType.OPENAI_DIRECT,
                                null,
                                1
                        ))
                )));

        DistributedKeyView distributedKeyView = new DistributedKeyView(
                1L,
                "test-key",
                "sk-gw-test",
                "masked",
                List.of("openai"),
                List.of("writer-fast"),
                List.of(new DistributedCredentialBindingView(
                        1L,
                        101L,
                        "openai-1",
                        ProviderType.OPENAI_DIRECT,
                        "https://api.openai.com",
                        10,
                        100
                ))
        );

        GatewayPublicModelView model = service.findAccessiblePublicModel(distributedKeyView, "openai", "WRITER-FAST")
                .orElseThrow();

        assertEquals("Writer-Fast", model.publicModelId());
        assertEquals("writer-fast", model.resolvedModelKey());
        assertTrue(model.alias());
    }

    private CredentialModelCatalogEntity catalogEntity(Long credentialId, String modelKey, List<String> protocols) {
        UpstreamCredentialEntity credential = new UpstreamCredentialEntity();
        credential.setCredentialName("credential-" + credentialId);
        credential.setProviderType(ProviderType.OPENAI_DIRECT);
        credential.setBaseUrl("https://api.openai.com");
        setId(credential, credentialId);

        CredentialModelCatalogEntity entity = new CredentialModelCatalogEntity();
        entity.setCredential(credential);
        entity.setModelName(modelKey);
        entity.setModelKey(modelKey);
        entity.setSupportedProtocols(protocols);
        entity.setSupportsChat(!modelKey.contains("embedding"));
        entity.setSupportsEmbeddings(modelKey.contains("embedding"));
        entity.setSupportsCache(true);
        entity.setSupportsThinking(true);
        entity.setSupportsVisibleReasoning(true);
        entity.setSupportsReasoningReuse(true);
        entity.setReasoningTransport(ReasoningTransport.OPENAI_CHAT);
        entity.setActive(true);
        return entity;
    }

    private void setId(UpstreamCredentialEntity credential, Long id) {
        org.springframework.test.util.ReflectionTestUtils.setField(credential, "id", id);
    }
}
