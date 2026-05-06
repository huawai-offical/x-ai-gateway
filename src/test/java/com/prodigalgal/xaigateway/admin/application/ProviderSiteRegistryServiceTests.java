package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ProviderSitePresetResponse;
import com.prodigalgal.xaigateway.gateway.core.shared.SiteProfileSource;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.gateway.core.site.UpstreamSitePolicyService;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteCapabilitySnapshotEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteCapabilitySnapshotRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteModelCapabilityRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamSiteProfileRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderSiteRegistryServiceTests {

    @Test
    void shouldListPresetCatalogWithImportedFlag() {
        UpstreamSiteProfileRepository profileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        ProviderSiteRegistryService service = newService(profileRepository);
        UpstreamSiteProfileEntity existing = new UpstreamSiteProfileEntity();
        existing.setProfileCode("preset:openai");
        existing.setDisplayName("OpenAI");

        when(profileRepository.findByProfileCode(anyString())).thenReturn(Optional.empty());
        when(profileRepository.findByProfileCode("preset:openai")).thenReturn(Optional.of(existing));

        List<ProviderSitePresetResponse> presets = service.listPresets();

        assertTrue(presets.size() >= 15);
        ProviderSitePresetResponse openai = presets.stream()
                .filter(item -> item.code().equals("openai"))
                .findFirst()
                .orElseThrow();
        ProviderSitePresetResponse qwen = presets.stream()
                .filter(item -> item.code().equals("qwen"))
                .findFirst()
                .orElseThrow();
        assertTrue(openai.imported());
        assertEquals(UpstreamSiteKind.OPENAI_DIRECT, openai.siteKind());
        assertTrue(openai.supportedProtocols().contains("openai"));
        assertEquals("openai-compatible-chat", qwen.compatibilitySurface());
        assertEquals("cloud-openai-compatible", qwen.supportStrategy());
        assertTrue(qwen.modelFamilies().contains("qwen3"));
        assertTrue(qwen.unsupportedFeatures().stream().anyMatch(item -> item.contains("realtime_client_secret")));
    }

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

    private ProviderSiteRegistryService newService(UpstreamSiteProfileRepository profileRepository) {
        return new ProviderSiteRegistryService(
                profileRepository,
                Mockito.mock(SiteCapabilitySnapshotRepository.class),
                Mockito.mock(SiteModelCapabilityRepository.class),
                new UpstreamSitePolicyService(),
                new ProviderCatalogLoader(new ObjectMapper())
        );
    }
}
