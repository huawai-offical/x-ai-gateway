package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.gateway.core.site.UpstreamSitePolicyService;
import com.prodigalgal.xaigateway.infra.persistence.entity.SiteCapabilitySnapshotEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.SiteCapabilitySnapshotRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteCapabilityTruthServiceTests {

    @Test
    void shouldReturnNativeForOpenAiAudioAndUnsupportedForOpenAiCompatibleAudio() {
        SiteCapabilitySnapshotRepository repository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        Mockito.when(repository.findBySiteProfile_Id(1L)).thenReturn(Optional.of(snapshot(true, true, true, true, true, true, true, true, true, true)));
        Mockito.when(repository.findBySiteProfile_Id(2L)).thenReturn(Optional.of(snapshot(true, true, false, false, false, false, false, false, false, false)));

        SiteCapabilityTruthService service = new SiteCapabilityTruthService(new UpstreamSitePolicyService(), repository);

        assertEquals(
                InteropCapabilityLevel.NATIVE,
                service.capabilityLevel(candidate(1L, ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT), InteropFeature.AUDIO_TRANSCRIPTION)
        );
        assertEquals(
                InteropCapabilityLevel.UNSUPPORTED,
                service.capabilityLevel(candidate(2L, ProviderType.OPENAI_COMPATIBLE, UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC), InteropFeature.AUDIO_TRANSCRIPTION)
        );
    }

    @Test
    void shouldReturnNativeForFileObjectOnlyWhenSnapshotAndSiteBothSupportIt() {
        SiteCapabilitySnapshotRepository repository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        Mockito.when(repository.findBySiteProfile_Id(3L)).thenReturn(Optional.of(snapshot(true, true, false, false, false, true, false, false, false, false)));
        Mockito.when(repository.findBySiteProfile_Id(4L)).thenReturn(Optional.of(snapshot(true, true, false, false, false, false, false, false, false, false)));

        SiteCapabilityTruthService service = new SiteCapabilityTruthService(new UpstreamSitePolicyService(), repository);

        assertEquals(
                InteropCapabilityLevel.NATIVE,
                service.capabilityLevel(candidate(3L, ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT), InteropFeature.FILE_OBJECT)
        );
        assertEquals(
                InteropCapabilityLevel.UNSUPPORTED,
                service.capabilityLevel(candidate(4L, ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT), InteropFeature.FILE_OBJECT)
        );
    }

    @Test
    void shouldBuildBlockedExecutionPlanForUnsupportedFeature() {
        SiteCapabilitySnapshotRepository repository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        Mockito.when(repository.findBySiteProfile_Id(5L)).thenReturn(Optional.of(snapshot(true, true, false, false, false, false, false, false, false, false)));
        SiteCapabilityTruthService service = new SiteCapabilityTruthService(new UpstreamSitePolicyService(), repository);

        CatalogCandidateView candidate = candidate(5L, ProviderType.OPENAI_COMPATIBLE, UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC);
        FeatureCompatibilityReport report = service.evaluate(
                candidate,
                new GatewayRequestSemantics(
                        TranslationResourceType.MODERATION,
                        TranslationOperation.MODERATION_CREATE,
                        List.of(InteropFeature.MODERATION),
                        true
                )
        );

        assertEquals(ExecutionKind.BLOCKED, report.executionKind());
        assertEquals("blocked", report.upstreamObjectMode());
        assertTrue(report.blockedReasons().stream().anyMatch(item -> item.contains("moderation")));
    }

    @Test
    void shouldUseCandidateLevelOllamaToolAndImageCapabilities() {
        SiteCapabilitySnapshotRepository repository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        Mockito.when(repository.findBySiteProfile_Id(6L)).thenReturn(Optional.of(snapshot(true, false, false, false, false, false, false, false, false, false)));
        SiteCapabilityTruthService service = new SiteCapabilityTruthService(new UpstreamSitePolicyService(), repository);

        CatalogCandidateView capableCandidate = new CatalogCandidateView(
                101L,
                "ollama-capable",
                ProviderType.OLLAMA_DIRECT,
                6L,
                ProviderFamily.OLLAMA,
                UpstreamSiteKind.OLLAMA_DIRECT,
                AuthStrategy.UNSUPPORTED,
                PathStrategy.OLLAMA_API_CHAT,
                ErrorSchemaStrategy.OLLAMA_ERROR,
                "http://localhost:11434",
                "qwen3",
                "qwen3",
                List.of("openai", "responses"),
                true,
                true,
                true,
                false,
                false,
                true,
                true,
                false,
                ReasoningTransport.OLLAMA_THINKING,
                InteropCapabilityLevel.NATIVE
        );
        CatalogCandidateView blockedCandidate = new CatalogCandidateView(
                102L,
                "ollama-blocked",
                ProviderType.OLLAMA_DIRECT,
                6L,
                ProviderFamily.OLLAMA,
                UpstreamSiteKind.OLLAMA_DIRECT,
                AuthStrategy.UNSUPPORTED,
                PathStrategy.OLLAMA_API_CHAT,
                ErrorSchemaStrategy.OLLAMA_ERROR,
                "http://localhost:11434",
                "llama3",
                "llama3",
                List.of("openai", "responses"),
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                ReasoningTransport.NONE,
                InteropCapabilityLevel.NATIVE
        );

        assertEquals(InteropCapabilityLevel.NATIVE, service.capabilityLevel(capableCandidate, InteropFeature.TOOLS));
        assertEquals(InteropCapabilityLevel.NATIVE, service.capabilityLevel(capableCandidate, InteropFeature.IMAGE_INPUT));
        assertEquals(InteropCapabilityLevel.NATIVE, service.capabilityLevel(capableCandidate, InteropFeature.REASONING));
        assertEquals(InteropCapabilityLevel.UNSUPPORTED, service.capabilityLevel(blockedCandidate, InteropFeature.TOOLS));
        assertEquals(InteropCapabilityLevel.UNSUPPORTED, service.capabilityLevel(blockedCandidate, InteropFeature.IMAGE_INPUT));
        assertEquals(InteropCapabilityLevel.UNSUPPORTED, service.capabilityLevel(blockedCandidate, InteropFeature.FILE_INPUT));
    }

    @Test
    void shouldTreatCohereChatAsNativeButModerationAsUnsupported() {
        SiteCapabilitySnapshotRepository repository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        Mockito.when(repository.findBySiteProfile_Id(7L)).thenReturn(Optional.of(snapshot(false, true, false, false, false, false, false, false, false, false)));
        SiteCapabilityTruthService service = new SiteCapabilityTruthService(new UpstreamSitePolicyService(), repository);

        CatalogCandidateView candidate = new CatalogCandidateView(
                201L,
                "cohere",
                ProviderType.OPENAI_COMPATIBLE,
                7L,
                ProviderFamily.OPENAI,
                UpstreamSiteKind.COHERE,
                AuthStrategy.BEARER,
                PathStrategy.OPENAI_V1,
                ErrorSchemaStrategy.OPENAI_ERROR,
                "https://api.cohere.ai/compatibility/v1",
                "command-a-03-2025",
                "command-a-03-2025",
                List.of("openai"),
                true,
                true,
                false,
                true,
                false,
                false,
                false,
                false,
                ReasoningTransport.NONE,
                InteropCapabilityLevel.NATIVE
        );

        assertEquals(InteropCapabilityLevel.NATIVE, service.capabilityLevel(candidate, InteropFeature.CHAT_TEXT));
        assertEquals(InteropCapabilityLevel.UNSUPPORTED, service.capabilityLevel(candidate, InteropFeature.MODERATION));
    }

    @Test
    void shouldTreatVertexChatAsNativeButEmbeddingsAsUnsupported() {
        SiteCapabilitySnapshotRepository repository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        Mockito.when(repository.findBySiteProfile_Id(8L)).thenReturn(Optional.of(snapshot(false, false, false, false, false, false, false, false, false, false)));
        SiteCapabilityTruthService service = new SiteCapabilityTruthService(new UpstreamSitePolicyService(), repository);

        CatalogCandidateView candidate = new CatalogCandidateView(
                202L,
                "vertex",
                ProviderType.GEMINI_DIRECT,
                8L,
                ProviderFamily.GEMINI,
                UpstreamSiteKind.VERTEX_AI,
                AuthStrategy.BEARER,
                PathStrategy.GEMINI_V1BETA_MODELS,
                ErrorSchemaStrategy.GEMINI_ERROR,
                "https://aiplatform.googleapis.com/v1/projects/demo/locations/us-central1/endpoints/openapi",
                "gemini-2.5-pro",
                "gemini-2.5-pro",
                List.of("google_native"),
                true,
                true,
                true,
                false,
                false,
                true,
                true,
                false,
                ReasoningTransport.GEMINI_THOUGHTS,
                InteropCapabilityLevel.NATIVE
        );

        assertEquals(InteropCapabilityLevel.NATIVE, service.capabilityLevel(candidate, InteropFeature.CHAT_TEXT));
        assertEquals(InteropCapabilityLevel.NATIVE, service.capabilityLevel(candidate, InteropFeature.IMAGE_INPUT));
        assertEquals(InteropCapabilityLevel.UNSUPPORTED, service.capabilityLevel(candidate, InteropFeature.EMBEDDINGS));
    }

    private CatalogCandidateView candidate(Long siteProfileId, ProviderType providerType, UpstreamSiteKind siteKind) {
        return new CatalogCandidateView(
                101L,
                "candidate",
                providerType,
                siteProfileId,
                ProviderFamily.OPENAI,
                siteKind,
                AuthStrategy.BEARER,
                PathStrategy.OPENAI_V1,
                ErrorSchemaStrategy.OPENAI_ERROR,
                "https://example.com",
                "model-a",
                "model-a",
                List.of("openai", "responses"),
                true,
                true,
                true,
                true,
                true,
                true,
                ReasoningTransport.OPENAI_CHAT,
                InteropCapabilityLevel.NATIVE
        );
    }

    private SiteCapabilitySnapshotEntity snapshot(
            boolean responses,
            boolean embeddings,
            boolean audio,
            boolean images,
            boolean moderation,
            boolean files,
            boolean uploads,
            boolean batches,
            boolean tuning,
            boolean realtime) {
        SiteCapabilitySnapshotEntity entity = new SiteCapabilitySnapshotEntity();
        entity.setSupportsResponses(responses);
        entity.setSupportsEmbeddings(embeddings);
        entity.setSupportsAudio(audio);
        entity.setSupportsImages(images);
        entity.setSupportsModeration(moderation);
        entity.setSupportsFiles(files);
        entity.setSupportsUploads(uploads);
        entity.setSupportsBatches(batches);
        entity.setSupportsTuning(tuning);
        entity.setSupportsRealtime(realtime);
        entity.setSupportedProtocols(List.of("openai", "responses"));
        entity.setAuthStrategy(AuthStrategy.BEARER);
        entity.setPathStrategy(PathStrategy.OPENAI_V1);
        entity.setErrorSchemaStrategy(ErrorSchemaStrategy.OPENAI_ERROR);
        entity.setHealthState("READY");
        return entity;
    }
}
