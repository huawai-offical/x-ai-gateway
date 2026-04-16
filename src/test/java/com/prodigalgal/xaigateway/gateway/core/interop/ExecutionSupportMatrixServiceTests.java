package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecutionSupportMatrixServiceTests {

    private final ExecutionSupportMatrixService service = new ExecutionSupportMatrixService();

    @Test
    void shouldResolveNativeStatusForNativeBackend() {
        assertEquals(
                SupportStatus.NATIVE,
                service.supportStatus(ExecutionBackend.NATIVE, InteropCapabilityLevel.NATIVE, List.of())
        );
    }

    @Test
    void shouldResolvePassthroughStatusForPassthroughBackend() {
        assertEquals(
                SupportStatus.PASSTHROUGH,
                service.supportStatus(ExecutionBackend.PASSTHROUGH, InteropCapabilityLevel.NATIVE, List.of())
        );
    }

    @Test
    void shouldResolveOrchestrationStatusForOrchestrationBackend() {
        assertEquals(
                SupportStatus.ORCHESTRATION,
                service.supportStatus(ExecutionBackend.ORCHESTRATION, InteropCapabilityLevel.NATIVE, List.of())
        );
    }

    @Test
    void shouldResolveDegradedStatusForLossyCapability() {
        assertEquals(
                SupportStatus.DEGRADED,
                service.supportStatus(ExecutionBackend.NATIVE, InteropCapabilityLevel.EMULATED, List.of())
        );
        assertEquals(
                InteropCapabilityLevel.EMULATED,
                service.degradationLevel(InteropCapabilityLevel.EMULATED, List.of())
        );
    }

    @Test
    void shouldResolveBlockedStatusWhenBlockersExist() {
        assertEquals(
                SupportStatus.BLOCKED,
                service.supportStatus(ExecutionBackend.ORCHESTRATION, InteropCapabilityLevel.NATIVE, List.of("missing feature"))
        );
        assertEquals(
                InteropCapabilityLevel.UNSUPPORTED,
                service.degradationLevel(InteropCapabilityLevel.NATIVE, List.of("missing feature"))
        );
    }

    @Test
    void shouldFreezeGeminiAndVertexFirstSliceMatrix() {
        GatewayRequestSemantics embeddingsSemantics = new GatewayRequestSemantics(
                TranslationResourceType.EMBEDDING,
                TranslationOperation.EMBEDDING_CREATE,
                List.of(InteropFeature.EMBEDDINGS),
                true
        );
        GatewayRequestSemantics audioSemantics = new GatewayRequestSemantics(
                TranslationResourceType.AUDIO,
                TranslationOperation.AUDIO_TRANSCRIPTION,
                List.of(InteropFeature.AUDIO_TRANSCRIPTION),
                true
        );
        GatewayRequestSemantics audioSpeechSemantics = new GatewayRequestSemantics(
                TranslationResourceType.AUDIO,
                TranslationOperation.AUDIO_SPEECH,
                List.of(InteropFeature.AUDIO_SPEECH),
                true
        );
        GatewayRequestSemantics batchSemantics = new GatewayRequestSemantics(
                TranslationResourceType.BATCH,
                TranslationOperation.BATCH_CREATE,
                List.of(InteropFeature.BATCH_CREATE),
                true
        );
        GatewayRequestSemantics fileSemantics = new GatewayRequestSemantics(
                TranslationResourceType.FILE,
                TranslationOperation.FILE_CREATE,
                List.of(InteropFeature.FILE_OBJECT),
                true
        );
        GatewayRequestSemantics uploadSemantics = new GatewayRequestSemantics(
                TranslationResourceType.UPLOAD,
                TranslationOperation.UPLOAD_CREATE,
                List.of(InteropFeature.UPLOAD_CREATE),
                true
        );
        GatewayRequestSemantics tuningSemantics = new GatewayRequestSemantics(
                TranslationResourceType.TUNING,
                TranslationOperation.TUNING_CREATE,
                List.of(InteropFeature.TUNING_CREATE),
                true
        );
        GatewayRequestSemantics realtimeSemantics = new GatewayRequestSemantics(
                TranslationResourceType.REALTIME,
                TranslationOperation.REALTIME_CLIENT_SECRET_CREATE,
                List.of(InteropFeature.REALTIME_CLIENT_SECRET),
                true
        );
        GatewayRequestSemantics imageSemantics = new GatewayRequestSemantics(
                TranslationResourceType.IMAGE,
                TranslationOperation.IMAGE_GENERATION,
                List.of(InteropFeature.IMAGE_GENERATION),
                true
        );
        GatewayRequestSemantics imageEditSemantics = new GatewayRequestSemantics(
                TranslationResourceType.IMAGE,
                TranslationOperation.IMAGE_EDIT,
                List.of(InteropFeature.IMAGE_EDIT),
                true
        );
        GatewayRequestSemantics moderationSemantics = new GatewayRequestSemantics(
                TranslationResourceType.MODERATION,
                TranslationOperation.MODERATION_CREATE,
                List.of(InteropFeature.MODERATION),
                true
        );

        assertEquals(
                InteropCapabilityLevel.NATIVE,
                service.implementedLevel(geminiCandidate(UpstreamSiteKind.GEMINI_DIRECT), embeddingsSemantics, InteropFeature.EMBEDDINGS)
        );
        assertEquals(
                InteropCapabilityLevel.UNSUPPORTED,
                service.implementedLevel(geminiCandidate(UpstreamSiteKind.VERTEX_AI), embeddingsSemantics, InteropFeature.EMBEDDINGS)
        );
        assertEquals(
                InteropCapabilityLevel.NATIVE,
                service.implementedLevel(geminiCandidate(UpstreamSiteKind.GEMINI_DIRECT), audioSemantics, InteropFeature.AUDIO_TRANSCRIPTION)
        );
        assertEquals(
                InteropCapabilityLevel.NATIVE,
                service.implementedLevel(geminiCandidate(UpstreamSiteKind.GEMINI_DIRECT), audioSpeechSemantics, InteropFeature.AUDIO_SPEECH)
        );
        assertEquals(
                InteropCapabilityLevel.NATIVE,
                service.implementedLevel(geminiCandidate(UpstreamSiteKind.GEMINI_DIRECT), imageSemantics, InteropFeature.IMAGE_GENERATION)
        );
        assertEquals(
                InteropCapabilityLevel.UNSUPPORTED,
                service.implementedLevel(geminiCandidate(UpstreamSiteKind.GEMINI_DIRECT), imageEditSemantics, InteropFeature.IMAGE_EDIT)
        );
        assertEquals(
                InteropCapabilityLevel.NATIVE,
                service.implementedLevel(geminiCandidate(UpstreamSiteKind.GEMINI_DIRECT), moderationSemantics, InteropFeature.MODERATION)
        );
        assertEquals(
                InteropCapabilityLevel.NATIVE,
                service.implementedLevel(geminiCandidate(UpstreamSiteKind.GEMINI_DIRECT), fileSemantics, InteropFeature.FILE_OBJECT)
        );
        assertEquals(
                InteropCapabilityLevel.UNSUPPORTED,
                service.implementedLevel(geminiCandidate(UpstreamSiteKind.GEMINI_DIRECT), uploadSemantics, InteropFeature.UPLOAD_CREATE)
        );
        assertEquals(
                InteropCapabilityLevel.NATIVE,
                service.implementedLevel(geminiCandidate(UpstreamSiteKind.GEMINI_DIRECT), batchSemantics, InteropFeature.BATCH_CREATE)
        );
        assertEquals(
                InteropCapabilityLevel.NATIVE,
                service.implementedLevel(geminiCandidate(UpstreamSiteKind.GEMINI_DIRECT), tuningSemantics, InteropFeature.TUNING_CREATE)
        );
        assertEquals(
                InteropCapabilityLevel.UNSUPPORTED,
                service.implementedLevel(geminiCandidate(UpstreamSiteKind.GEMINI_DIRECT), realtimeSemantics, InteropFeature.REALTIME_CLIENT_SECRET)
        );
        assertEquals(
                InteropCapabilityLevel.UNSUPPORTED,
                service.implementedLevel(geminiCandidate(UpstreamSiteKind.VERTEX_AI), batchSemantics, InteropFeature.BATCH_CREATE)
        );
        assertEquals(
                InteropCapabilityLevel.UNSUPPORTED,
                service.implementedLevel(geminiCandidate(UpstreamSiteKind.VERTEX_AI), fileSemantics, InteropFeature.FILE_OBJECT)
        );
        assertEquals(
                InteropCapabilityLevel.UNSUPPORTED,
                service.implementedLevel(geminiCandidate(UpstreamSiteKind.VERTEX_AI), realtimeSemantics, InteropFeature.REALTIME_CLIENT_SECRET)
        );
    }

    private CatalogCandidateView geminiCandidate(UpstreamSiteKind siteKind) {
        return new CatalogCandidateView(
                101L,
                "gemini-candidate",
                ProviderType.GEMINI_DIRECT,
                1L,
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
}
