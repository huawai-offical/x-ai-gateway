package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ProviderSitePresetResponse;
import com.prodigalgal.xaigateway.gateway.core.catalog.DiscoveredModelDefinition;
import com.prodigalgal.xaigateway.gateway.core.model.ModelPolicyScopeType;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelAddressingStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.SiteProfileSource;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.gateway.core.site.UpstreamSitePolicyService;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteCapabilitySnapshotEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteModelCapabilityEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.ModelPolicyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.ProviderProtocolEndpointEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.ModelPolicyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.ProviderProtocolEndpointRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteCapabilitySnapshotRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteModelCapabilityRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamSiteProfileRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderSiteRegistryServiceTests {


    @Test
    void shouldReturnExistingPresetWithoutOverwritingUserConfiguration() {
        UpstreamSiteProfileRepository profileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        ProviderSiteRegistryService service = newService(profileRepository);
        UpstreamSiteProfileEntity existing = new UpstreamSiteProfileEntity();
        existing.setProfileCode("preset:deepseek");
        existing.setDisplayName("用户自定义 DeepSeek");
        existing.setActive(false);

        when(profileRepository.findByProfileCode("preset:deepseek")).thenReturn(Optional.of(existing));

        UpstreamSiteProfileEntity result = service.importPreset("deepseek", true, false);

        assertSame(existing, result);
        assertEquals("用户自定义 DeepSeek", result.getDisplayName());
        assertFalse(result.isActive());
        verify(profileRepository, never()).save(any());
    }

    @Test
    void shouldCreatePresetProfileAndRefreshSnapshot() {
        UpstreamSiteProfileRepository profileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        SiteModelCapabilityRepository modelCapabilityRepository = Mockito.mock(SiteModelCapabilityRepository.class);
        ProviderSiteRegistryService service = new ProviderSiteRegistryService(
                profileRepository,
                snapshotRepository,
                modelCapabilityRepository,
                new UpstreamSitePolicyService(),
                new ProviderCatalogLoader(new ObjectMapper())
        );
        when(profileRepository.findByProfileCode("preset:openrouter")).thenReturn(Optional.empty());
        when(profileRepository.save(any(UpstreamSiteProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(snapshotRepository.findBySiteProfile_Id(null)).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(SiteCapabilitySnapshotEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpstreamSiteProfileEntity result = service.importPreset("openrouter", true, true);

        assertEquals("preset:openrouter", result.getProfileCode());
        assertEquals(UpstreamSiteKind.OPENROUTER, result.getSiteKind());
        assertEquals(SiteProfileSource.PRESET, result.getProfileSource());
        assertEquals("https://openrouter.ai/api/v1", result.getBaseUrlPattern());

        ArgumentCaptor<SiteCapabilitySnapshotEntity> snapshotCaptor = ArgumentCaptor.forClass(SiteCapabilitySnapshotEntity.class);
        verify(snapshotRepository).save(snapshotCaptor.capture());
        assertEquals("READY", snapshotCaptor.getValue().getHealthState());
        assertTrue(snapshotCaptor.getValue().getSupportedProtocols().contains("openai"));
    }

    @Test
    void shouldRefreshModelCapabilitiesByUpdatingExistingRowsAndMergingDuplicateKeys() {
        UpstreamSiteProfileRepository profileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        SiteModelCapabilityRepository modelCapabilityRepository = Mockito.mock(SiteModelCapabilityRepository.class);
        ProviderSiteRegistryService service = new ProviderSiteRegistryService(
                profileRepository,
                snapshotRepository,
                modelCapabilityRepository,
                new UpstreamSitePolicyService(),
                new ProviderCatalogLoader(new ObjectMapper())
        );
        UpstreamSiteProfileEntity site = sampleSite(2L);
        SiteModelCapabilityEntity existing = capability(site, "mimo-v2-omni", false);
        SiteModelCapabilityEntity stale = capability(site, "old-model", true);

        when(profileRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(site));
        when(snapshotRepository.findBySiteProfile_Id(2L)).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(SiteCapabilitySnapshotEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(modelCapabilityRepository.findAllBySiteProfile_IdOrderByModelKeyAsc(2L)).thenReturn(List.of(existing, stale));

        service.refreshCapabilities(site, List.of(
                discovered("mimo-v2-omni", "openai", false, true),
                discovered("MIMO-V2-OMNI", "responses", true, false)
        ));

        ArgumentCaptor<Iterable<SiteModelCapabilityEntity>> saveAllCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(modelCapabilityRepository).saveAll(saveAllCaptor.capture());
        List<SiteModelCapabilityEntity> writes = toList(saveAllCaptor.getValue());

        assertEquals(2, writes.size());
        SiteModelCapabilityEntity refreshed = writes.stream()
                .filter(item -> item.getModelKey().equals("mimo-v2-omni"))
                .findFirst()
                .orElseThrow();
        assertSame(existing, refreshed);
        assertTrue(refreshed.isActive());
        assertTrue(refreshed.isSupportsChat());
        assertTrue(refreshed.isSupportsTools());
        assertTrue(refreshed.getSupportedProtocols().contains("openai"));
        assertTrue(refreshed.getSupportedProtocols().contains("responses"));

        SiteModelCapabilityEntity inactive = writes.stream()
                .filter(item -> item.getModelKey().equals("old-model"))
                .findFirst()
                .orElseThrow();
        assertFalse(inactive.isActive());
        verify(modelCapabilityRepository, never()).deleteAllBySiteProfile_Id(2L);
    }

    @Test
    void shouldNotClearModelCapabilitiesWhenSnapshotRefreshHasNoDiscoveredModels() {
        UpstreamSiteProfileRepository profileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        SiteModelCapabilityRepository modelCapabilityRepository = Mockito.mock(SiteModelCapabilityRepository.class);
        ProviderSiteRegistryService service = new ProviderSiteRegistryService(
                profileRepository,
                snapshotRepository,
                modelCapabilityRepository,
                new UpstreamSitePolicyService(),
                new ProviderCatalogLoader(new ObjectMapper())
        );
        UpstreamSiteProfileEntity site = sampleSite(2L);

        when(profileRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(site));
        when(snapshotRepository.findBySiteProfile_Id(2L)).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(SiteCapabilitySnapshotEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.refreshCapabilities(site, List.of());

        verify(modelCapabilityRepository, never()).deleteAllBySiteProfile_Id(2L);
        verify(modelCapabilityRepository, never()).saveAll(anyIterable());
        verify(modelCapabilityRepository, never()).findAllBySiteProfile_IdOrderByModelKeyAsc(2L);
    }

    @Test
    void shouldImportOnlyNonDeprecatedDefaultPresets() {
        UpstreamSiteProfileRepository profileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        SiteModelCapabilityRepository modelCapabilityRepository = Mockito.mock(SiteModelCapabilityRepository.class);
        ProviderSiteRegistryService service = new ProviderSiteRegistryService(
                profileRepository,
                snapshotRepository,
                modelCapabilityRepository,
                new UpstreamSitePolicyService(),
                catalog("""
                        {
                          "catalogVersion": "test",
                          "catalogSource": "unit",
                          "presets": [
                            {
                              "code": "openai",
                              "displayName": "OpenAI",
                              "siteKind": "OPENAI_DIRECT",
                              "defaultBaseUrl": "https://api.openai.com",
                              "conformanceChecks": ["chat.native"]
                            },
                            {
                              "code": "deepseek",
                              "displayName": "DeepSeek",
                              "siteKind": "DEEPSEEK",
                              "defaultBaseUrl": "https://api.deepseek.com",
                              "conformanceChecks": ["chat.native"]
                            },
                            {
                              "code": "legacy_vendor",
                              "displayName": "Legacy Vendor",
                              "siteKind": "OPENAI_COMPATIBLE_GENERIC",
                              "defaultBaseUrl": "https://legacy.example.com/v1",
                              "deprecated": true,
                              "conformanceChecks": ["chat.native"]
                            }
                          ]
                        }
                        """)
        );
        when(profileRepository.findByProfileCode(anyString())).thenReturn(Optional.empty());
        when(profileRepository.save(any(UpstreamSiteProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(snapshotRepository.findBySiteProfile_Id(null)).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(SiteCapabilitySnapshotEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<UpstreamSiteProfileEntity> result = service.importDefaultPresets();

        assertEquals(2, result.size());
        assertEquals(List.of("preset:openai", "preset:deepseek"), result.stream()
                .map(UpstreamSiteProfileEntity::getProfileCode)
                .toList());
        assertTrue(result.stream().allMatch(item -> item.getProfileSource() == SiteProfileSource.PRESET));
        verify(profileRepository, never()).findByProfileCode("preset:legacy_vendor");
        verify(snapshotRepository, Mockito.times(2)).save(any(SiteCapabilitySnapshotEntity.class));
        verify(modelCapabilityRepository, never()).saveAll(anyIterable());
    }

    @Test
    void shouldCreateOpenAiAndAnthropicProtocolEndpointsForMimoPreset() {
        UpstreamSiteProfileRepository profileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        SiteModelCapabilityRepository modelCapabilityRepository = Mockito.mock(SiteModelCapabilityRepository.class);
        ProviderProtocolEndpointRepository endpointRepository = Mockito.mock(ProviderProtocolEndpointRepository.class);
        ProviderSiteRegistryService service = new ProviderSiteRegistryService(
                profileRepository,
                snapshotRepository,
                modelCapabilityRepository,
                null,
                endpointRepository,
                new UpstreamSitePolicyService(),
                catalog("""
                        {
                          "catalogVersion": "test",
                          "catalogSource": "unit",
                          "presets": [
                            {
                              "code": "xiaomi_mimo",
                              "displayName": "Xiaomi MiMo",
                              "vendorCode": "xiaomi_mimo",
                              "siteKind": "OPENAI_COMPATIBLE_GENERIC",
                              "defaultBaseUrl": "https://token-plan-sgp.xiaomimimo.com/v1",
                              "conformanceChecks": ["chat.native"]
                            }
                          ]
                        }
                        """)
        );
        UpstreamSiteProfileEntity savedSite = sampleSite(8L);
        savedSite.setProfileCode("preset:xiaomi_mimo");
        savedSite.setBaseUrlPattern("https://token-plan-sgp.xiaomimimo.com/v1");

        Mockito.when(profileRepository.findByProfileCode("preset:xiaomi_mimo")).thenReturn(Optional.empty());
        Mockito.when(profileRepository.save(any(UpstreamSiteProfileEntity.class))).thenReturn(savedSite);
        Mockito.when(endpointRepository.findBySiteProfileIdAndProtocolSuite(Mockito.eq(8L), anyString()))
                .thenReturn(Optional.empty());
        Mockito.when(endpointRepository.save(any(ProviderProtocolEndpointEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.importPreset("xiaomi_mimo", true, false);

        ArgumentCaptor<ProviderProtocolEndpointEntity> endpointCaptor =
                ArgumentCaptor.forClass(ProviderProtocolEndpointEntity.class);
        verify(endpointRepository, Mockito.times(2)).save(endpointCaptor.capture());
        List<ProviderProtocolEndpointEntity> endpoints = endpointCaptor.getAllValues();

        assertTrue(endpoints.stream().anyMatch(endpoint ->
                endpoint.getProtocolSuite().equals("xiaomi_mimo.openai_compatible")
                        && endpoint.getBaseUrl().equals("https://token-plan-sgp.xiaomimimo.com/v1")));
        assertTrue(endpoints.stream().anyMatch(endpoint ->
                endpoint.getProtocolSuite().equals("xiaomi_mimo.anthropic_compatible")
                        && endpoint.getBaseUrl().equals("https://token-plan-sgp.xiaomimimo.com/anthropic")));
    }

    @Test
    void shouldRefreshMimoPresetSnapshotWithOpenAiStyleResourceCapabilities() {
        UpstreamSiteProfileRepository profileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        SiteModelCapabilityRepository modelCapabilityRepository = Mockito.mock(SiteModelCapabilityRepository.class);
        ProviderSiteRegistryService service = new ProviderSiteRegistryService(
                profileRepository,
                snapshotRepository,
                modelCapabilityRepository,
                new UpstreamSitePolicyService(),
                new ProviderCatalogLoader(new ObjectMapper())
        );
        UpstreamSiteProfileEntity site = sampleSite(8L);
        site.setProfileCode("preset:xiaomi_mimo");

        when(profileRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(site));
        when(snapshotRepository.findBySiteProfile_Id(8L)).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(SiteCapabilitySnapshotEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.refreshCapabilities(site, List.of());

        ArgumentCaptor<SiteCapabilitySnapshotEntity> snapshotCaptor = ArgumentCaptor.forClass(SiteCapabilitySnapshotEntity.class);
        verify(snapshotRepository).save(snapshotCaptor.capture());
        SiteCapabilitySnapshotEntity snapshot = snapshotCaptor.getValue();
        assertTrue(snapshot.isSupportsAudio());
        assertTrue(snapshot.isSupportsImages());
        assertTrue(snapshot.isSupportsModeration());
        assertTrue(snapshot.isSupportsFiles());
        assertTrue(snapshot.isSupportsUploads());
    }

    @Test
    void shouldNotRecreateDisabledPresetModelPolicyOnDefaultImport() {
        UpstreamSiteProfileRepository profileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        SiteModelCapabilityRepository modelCapabilityRepository = Mockito.mock(SiteModelCapabilityRepository.class);
        ModelPolicyRepository modelPolicyRepository = Mockito.mock(ModelPolicyRepository.class);
        ProviderSiteRegistryService service = new ProviderSiteRegistryService(
                profileRepository,
                snapshotRepository,
                modelCapabilityRepository,
                modelPolicyRepository,
                new UpstreamSitePolicyService(),
                catalog("""
                        {
                          "catalogVersion": "test",
                          "catalogSource": "unit",
                          "presets": [
                            {
                              "code": "xiaomi_mimo",
                              "displayName": "Xiaomi MiMo",
                              "siteKind": "OPENAI_COMPATIBLE_GENERIC",
                              "defaultBaseUrl": "https://token-plan-sgp.xiaomimimo.com/v1",
                              "conformanceChecks": ["chat.native"],
                              "modelPolicies": [
                                {
                                  "policyKind": "MAP",
                                  "publicModel": "gpt-5-codex",
                                  "upstreamModel": "mimo-v2.5-pro",
                                  "supportedProtocols": ["responses"]
                                }
                              ]
                            }
                          ]
                        }
                        """)
        );
        UpstreamSiteProfileEntity site = sampleSite(3L);
        site.setProfileCode("preset:xiaomi_mimo");
        ModelPolicyEntity disabledPolicy = new ModelPolicyEntity();
        disabledPolicy.setPublicModelKey("gpt-5-codex");
        disabledPolicy.setUpstreamModelKey("mimo-v2.5-pro");
        disabledPolicy.setMappingSource("preset");
        disabledPolicy.setEnabled(false);

        when(profileRepository.findByProfileCode("preset:xiaomi_mimo")).thenReturn(Optional.of(site));
        when(profileRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(site));
        when(snapshotRepository.findBySiteProfile_Id(3L)).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(SiteCapabilitySnapshotEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(modelPolicyRepository.findAllByScopeTypeAndScopeIdOrderByPriorityAscCreatedAtAsc(
                ModelPolicyScopeType.SITE_PROFILE,
                3L
        )).thenReturn(List.of(disabledPolicy));

        service.importDefaultPresets();

        verify(modelPolicyRepository, never()).save(any(ModelPolicyEntity.class));
    }

    @Test
    void shouldBackfillCredentialProtocolEndpointWhenEndpointMatchesUniquely() {
        UpstreamSiteProfileRepository profileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        SiteModelCapabilityRepository modelCapabilityRepository = Mockito.mock(SiteModelCapabilityRepository.class);
        ProviderProtocolEndpointRepository endpointRepository = Mockito.mock(ProviderProtocolEndpointRepository.class);
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        ProviderSiteRegistryService service = new ProviderSiteRegistryService(
                profileRepository,
                snapshotRepository,
                modelCapabilityRepository,
                null,
                endpointRepository,
                credentialRepository,
                new UpstreamSitePolicyService(),
                new ProviderCatalogLoader(new ObjectMapper())
        );
        UpstreamCredentialEntity credential = credential(
                50L,
                8L,
                ProviderType.OPENAI_COMPATIBLE,
                "https://token-plan-sgp.xiaomimimo.com/v1/"
        );
        credential.setCredentialMetadataJson("""
                {"source":"legacy","conversationProfile":{"customFlag":"keep_user"}}
                """);
        ProviderProtocolEndpointEntity endpoint = endpoint(
                90L,
                8L,
                ProviderType.OPENAI_COMPATIBLE,
                "https://token-plan-sgp.xiaomimimo.com/v1",
                """
                        {
                          "targetProtocol": "openai_chat_or_responses",
                          "reasoningContent": "pass_through"
                        }
                        """
        );

        when(credentialRepository.findAllByProtocolEndpointIdIsNullAndDeletedFalse()).thenReturn(List.of(credential));
        when(endpointRepository.findAllBySiteProfileIdAndActiveTrueOrderByDisplayNameAsc(8L)).thenReturn(List.of(endpoint));
        when(credentialRepository.saveAll(anyIterable())).thenAnswer(invocation -> invocation.getArgument(0));

        int count = service.backfillCredentialProtocolEndpoints();

        assertEquals(1, count);
        assertEquals(90L, credential.getProtocolEndpointId());
        assertTrue(credential.getCredentialMetadataJson().contains("\"source\":\"legacy\""));
        assertTrue(credential.getCredentialMetadataJson().contains("\"targetProtocol\":\"openai_chat_or_responses\""));
        assertTrue(credential.getCredentialMetadataJson().contains("\"reasoningContent\":\"pass_through\""));
        assertTrue(credential.getCredentialMetadataJson().contains("\"customFlag\":\"keep_user\""));
        verify(credentialRepository).saveAll(anyIterable());
    }

    @Test
    void shouldSkipCredentialProtocolEndpointBackfillWhenEndpointMatchIsAmbiguous() {
        UpstreamSiteProfileRepository profileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        SiteModelCapabilityRepository modelCapabilityRepository = Mockito.mock(SiteModelCapabilityRepository.class);
        ProviderProtocolEndpointRepository endpointRepository = Mockito.mock(ProviderProtocolEndpointRepository.class);
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        ProviderSiteRegistryService service = new ProviderSiteRegistryService(
                profileRepository,
                snapshotRepository,
                modelCapabilityRepository,
                null,
                endpointRepository,
                credentialRepository,
                new UpstreamSitePolicyService(),
                new ProviderCatalogLoader(new ObjectMapper())
        );
        UpstreamCredentialEntity credential = credential(51L, 8L, ProviderType.OPENAI_COMPATIBLE, "https://api.example.com/v1");
        ProviderProtocolEndpointEntity first = endpoint(91L, 8L, ProviderType.OPENAI_COMPATIBLE, "https://api.example.com/v1", null);
        ProviderProtocolEndpointEntity second = endpoint(92L, 8L, ProviderType.OPENAI_COMPATIBLE, "https://api.example.com/v1/", null);

        when(credentialRepository.findAllByProtocolEndpointIdIsNullAndDeletedFalse()).thenReturn(List.of(credential));
        when(endpointRepository.findAllBySiteProfileIdAndActiveTrueOrderByDisplayNameAsc(8L)).thenReturn(List.of(first, second));

        int count = service.backfillCredentialProtocolEndpoints();

        assertEquals(0, count);
        assertEquals(null, credential.getProtocolEndpointId());
        verify(credentialRepository, never()).saveAll(anyIterable());
    }

    private ProviderSiteRegistryService newService(UpstreamSiteProfileRepository profileRepository) {
        return new ProviderSiteRegistryService(
                profileRepository,
                Mockito.mock(SiteCapabilitySnapshotRepository.class),
                Mockito.mock(SiteModelCapabilityRepository.class),
                new UpstreamSitePolicyService(),
                new ProviderCatalogLoader(new ObjectMapper())
        );
    }

    private ProviderCatalogLoader catalog(String json) {
        return new ProviderCatalogLoader(new ObjectMapper()) {
            @Override
            public ProviderCatalogSnapshot load() {
                return loadFromJson(json, "unit-test");
            }
        };
    }

    private UpstreamSiteProfileEntity sampleSite(Long id) {
        UpstreamSiteProfileEntity entity = new UpstreamSiteProfileEntity();
        entity.setProfileCode("site:openai_compatible_generic");
        entity.setDisplayName("MiMo");
        entity.setProviderFamily(ProviderFamily.OPENAI);
        entity.setSiteKind(UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC);
        entity.setAuthStrategy(AuthStrategy.BEARER);
        entity.setPathStrategy(PathStrategy.OPENAI_V1);
        entity.setModelAddressingStrategy(ModelAddressingStrategy.MODEL_NAME);
        entity.setErrorSchemaStrategy(ErrorSchemaStrategy.OPENAI_ERROR);
        entity.setActive(true);
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private UpstreamCredentialEntity credential(
            Long id,
            Long siteProfileId,
            ProviderType providerType,
            String baseUrl) {
        UpstreamCredentialEntity entity = new UpstreamCredentialEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setSiteProfileId(siteProfileId);
        entity.setProviderType(providerType);
        entity.setBaseUrl(baseUrl);
        entity.setCredentialName("credential-" + id);
        entity.setActive(true);
        entity.setDeleted(false);
        return entity;
    }

    private ProviderProtocolEndpointEntity endpoint(
            Long id,
            Long siteProfileId,
            ProviderType providerType,
            String baseUrl,
            String conversationProfileJson) {
        ProviderProtocolEndpointEntity entity = new ProviderProtocolEndpointEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setSiteProfileId(siteProfileId);
        entity.setEndpointCode("endpoint-" + id);
        entity.setDisplayName("Endpoint " + id);
        entity.setProtocolSuite("suite-" + id);
        entity.setProviderType(providerType);
        entity.setSiteKind(UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC);
        entity.setBaseUrl(baseUrl);
        entity.setConversationProfileJson(conversationProfileJson);
        entity.setActive(true);
        return entity;
    }

    private SiteModelCapabilityEntity capability(UpstreamSiteProfileEntity site, String modelKey, boolean active) {
        SiteModelCapabilityEntity entity = new SiteModelCapabilityEntity();
        entity.setSiteProfile(site);
        entity.setModelName(modelKey);
        entity.setModelKey(modelKey);
        entity.setSupportedProtocols(List.of("openai"));
        entity.setActive(active);
        return entity;
    }

    private DiscoveredModelDefinition discovered(
            String model,
            String protocol,
            boolean supportsChat,
            boolean supportsTools) {
        return new DiscoveredModelDefinition(
                model,
                model.toLowerCase(),
                List.of(protocol),
                supportsChat,
                supportsTools,
                false,
                false,
                false,
                false,
                false,
                false,
                ReasoningTransport.NONE
        );
    }

    private List<SiteModelCapabilityEntity> toList(Iterable<SiteModelCapabilityEntity> items) {
        java.util.ArrayList<SiteModelCapabilityEntity> result = new java.util.ArrayList<>();
        items.forEach(result::add);
        return result;
    }
}
