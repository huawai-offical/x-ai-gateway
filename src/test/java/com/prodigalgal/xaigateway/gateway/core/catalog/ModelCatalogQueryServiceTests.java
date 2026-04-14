package com.prodigalgal.xaigateway.gateway.core.catalog;

import com.prodigalgal.xaigateway.gateway.core.alias.ModelAliasQueryService;
import com.prodigalgal.xaigateway.gateway.core.alias.ModelAliasRuleView;
import com.prodigalgal.xaigateway.gateway.core.alias.ModelAliasView;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedCredentialBindingView;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.interop.CapabilityResolution;
import com.prodigalgal.xaigateway.gateway.core.interop.CapabilityResolutionReport;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthService;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteModelCapabilityEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteModelCapabilityRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.util.List;
import java.util.Optional;
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
        SiteModelCapabilityRepository siteModelCapabilityRepository = Mockito.mock(SiteModelCapabilityRepository.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        ModelAliasQueryService modelAliasQueryService = Mockito.mock(ModelAliasQueryService.class);
        SiteCapabilityTruthService siteCapabilityTruthService = Mockito.mock(SiteCapabilityTruthService.class);
        Mockito.when(siteCapabilityTruthService.resolve(Mockito.any(), Mockito.any()))
                .thenReturn(nativeResolutionReport());
        ModelCatalogQueryService service = new ModelCatalogQueryService(
                siteModelCapabilityRepository,
                upstreamCredentialRepository,
                modelAliasQueryService,
                siteCapabilityTruthService
        );

        when(upstreamCredentialRepository.findAllByIdInAndDeletedFalse(argThat(ids ->
                ids != null && ids.containsAll(List.of(101L, 102L)) && ids.size() == 2)))
                .thenReturn(List.of(
                        credentialEntity(101L, 1001L),
                        credentialEntity(102L, 1002L)
                ));
        when(siteModelCapabilityRepository.findAllBySiteProfile_IdInAndActiveTrue(argThat(ids ->
                ids != null && ids.containsAll(List.of(1001L, 1002L)) && ids.size() == 2)))
                .thenReturn(List.of(
                        siteCapabilityEntity(1001L, "gpt-4o", List.of("openai")),
                        siteCapabilityEntity(1002L, "gpt-4.1", List.of("openai")),
                        siteCapabilityEntity(1001L, "text-embedding-3-large", List.of("openai"))
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
        SiteModelCapabilityRepository siteModelCapabilityRepository = Mockito.mock(SiteModelCapabilityRepository.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        ModelAliasQueryService modelAliasQueryService = Mockito.mock(ModelAliasQueryService.class);
        SiteCapabilityTruthService siteCapabilityTruthService = Mockito.mock(SiteCapabilityTruthService.class);
        Mockito.when(siteCapabilityTruthService.resolve(Mockito.any(), Mockito.any()))
                .thenReturn(nativeResolutionReport());
        ModelCatalogQueryService service = new ModelCatalogQueryService(
                siteModelCapabilityRepository,
                upstreamCredentialRepository,
                modelAliasQueryService,
                siteCapabilityTruthService
        );

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
        SiteModelCapabilityRepository siteModelCapabilityRepository = Mockito.mock(SiteModelCapabilityRepository.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        ModelAliasQueryService modelAliasQueryService = Mockito.mock(ModelAliasQueryService.class);
        SiteCapabilityTruthService siteCapabilityTruthService = Mockito.mock(SiteCapabilityTruthService.class);
        Mockito.when(siteCapabilityTruthService.resolve(Mockito.any(), Mockito.any()))
                .thenReturn(nativeResolutionReport());
        ModelCatalogQueryService service = new ModelCatalogQueryService(
                siteModelCapabilityRepository,
                upstreamCredentialRepository,
                modelAliasQueryService,
                siteCapabilityTruthService
        );

        when(upstreamCredentialRepository.findAllByIdInAndDeletedFalse(argThat(ids ->
                ids != null && ids.contains(101L) && ids.size() == 1)))
                .thenReturn(List.of(credentialEntity(101L, 1001L)));
        when(siteModelCapabilityRepository.findAllBySiteProfile_IdInAndActiveTrue(argThat(ids ->
                ids != null && ids.contains(1001L) && ids.size() == 1)))
                .thenReturn(List.of(siteCapabilityEntity(1001L, "gpt-4o", List.of("openai"))));
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

    @Test
    void shouldResolveCrossProtocolCandidateWithoutSupportedProtocolsShortCircuit() {
        SiteModelCapabilityRepository siteModelCapabilityRepository = Mockito.mock(SiteModelCapabilityRepository.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        ModelAliasQueryService modelAliasQueryService = Mockito.mock(ModelAliasQueryService.class);
        SiteCapabilityTruthService siteCapabilityTruthService = Mockito.mock(SiteCapabilityTruthService.class);
        Mockito.when(siteCapabilityTruthService.resolve(Mockito.any(), Mockito.any()))
                .thenReturn(nativeResolutionReport());
        ModelCatalogQueryService service = new ModelCatalogQueryService(
                siteModelCapabilityRepository,
                upstreamCredentialRepository,
                modelAliasQueryService,
                siteCapabilityTruthService
        );

        when(siteModelCapabilityRepository.findAllByModelKeyAndActiveTrue("claude-sonnet-4"))
                .thenReturn(List.of(siteCapabilityEntity(1001L, "claude-sonnet-4", List.of("openai"))));
        when(upstreamCredentialRepository.findAllBySiteProfileIdInAndDeletedFalseAndActiveTrue(argThat(ids ->
                ids != null && ids.contains(1001L) && ids.size() == 1)))
                .thenReturn(List.of(credentialEntity(101L, 1001L)));
        when(modelAliasQueryService.findEnabledAlias("claude-sonnet-4"))
                .thenReturn(Optional.empty());

        ResolvedModelView resolved = service.resolveRequestedModel("claude-sonnet-4", "anthropic_native")
                .orElseThrow();

        assertEquals("claude-sonnet-4", resolved.publicModel());
        assertEquals(1, resolved.candidates().size());
        assertEquals(101L, resolved.candidates().get(0).credentialId());
    }

    private UpstreamCredentialEntity credentialEntity(Long credentialId, Long siteProfileId) {
        UpstreamCredentialEntity credential = new UpstreamCredentialEntity();
        credential.setCredentialName("credential-" + credentialId);
        credential.setProviderType(ProviderType.OPENAI_DIRECT);
        credential.setBaseUrl("https://api.openai.com");
        credential.setSiteProfileId(siteProfileId);
        setId(credential, credentialId);
        return credential;
    }

    private SiteModelCapabilityEntity siteCapabilityEntity(Long siteProfileId, String modelKey, List<String> protocols) {
        UpstreamSiteProfileEntity siteProfile = new UpstreamSiteProfileEntity();
        org.springframework.test.util.ReflectionTestUtils.setField(siteProfile, "id", siteProfileId);
        siteProfile.setProviderFamily(ProviderFamily.OPENAI);
        siteProfile.setSiteKind(UpstreamSiteKind.OPENAI_DIRECT);
        siteProfile.setAuthStrategy(AuthStrategy.BEARER);
        siteProfile.setPathStrategy(PathStrategy.OPENAI_V1);
        siteProfile.setErrorSchemaStrategy(ErrorSchemaStrategy.OPENAI_ERROR);

        SiteModelCapabilityEntity entity = new SiteModelCapabilityEntity();
        entity.setSiteProfile(siteProfile);
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
        entity.setSourceRefreshedAt(java.time.Instant.now());
        return entity;
    }

    private void setId(UpstreamCredentialEntity credential, Long id) {
        org.springframework.test.util.ReflectionTestUtils.setField(credential, "id", id);
    }

    private CapabilityResolutionReport nativeResolutionReport() {
        CapabilityResolution resolution = new CapabilityResolution(
                InteropFeature.CHAT_TEXT,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                List.of(),
                List.of()
        );
        return new CapabilityResolutionReport(
                java.util.Map.of(InteropFeature.CHAT_TEXT.wireName(), resolution),
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                ExecutionKind.NATIVE,
                "direct_upstream_execution",
                List.of(),
                List.of()
        );
    }
}
