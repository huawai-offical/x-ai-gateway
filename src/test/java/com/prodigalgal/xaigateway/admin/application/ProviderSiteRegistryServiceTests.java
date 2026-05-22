package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ProviderSitePresetResponse;
import com.prodigalgal.xaigateway.gateway.core.catalog.DiscoveredModelDefinition;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelAddressingStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.SiteProfileSource;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.gateway.core.site.UpstreamSitePolicyService;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteCapabilitySnapshotEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteModelCapabilityEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteCapabilitySnapshotRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteModelCapabilityRepository;
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
        assertEquals(SiteProfileSource.MANUAL, result.getProfileSource());
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

    private ProviderSiteRegistryService newService(UpstreamSiteProfileRepository profileRepository) {
        return new ProviderSiteRegistryService(
                profileRepository,
                Mockito.mock(SiteCapabilitySnapshotRepository.class),
                Mockito.mock(SiteModelCapabilityRepository.class),
                new UpstreamSitePolicyService(),
                new ProviderCatalogLoader(new ObjectMapper())
        );
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
