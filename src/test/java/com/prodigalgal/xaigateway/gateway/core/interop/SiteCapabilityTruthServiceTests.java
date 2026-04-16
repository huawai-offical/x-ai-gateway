package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
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
        assertEquals(SupportStatus.BLOCKED, report.supportStatus());
        assertEquals(InteropCapabilityLevel.UNSUPPORTED, report.degradationLevel());
        assertEquals("blocked", report.upstreamObjectMode());
        assertTrue(report.blockedReasons().stream().anyMatch(item -> item.contains("moderation")));
    }

    @Test
    void shouldExposeNativeSupportStatusForNativeFeatureCompatibility() {
        SiteCapabilitySnapshotRepository repository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        Mockito.when(repository.findBySiteProfile_Id(9L)).thenReturn(Optional.of(snapshot(true, true, true, true, true, true, true, true, true, true)));
        SiteCapabilityTruthService service = new SiteCapabilityTruthService(new UpstreamSitePolicyService(), repository);

        FeatureCompatibilityReport report = service.evaluate(
                candidate(9L, ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT),
                new GatewayRequestSemantics(
                        TranslationResourceType.AUDIO,
                        TranslationOperation.AUDIO_TRANSCRIPTION,
                        List.of(InteropFeature.AUDIO_TRANSCRIPTION),
                        true
                )
        );

        assertEquals(ExecutionKind.NATIVE, report.executionKind());
        assertEquals(SupportStatus.NATIVE, report.supportStatus());
        assertEquals(InteropCapabilityLevel.NATIVE, report.degradationLevel());
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
    void shouldTreatVertexChatAndEmbeddingsAsNative() {
        SiteCapabilitySnapshotRepository repository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        Mockito.when(repository.findBySiteProfile_Id(8L)).thenReturn(Optional.of(snapshot(false, true, true, true, true, true, false, true, true, false)));
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
                true,
                false,
                true,
                true,
                false,
                ReasoningTransport.GEMINI_THOUGHTS,
                InteropCapabilityLevel.NATIVE
        );

        assertEquals(InteropCapabilityLevel.NATIVE, service.capabilityLevel(candidate, InteropFeature.CHAT_TEXT));
        assertEquals(InteropCapabilityLevel.NATIVE, service.capabilityLevel(candidate, InteropFeature.IMAGE_INPUT));
        assertEquals(InteropCapabilityLevel.NATIVE, service.capabilityLevel(candidate, InteropFeature.EMBEDDINGS));
        assertEquals(InteropCapabilityLevel.NATIVE, service.capabilityLevel(candidate, InteropFeature.AUDIO_TRANSCRIPTION));
        assertEquals(InteropCapabilityLevel.NATIVE, service.capabilityLevel(candidate, InteropFeature.MODERATION));
        assertEquals(InteropCapabilityLevel.NATIVE, service.capabilityLevel(candidate, InteropFeature.FILE_OBJECT));
    }

    @Test
    void shouldFreezeGeminiFirstSliceSupportStatuses() {
        SiteCapabilitySnapshotRepository repository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        Mockito.when(repository.findBySiteProfile_Id(10L)).thenReturn(Optional.of(snapshot(false, true, true, true, true, false, false, false, false, false)));
        Mockito.when(repository.findBySiteProfile_Id(11L)).thenReturn(Optional.of(snapshot(false, true, true, true, true, true, false, true, true, false)));
        SiteCapabilityTruthService service = new SiteCapabilityTruthService(new UpstreamSitePolicyService(), repository);

        FeatureCompatibilityReport nativeEmbeddings = service.evaluate(
                geminiCandidate(10L, UpstreamSiteKind.GEMINI_DIRECT),
                new GatewayRequestSemantics(
                        TranslationResourceType.EMBEDDING,
                        TranslationOperation.EMBEDDING_CREATE,
                        List.of(InteropFeature.EMBEDDINGS),
                        true
                )
        );
        FeatureCompatibilityReport nativeAudio = service.evaluate(
                geminiCandidate(10L, UpstreamSiteKind.GEMINI_DIRECT),
                new GatewayRequestSemantics(
                        TranslationResourceType.AUDIO,
                        TranslationOperation.AUDIO_TRANSCRIPTION,
                        List.of(InteropFeature.AUDIO_TRANSCRIPTION),
                        true
                )
        );
        FeatureCompatibilityReport nativeImageGeneration = service.evaluate(
                geminiCandidate(10L, UpstreamSiteKind.GEMINI_DIRECT),
                new GatewayRequestSemantics(
                        TranslationResourceType.IMAGE,
                        TranslationOperation.IMAGE_GENERATION,
                        List.of(InteropFeature.IMAGE_GENERATION),
                        true
                )
        );
        FeatureCompatibilityReport nativeModeration = service.evaluate(
                geminiCandidate(10L, UpstreamSiteKind.GEMINI_DIRECT),
                new GatewayRequestSemantics(
                        TranslationResourceType.MODERATION,
                        TranslationOperation.MODERATION_CREATE,
                        List.of(InteropFeature.MODERATION),
                        true
                )
        );
        FeatureCompatibilityReport nativeVertexEmbeddings = service.evaluate(
                geminiCandidate(11L, UpstreamSiteKind.VERTEX_AI),
                new GatewayRequestSemantics(
                        TranslationResourceType.EMBEDDING,
                        TranslationOperation.EMBEDDING_CREATE,
                        List.of(InteropFeature.EMBEDDINGS),
                        true
                )
        );

        assertEquals(SupportStatus.NATIVE, nativeEmbeddings.supportStatus());
        assertEquals(ExecutionKind.NATIVE, nativeEmbeddings.executionKind());
        assertEquals(SupportStatus.NATIVE, nativeAudio.supportStatus());
        assertEquals(SupportStatus.NATIVE, nativeImageGeneration.supportStatus());
        assertEquals(SupportStatus.NATIVE, nativeModeration.supportStatus());
        assertEquals(SupportStatus.NATIVE, nativeVertexEmbeddings.supportStatus());
    }

    @Test
    void shouldExposeNativeWrappedSurfaceForGeminiFileResources() {
        SiteCapabilitySnapshotRepository repository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        SiteCapabilityTruthService service = new SiteCapabilityTruthService(new UpstreamSitePolicyService(), repository);

        SurfaceCompatibilityReport report = service.evaluateSurface(
                siteProfile(UpstreamSiteKind.GEMINI_DIRECT),
                snapshot(false, true, false, false, false, true, false, true, true, false),
                new GatewayRequestSemantics(
                        TranslationResourceType.FILE,
                        TranslationOperation.FILE_CREATE,
                        List.of(InteropFeature.FILE_OBJECT),
                        true
                ),
                new com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendDecision(
                        ExecutionBackend.ORCHESTRATION,
                        List.of(ExecutionBackend.ORCHESTRATION),
                        "test"
                )
        );

        assertEquals(InteropCapabilityLevel.NATIVE, report.executionCapabilityLevel());
        assertTrue(report.blockedReasons().isEmpty());
        assertEquals(
                InteropCapabilityLevel.NATIVE,
                report.featureResolutions().get("file_object").effectiveLevel()
        );
    }

    @Test
    void shouldKeepGeminiUploadsAsOrchestrationSurfaceWhenFeatureTruthBlocked() {
        SiteCapabilitySnapshotRepository repository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        SiteCapabilityTruthService service = new SiteCapabilityTruthService(new UpstreamSitePolicyService(), repository);

        SurfaceCompatibilityReport report = service.evaluateSurface(
                siteProfile(UpstreamSiteKind.GEMINI_DIRECT),
                snapshot(false, true, true, true, true, true, false, true, true, false),
                new GatewayRequestSemantics(
                        TranslationResourceType.UPLOAD,
                        TranslationOperation.UPLOAD_CREATE,
                        List.of(InteropFeature.UPLOAD_CREATE, InteropFeature.FILE_OBJECT),
                        true
                ),
                new com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendDecision(
                        ExecutionBackend.ORCHESTRATION,
                        List.of(ExecutionBackend.ORCHESTRATION),
                        "test"
                )
        );

        assertEquals(InteropCapabilityLevel.NATIVE, report.executionCapabilityLevel());
        assertEquals(
                InteropCapabilityLevel.UNSUPPORTED,
                report.featureResolutions().get("upload_create").effectiveLevel()
        );
        assertEquals(
                InteropCapabilityLevel.NATIVE,
                report.featureResolutions().get("file_object").effectiveLevel()
        );
        assertTrue(report.featureResolutions().get("upload_create").blockedReasons().stream()
                .anyMatch(reason -> reason.contains("gateway-local orchestration surface")));
    }

    @Test
    void shouldKeepGeminiRealtimeBlockedAtSurfaceLevel() {
        SiteCapabilitySnapshotRepository repository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        SiteCapabilityTruthService service = new SiteCapabilityTruthService(new UpstreamSitePolicyService(), repository);

        SurfaceCompatibilityReport report = service.evaluateSurface(
                siteProfile(UpstreamSiteKind.GEMINI_DIRECT),
                snapshot(false, true, true, true, true, true, false, true, true, true),
                new GatewayRequestSemantics(
                        TranslationResourceType.REALTIME,
                        TranslationOperation.REALTIME_CLIENT_SECRET_CREATE,
                        List.of(InteropFeature.REALTIME_CLIENT_SECRET),
                        false
                ),
                new com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendDecision(
                        ExecutionBackend.ORCHESTRATION,
                        List.of(ExecutionBackend.ORCHESTRATION),
                        "test"
                )
        );

        assertEquals(InteropCapabilityLevel.UNSUPPORTED, report.executionCapabilityLevel());
        assertTrue(report.blockedReasons().stream()
                .anyMatch(reason -> reason.contains("Gemini ephemeral/live token")));
    }

    @Test
    void shouldExposeVertexEmbeddingsAsNativeAtSurfaceLevel() {
        SiteCapabilitySnapshotRepository repository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        SiteCapabilityTruthService service = new SiteCapabilityTruthService(new UpstreamSitePolicyService(), repository);

        SurfaceCompatibilityReport report = service.evaluateSurface(
                siteProfile(UpstreamSiteKind.VERTEX_AI),
                snapshot(false, true, true, true, true, true, false, true, true, false),
                new GatewayRequestSemantics(
                        TranslationResourceType.EMBEDDING,
                        TranslationOperation.EMBEDDING_CREATE,
                        List.of(InteropFeature.EMBEDDINGS),
                        true
                ),
                new com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendDecision(
                        ExecutionBackend.NATIVE,
                        List.of(ExecutionBackend.NATIVE),
                        "test"
                )
        );

        assertEquals(InteropCapabilityLevel.NATIVE, report.executionCapabilityLevel());
        assertTrue(report.blockedReasons().isEmpty());
    }

    @Test
    void shouldExposeGeminiAudioAsNativeAtSurfaceLevel() {
        SiteCapabilitySnapshotRepository repository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        SiteCapabilityTruthService service = new SiteCapabilityTruthService(new UpstreamSitePolicyService(), repository);

        SurfaceCompatibilityReport report = service.evaluateSurface(
                siteProfile(UpstreamSiteKind.GEMINI_DIRECT),
                snapshot(false, true, true, true, true, false, false, false, false, false),
                new GatewayRequestSemantics(
                        TranslationResourceType.AUDIO,
                        TranslationOperation.AUDIO_TRANSCRIPTION,
                        List.of(InteropFeature.AUDIO_TRANSCRIPTION),
                        true
                ),
                new com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendDecision(
                        ExecutionBackend.NATIVE,
                        List.of(ExecutionBackend.NATIVE),
                        "test"
                )
        );

        assertEquals(InteropCapabilityLevel.NATIVE, report.executionCapabilityLevel());
        assertTrue(report.blockedReasons().isEmpty());
    }

    @Test
    void shouldTreatAnthropicFilesAndNativeMessageBatchSeparatelyFromGenericBatch() {
        SiteCapabilitySnapshotRepository repository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        Mockito.when(repository.findBySiteProfile_Id(12L)).thenReturn(Optional.of(snapshot(false, false, false, false, false, true, false, true, false, false)));
        SiteCapabilityTruthService service = new SiteCapabilityTruthService(new UpstreamSitePolicyService(), repository);

        CatalogCandidateView candidate = anthropicCandidate(12L);

        assertEquals(InteropCapabilityLevel.NATIVE, service.capabilityLevel(candidate, InteropFeature.FILE_OBJECT));
        assertEquals(InteropCapabilityLevel.UNSUPPORTED, service.capabilityLevel(candidate, InteropFeature.BATCH_CREATE));
        assertEquals(InteropCapabilityLevel.NATIVE, service.capabilityLevel(candidate, InteropFeature.ANTHROPIC_MESSAGE_BATCH));
    }

    @Test
    void shouldExposeAnthropicFileAndNativeBatchSurfaceTruth() {
        SiteCapabilitySnapshotRepository repository = Mockito.mock(SiteCapabilitySnapshotRepository.class);
        SiteCapabilityTruthService service = new SiteCapabilityTruthService(new UpstreamSitePolicyService(), repository);

        SurfaceCompatibilityReport fileReport = service.evaluateSurface(
                siteProfile(UpstreamSiteKind.ANTHROPIC_DIRECT),
                snapshot(false, false, false, false, false, true, false, true, false, false),
                new GatewayRequestSemantics(
                        TranslationResourceType.FILE,
                        TranslationOperation.FILE_CREATE,
                        List.of(InteropFeature.FILE_OBJECT),
                        true
                ),
                new com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendDecision(
                        ExecutionBackend.ORCHESTRATION,
                        List.of(ExecutionBackend.ORCHESTRATION),
                        "test"
                )
        );
        SurfaceCompatibilityReport batchReport = service.evaluateSurface(
                siteProfile(UpstreamSiteKind.ANTHROPIC_DIRECT),
                snapshot(false, false, false, false, false, true, false, true, false, false),
                new GatewayRequestSemantics(
                        TranslationResourceType.BATCH,
                        TranslationOperation.ANTHROPIC_MESSAGE_BATCH_CREATE,
                        List.of(InteropFeature.ANTHROPIC_MESSAGE_BATCH),
                        true
                ),
                new com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendDecision(
                        ExecutionBackend.ORCHESTRATION,
                        List.of(ExecutionBackend.ORCHESTRATION),
                        "test"
                )
        );
        SurfaceCompatibilityReport genericBatchReport = service.evaluateSurface(
                siteProfile(UpstreamSiteKind.ANTHROPIC_DIRECT),
                snapshot(false, false, false, false, false, true, false, true, false, false),
                new GatewayRequestSemantics(
                        TranslationResourceType.BATCH,
                        TranslationOperation.BATCH_CREATE,
                        List.of(InteropFeature.BATCH_CREATE),
                        true
                ),
                new com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendDecision(
                        ExecutionBackend.ORCHESTRATION,
                        List.of(ExecutionBackend.ORCHESTRATION),
                        "test"
                )
        );

        assertEquals(InteropCapabilityLevel.NATIVE, fileReport.executionCapabilityLevel());
        assertEquals(InteropCapabilityLevel.NATIVE, batchReport.executionCapabilityLevel());
        assertEquals(InteropCapabilityLevel.UNSUPPORTED, genericBatchReport.executionCapabilityLevel());
        assertTrue(genericBatchReport.blockedReasons().stream().anyMatch(reason -> reason.contains("Message Batches")));
    }

    private CatalogCandidateView geminiCandidate(Long siteProfileId, UpstreamSiteKind siteKind) {
        return new CatalogCandidateView(
                301L,
                "gemini",
                ProviderType.GEMINI_DIRECT,
                siteProfileId,
                ProviderFamily.GEMINI,
                siteKind,
                AuthStrategy.BEARER,
                PathStrategy.GEMINI_V1BETA_MODELS,
                ErrorSchemaStrategy.GEMINI_ERROR,
                "https://example.com",
                "gemini-2.5-pro",
                "gemini-2.5-pro",
                List.of("google_native"),
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                false,
                ReasoningTransport.GEMINI_THOUGHTS,
                InteropCapabilityLevel.NATIVE
        );
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

    private CatalogCandidateView anthropicCandidate(Long siteProfileId) {
        return new CatalogCandidateView(
                401L,
                "anthropic",
                ProviderType.ANTHROPIC_DIRECT,
                siteProfileId,
                ProviderFamily.ANTHROPIC,
                UpstreamSiteKind.ANTHROPIC_DIRECT,
                AuthStrategy.BEARER,
                PathStrategy.ANTHROPIC_V1_MESSAGES,
                ErrorSchemaStrategy.ANTHROPIC_ERROR,
                "https://api.anthropic.com",
                "claude-sonnet-4",
                "claude-sonnet-4",
                List.of("anthropic_native"),
                true,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                ReasoningTransport.ANTHROPIC,
                InteropCapabilityLevel.NATIVE
        );
    }

    private com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity siteProfile(UpstreamSiteKind siteKind) {
        com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity entity =
                new com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity();
        entity.setSiteKind(siteKind);
        return entity;
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
