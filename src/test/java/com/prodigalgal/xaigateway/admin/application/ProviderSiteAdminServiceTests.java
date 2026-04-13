package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.catalog.CredentialModelDiscoveryService;
import com.prodigalgal.xaigateway.gateway.core.interop.CapabilityResolution;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthService;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelAddressingStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteCapabilitySnapshotEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.admin.api.ProviderSiteResponse;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteCapabilitySnapshotRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteModelCapabilityRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamSiteProfileRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProviderSiteAdminServiceTests {

    @Test
    void shouldProjectStreamFallbackAndCooldownSummary() {
        UpstreamSiteProfileRepository profileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        SiteModelCapabilityRepository modelCapabilityRepository = Mockito.mock(SiteModelCapabilityRepository.class);
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        ProviderSiteRegistryService providerSiteRegistryService = Mockito.mock(ProviderSiteRegistryService.class);
        CredentialModelDiscoveryService credentialModelDiscoveryService = Mockito.mock(CredentialModelDiscoveryService.class);
        SiteCapabilityTruthService truthService = Mockito.mock(SiteCapabilityTruthService.class);

        ProviderSiteAdminService service = new ProviderSiteAdminService(
                profileRepository,
                snapshotRepository,
                modelCapabilityRepository,
                credentialRepository,
                providerSiteRegistryService,
                credentialModelDiscoveryService,
                truthService
        );

        UpstreamSiteProfileEntity site = sampleSite(1L, "OPENAI_DIRECT", true);
        SiteCapabilitySnapshotEntity snapshot = sampleSnapshot(site);
        UpstreamCredentialEntity cooling = sampleCredential(site.getId(), true, false, Instant.now().plusSeconds(600));
        UpstreamCredentialEntity ready = sampleCredential(site.getId(), true, false, null);

        Mockito.when(profileRepository.findById(1L)).thenReturn(Optional.of(site));
        Mockito.when(snapshotRepository.findBySiteProfile_Id(1L)).thenReturn(Optional.of(snapshot));
        Mockito.when(modelCapabilityRepository.findAllBySiteProfile_IdOrderByModelKeyAsc(1L)).thenReturn(List.of());
        Mockito.when(credentialRepository.findAllBySiteProfileIdAndDeletedFalseAndActiveTrueOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(cooling, ready));
        mockAllFeatures(truthService, site, snapshot);

        var response = service.get(1L);

        assertEquals("sse", response.streamTransport());
        assertEquals("provider-native", response.fallbackStrategy());
        assertEquals(1, response.cooldownCredentialCount());
        assertEquals(cooling.getCooldownUntil(), response.cooldownUntil());
    }

    @Test
    void shouldRefreshAllActiveSitesWhenIdsMissing() {
        UpstreamSiteProfileRepository profileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        SiteModelCapabilityRepository modelCapabilityRepository = Mockito.mock(SiteModelCapabilityRepository.class);
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        ProviderSiteRegistryService providerSiteRegistryService = Mockito.mock(ProviderSiteRegistryService.class);
        CredentialModelDiscoveryService credentialModelDiscoveryService = Mockito.mock(CredentialModelDiscoveryService.class);
        SiteCapabilityTruthService truthService = Mockito.mock(SiteCapabilityTruthService.class);

        ProviderSiteAdminService service = new ProviderSiteAdminService(
                profileRepository,
                snapshotRepository,
                modelCapabilityRepository,
                credentialRepository,
                providerSiteRegistryService,
                credentialModelDiscoveryService,
                truthService
        );

        UpstreamSiteProfileEntity active = sampleSite(1L, "OPENAI_DIRECT", true);
        SiteCapabilitySnapshotEntity snapshot = sampleSnapshot(active);

        Mockito.when(profileRepository.findAllByActiveTrueOrderByDisplayNameAsc()).thenReturn(List.of(active));
        Mockito.when(credentialRepository.findAllBySiteProfileIdAndDeletedFalseOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        Mockito.when(snapshotRepository.findBySiteProfile_Id(1L)).thenReturn(Optional.of(snapshot));
        Mockito.when(modelCapabilityRepository.findAllBySiteProfile_IdOrderByModelKeyAsc(1L)).thenReturn(List.of());
        Mockito.when(credentialRepository.findAllBySiteProfileIdAndDeletedFalseAndActiveTrueOrderByCreatedAtDesc(1L))
                .thenReturn(List.of());
        Mockito.when(providerSiteRegistryService.refreshCapabilities(active, List.of())).thenReturn(snapshot);
        mockAllFeatures(truthService, active, snapshot);

        List<ProviderSiteResponse> responses = service.refreshCapabilities((List<Long>) null);

        assertEquals(1, responses.size());
        assertEquals("OPENAI_DIRECT", responses.getFirst().displayName());
        assertNull(responses.getFirst().cooldownUntil());
        Mockito.verify(providerSiteRegistryService).refreshCapabilities(active, List.of());
    }

    private void mockAllFeatures(
            SiteCapabilityTruthService truthService,
            UpstreamSiteProfileEntity site,
            SiteCapabilitySnapshotEntity snapshot) {
        CapabilityResolution resolution = new CapabilityResolution(
                InteropFeature.RESPONSE_OBJECT,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                List.of(),
                List.of()
        );
        for (InteropFeature feature : List.of(
                InteropFeature.RESPONSE_OBJECT,
                InteropFeature.EMBEDDINGS,
                InteropFeature.AUDIO_TRANSCRIPTION,
                InteropFeature.IMAGE_GENERATION,
                InteropFeature.MODERATION,
                InteropFeature.FILE_OBJECT,
                InteropFeature.UPLOAD_CREATE,
                InteropFeature.BATCH_CREATE,
                InteropFeature.TUNING_CREATE,
                InteropFeature.REALTIME_CLIENT_SECRET
        )) {
            Mockito.when(truthService.resolve(site, snapshot, feature)).thenReturn(new CapabilityResolution(
                    feature,
                    resolution.declaredLevel(),
                    resolution.modelLevel(),
                    resolution.implementedLevel(),
                    resolution.effectiveLevel(),
                    resolution.blockedReasons(),
                    resolution.lossReasons()
            ));
            Mockito.when(truthService.supportsFeature(site, snapshot, feature)).thenReturn(true);
        }
    }

    private UpstreamSiteProfileEntity sampleSite(Long id, String displayName, boolean active) {
        UpstreamSiteProfileEntity entity = new UpstreamSiteProfileEntity();
        entity.setProfileCode("site:openai_direct");
        entity.setDisplayName(displayName);
        entity.setProviderFamily(ProviderFamily.OPENAI);
        entity.setSiteKind(UpstreamSiteKind.OPENAI_DIRECT);
        entity.setAuthStrategy(AuthStrategy.BEARER);
        entity.setPathStrategy(PathStrategy.OPENAI_V1);
        entity.setModelAddressingStrategy(ModelAddressingStrategy.MODEL_NAME);
        entity.setErrorSchemaStrategy(ErrorSchemaStrategy.OPENAI_ERROR);
        entity.setActive(active);
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private SiteCapabilitySnapshotEntity sampleSnapshot(UpstreamSiteProfileEntity site) {
        SiteCapabilitySnapshotEntity snapshot = new SiteCapabilitySnapshotEntity();
        snapshot.setSiteProfile(site);
        snapshot.setSupportedProtocols(List.of("openai", "responses"));
        snapshot.setHealthState("READY");
        snapshot.setStreamTransport("sse");
        snapshot.setFallbackStrategy("provider-native");
        snapshot.setRefreshedAt(Instant.parse("2026-04-13T03:00:00Z"));
        return snapshot;
    }

    private UpstreamCredentialEntity sampleCredential(Long siteProfileId, boolean active, boolean deleted, Instant cooldownUntil) {
        UpstreamCredentialEntity entity = new UpstreamCredentialEntity();
        entity.setSiteProfileId(siteProfileId);
        entity.setActive(active);
        entity.setDeleted(deleted);
        entity.setCooldownUntil(cooldownUntil);
        return entity;
    }
}
