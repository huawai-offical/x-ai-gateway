package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.catalog.CredentialModelDiscoveryService;
import com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendPolicyService;
import com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendDecision;
import com.prodigalgal.xaigateway.gateway.core.interop.CapabilityResolution;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthService;
import com.prodigalgal.xaigateway.gateway.core.interop.SupportStatus;
import com.prodigalgal.xaigateway.gateway.core.interop.SurfaceCompatibilityReport;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelAddressingStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.gateway.core.site.UpstreamSitePolicyService;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteCapabilitySnapshotEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.admin.api.ProviderSiteRequest;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderSiteAdminServiceTests {

    @Test
    void shouldRejectUnsafeProviderSiteBaseUrl() {
        UpstreamSiteProfileRepository profileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        SiteModelCapabilityRepository modelCapabilityRepository = Mockito.mock(SiteModelCapabilityRepository.class);
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        ProviderSiteRegistryService providerSiteRegistryService = Mockito.mock(ProviderSiteRegistryService.class);
        CredentialModelDiscoveryService credentialModelDiscoveryService = Mockito.mock(CredentialModelDiscoveryService.class);
        SiteCapabilityTruthService truthService = Mockito.mock(SiteCapabilityTruthService.class);
        SecurityPolicyService securityPolicyService = Mockito.mock(SecurityPolicyService.class);
        Mockito.when(providerSiteRegistryService.policy(UpstreamSiteKind.OPENAI_DIRECT))
                .thenReturn(new UpstreamSitePolicyService().policy(UpstreamSiteKind.OPENAI_DIRECT));
        Mockito.doThrow(new IllegalArgumentException("SSRF 防护阻断私网或本机地址。"))
                .when(securityPolicyService)
                .assertUrlAllowed("http://127.0.0.1:8080");

        ProviderSiteAdminService service = new ProviderSiteAdminService(
                profileRepository,
                snapshotRepository,
                modelCapabilityRepository,
                credentialRepository,
                providerSiteRegistryService,
                credentialModelDiscoveryService,
                truthService,
                new ExecutionBackendPolicyService(),
                securityPolicyService
        );

        assertThrows(IllegalArgumentException.class, () -> service.create(new ProviderSiteRequest(
                "unsafe",
                "Unsafe",
                UpstreamSiteKind.OPENAI_DIRECT,
                "http://127.0.0.1:8080",
                null,
                true
        )));
        Mockito.verify(profileRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void shouldRejectDeletingSiteWithLinkedCredentials() {
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
        Mockito.when(profileRepository.findById(1L)).thenReturn(Optional.of(site));
        Mockito.when(credentialRepository.countBySiteProfileIdAndDeletedFalse(1L)).thenReturn(1L);

        assertThrows(IllegalArgumentException.class, () -> service.delete(1L));
        Mockito.verify(profileRepository, Mockito.never()).delete(site);
        Mockito.verify(modelCapabilityRepository, Mockito.never()).deleteAllBySiteProfile_Id(1L);
    }

    @Test
    void shouldDeleteSiteAndCleanCapabilityData() {
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
        Mockito.when(profileRepository.findById(1L)).thenReturn(Optional.of(site));
        Mockito.when(credentialRepository.countBySiteProfileIdAndDeletedFalse(1L)).thenReturn(0L);
        Mockito.when(snapshotRepository.findBySiteProfile_Id(1L)).thenReturn(Optional.of(snapshot));

        service.delete(1L);

        Mockito.verify(modelCapabilityRepository).deleteAllBySiteProfile_Id(1L);
        Mockito.verify(snapshotRepository).delete(snapshot);
        Mockito.verify(profileRepository).delete(site);
    }

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



    @Test
    void shouldExposeAnthropicFileSurfaceOnlyForFunctionalApiScope() {
        UpstreamSiteProfileRepository profileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        SiteModelCapabilityRepository modelCapabilityRepository = Mockito.mock(SiteModelCapabilityRepository.class);
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        ProviderSiteRegistryService providerSiteRegistryService = Mockito.mock(ProviderSiteRegistryService.class);
        CredentialModelDiscoveryService credentialModelDiscoveryService = Mockito.mock(CredentialModelDiscoveryService.class);
        SiteCapabilityTruthService truthService = new SiteCapabilityTruthService(
                new UpstreamSitePolicyService(),
                snapshotRepository
        );

        ProviderSiteAdminService service = new ProviderSiteAdminService(
                profileRepository,
                snapshotRepository,
                modelCapabilityRepository,
                credentialRepository,
                providerSiteRegistryService,
                credentialModelDiscoveryService,
                truthService
        );

        UpstreamSiteProfileEntity site = sampleAnthropicSite(3L);
        SiteCapabilitySnapshotEntity snapshot = anthropicSnapshot(site);

        Mockito.when(profileRepository.findById(3L)).thenReturn(Optional.of(site));
        Mockito.when(snapshotRepository.findBySiteProfile_Id(3L)).thenReturn(Optional.of(snapshot));
        Mockito.when(modelCapabilityRepository.findAllBySiteProfile_IdOrderByModelKeyAsc(3L)).thenReturn(List.of());
        Mockito.when(credentialRepository.findAllBySiteProfileIdAndDeletedFalseAndActiveTrueOrderByCreatedAtDesc(3L))
                .thenReturn(List.of());

        ProviderSiteResponse response = service.get(3L);

        assertEquals("native", response.features().get("file_object").supportStatus());
        assertFalse(response.features().containsKey("batch_create"));
        assertFalse(response.features().containsKey("anthropic_message_batch"));
        assertEquals(SupportStatus.NATIVE, response.surfaces().get("file_create").supportStatus());
        assertFalse(response.surfaces().containsKey("batch_create"));
        assertFalse(response.surfaces().containsKey("anthropic_message_batch_create"));
    }


    @Test
    void shouldExposeAllMatrixFeaturesAndAvoidJinaGeneralChatOverclaim() {
        UpstreamSiteProfileRepository profileRepository = Mockito.mock(UpstreamSiteProfileRepository.class);
        SiteCapabilitySnapshotRepository snapshotRepository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        SiteModelCapabilityRepository modelCapabilityRepository = Mockito.mock(SiteModelCapabilityRepository.class);
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        ProviderSiteRegistryService providerSiteRegistryService = Mockito.mock(ProviderSiteRegistryService.class);
        CredentialModelDiscoveryService credentialModelDiscoveryService = Mockito.mock(CredentialModelDiscoveryService.class);
        SiteCapabilityTruthService truthService = new SiteCapabilityTruthService(
                new UpstreamSitePolicyService(),
                snapshotRepository
        );

        ProviderSiteAdminService service = new ProviderSiteAdminService(
                profileRepository,
                snapshotRepository,
                modelCapabilityRepository,
                credentialRepository,
                providerSiteRegistryService,
                credentialModelDiscoveryService,
                truthService
        );

        UpstreamSiteProfileEntity site = sampleJinaSite(6L);
        SiteCapabilitySnapshotEntity snapshot = sampleSnapshot(site);
        snapshot.setSupportsEmbeddings(true);

        Mockito.when(profileRepository.findById(6L)).thenReturn(Optional.of(site));
        Mockito.when(snapshotRepository.findBySiteProfile_Id(6L)).thenReturn(Optional.of(snapshot));
        Mockito.when(modelCapabilityRepository.findAllBySiteProfile_IdOrderByModelKeyAsc(6L)).thenReturn(List.of());
        Mockito.when(credentialRepository.findAllBySiteProfileIdAndDeletedFalseAndActiveTrueOrderByCreatedAtDesc(6L))
                .thenReturn(List.of());

        ProviderSiteResponse response = service.get(6L);

        assertEquals("blocked", response.features().get("chat_text").supportStatus());
        assertEquals("blocked", response.features().get("tools").supportStatus());
        assertEquals("native", response.features().get("embeddings").supportStatus());
        assertEquals("native", response.features().get("rerank").supportStatus());
        assertTrue(response.features().containsKey("video_generation"));
        assertTrue(response.features().containsKey("music_generation"));
        assertTrue(response.features().containsKey("web_search"));
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
                InteropFeature.CHAT_TEXT,
                InteropFeature.TOOLS,
                InteropFeature.IMAGE_INPUT,
                InteropFeature.FILE_INPUT,
                InteropFeature.RESPONSE_OBJECT,
                InteropFeature.EMBEDDINGS,
                InteropFeature.REASONING,
                InteropFeature.AUDIO_TRANSCRIPTION,
                InteropFeature.AUDIO_SPEECH,
                InteropFeature.IMAGE_GENERATION,
                InteropFeature.MODERATION,
                InteropFeature.FILE_OBJECT,
                InteropFeature.UPLOAD_CREATE,
                InteropFeature.RERANK,
                InteropFeature.VIDEO_GENERATION,
                InteropFeature.MUSIC_GENERATION,
                InteropFeature.WEB_SEARCH
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
        Mockito.when(truthService.evaluateSurface(Mockito.eq(site), Mockito.eq(snapshot), Mockito.any(GatewayRequestSemantics.class), Mockito.any(ExecutionBackendDecision.class)))
                .thenAnswer(invocation -> {
                    GatewayRequestSemantics semantics = invocation.getArgument(2);
                    java.util.LinkedHashMap<String, CapabilityResolution> featureResolutions = new java.util.LinkedHashMap<>();
                    for (InteropFeature feature : semantics.requiredFeatures()) {
                        featureResolutions.put(feature.wireName(), new CapabilityResolution(
                                feature,
                                InteropCapabilityLevel.NATIVE,
                                InteropCapabilityLevel.NATIVE,
                                InteropCapabilityLevel.NATIVE,
                                InteropCapabilityLevel.NATIVE,
                                List.of(),
                                List.of()
                        ));
                    }
                    return new SurfaceCompatibilityReport(
                            java.util.Map.copyOf(featureResolutions),
                            InteropCapabilityLevel.NATIVE,
                            List.of(),
                            List.of()
                    );
                });
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

    private UpstreamSiteProfileEntity sampleGeminiSite(Long id, UpstreamSiteKind siteKind) {
        UpstreamSiteProfileEntity entity = new UpstreamSiteProfileEntity();
        entity.setProfileCode("site:" + siteKind.name().toLowerCase());
        entity.setDisplayName(siteKind.name());
        entity.setProviderFamily(ProviderFamily.GEMINI);
        entity.setSiteKind(siteKind);
        entity.setAuthStrategy(siteKind == UpstreamSiteKind.GEMINI_DIRECT ? AuthStrategy.API_KEY_QUERY : AuthStrategy.BEARER);
        entity.setPathStrategy(PathStrategy.GEMINI_V1BETA_MODELS);
        entity.setModelAddressingStrategy(ModelAddressingStrategy.MODEL_NAME);
        entity.setErrorSchemaStrategy(ErrorSchemaStrategy.GEMINI_ERROR);
        entity.setActive(true);
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private UpstreamSiteProfileEntity sampleAnthropicSite(Long id) {
        UpstreamSiteProfileEntity entity = new UpstreamSiteProfileEntity();
        entity.setProfileCode("site:anthropic_direct");
        entity.setDisplayName("ANTHROPIC_DIRECT");
        entity.setProviderFamily(ProviderFamily.ANTHROPIC);
        entity.setSiteKind(UpstreamSiteKind.ANTHROPIC_DIRECT);
        entity.setAuthStrategy(AuthStrategy.BEARER);
        entity.setPathStrategy(PathStrategy.ANTHROPIC_V1_MESSAGES);
        entity.setModelAddressingStrategy(ModelAddressingStrategy.MODEL_NAME);
        entity.setErrorSchemaStrategy(ErrorSchemaStrategy.ANTHROPIC_ERROR);
        entity.setActive(true);
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private UpstreamSiteProfileEntity sampleOpenAiCompatibleSite(Long id) {
        UpstreamSiteProfileEntity entity = new UpstreamSiteProfileEntity();
        entity.setProfileCode("site:openai_compatible_generic");
        entity.setDisplayName("OPENAI_COMPATIBLE_GENERIC");
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

    private UpstreamSiteProfileEntity sampleJinaSite(Long id) {
        UpstreamSiteProfileEntity entity = new UpstreamSiteProfileEntity();
        entity.setProfileCode("site:jina");
        entity.setDisplayName("JINA");
        entity.setProviderFamily(ProviderFamily.OPENAI);
        entity.setSiteKind(UpstreamSiteKind.JINA);
        entity.setAuthStrategy(AuthStrategy.BEARER);
        entity.setPathStrategy(PathStrategy.OPENAI_V1);
        entity.setModelAddressingStrategy(ModelAddressingStrategy.MODEL_NAME);
        entity.setErrorSchemaStrategy(ErrorSchemaStrategy.OPENAI_ERROR);
        entity.setActive(true);
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

    private SiteCapabilitySnapshotEntity geminiSnapshot(UpstreamSiteProfileEntity site, boolean supportsEmbeddings) {
        SiteCapabilitySnapshotEntity snapshot = new SiteCapabilitySnapshotEntity();
        snapshot.setSiteProfile(site);
        snapshot.setSupportedProtocols(List.of("google_native"));
        snapshot.setHealthState("READY");
        snapshot.setSupportsEmbeddings(supportsEmbeddings);
        snapshot.setSupportsAudio(true);
        snapshot.setSupportsImages(true);
        snapshot.setSupportsModeration(true);
        snapshot.setSupportsFiles(site.getSiteKind() == UpstreamSiteKind.GEMINI_DIRECT || site.getSiteKind() == UpstreamSiteKind.VERTEX_AI);
        snapshot.setSupportsUploads(false);
        snapshot.setRefreshedAt(Instant.parse("2026-04-15T02:00:00Z"));
        return snapshot;
    }

    private SiteCapabilitySnapshotEntity anthropicSnapshot(UpstreamSiteProfileEntity site) {
        SiteCapabilitySnapshotEntity snapshot = new SiteCapabilitySnapshotEntity();
        snapshot.setSiteProfile(site);
        snapshot.setSupportedProtocols(List.of("anthropic_native"));
        snapshot.setHealthState("READY");
        snapshot.setSupportsResponses(true);
        snapshot.setSupportsFiles(true);
        snapshot.setSupportsEmbeddings(false);
        snapshot.setSupportsAudio(false);
        snapshot.setSupportsImages(false);
        snapshot.setSupportsModeration(false);
        snapshot.setSupportsUploads(false);
        snapshot.setRefreshedAt(Instant.parse("2026-04-16T02:00:00Z"));
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
