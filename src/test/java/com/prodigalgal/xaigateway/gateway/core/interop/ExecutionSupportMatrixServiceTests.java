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
    void shouldFreezeAnthropicFilesMatrix() {
        GatewayRequestSemantics fileSemantics = new GatewayRequestSemantics(
                TranslationResourceType.FILE,
                TranslationOperation.FILE_CREATE,
                List.of(InteropFeature.FILE_OBJECT),
                true
        );

        assertEquals(
                InteropCapabilityLevel.NATIVE,
                service.implementedLevel(anthropicCandidate(), fileSemantics, InteropFeature.FILE_OBJECT)
        );
    }

    @Test
    void shouldExposeOpenAiCompatibleFilesAndUploadsWhenSnapshotAllowsObjectLifecycle() {
        CatalogCandidateView candidate = openAiCandidate(
                ProviderType.OPENAI_COMPATIBLE,
                UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC
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
                List.of(InteropFeature.UPLOAD_CREATE, InteropFeature.FILE_OBJECT),
                true
        );

        assertEquals(
                InteropCapabilityLevel.NATIVE,
                service.implementedLevel(candidate, fileSemantics, InteropFeature.FILE_OBJECT)
        );
        assertEquals(
                InteropCapabilityLevel.NATIVE,
                service.implementedLevel(candidate, uploadSemantics, InteropFeature.UPLOAD_CREATE)
        );
    }


    @Test
    void shouldOnlyExposeWebSearchForOpenAiAndPerplexityAdapters() {
        GatewayRequestSemantics webSearchSemantics = new GatewayRequestSemantics(
                TranslationResourceType.WEB_SEARCH,
                TranslationOperation.WEB_SEARCH_CREATE,
                List.of(InteropFeature.WEB_SEARCH),
                true
        );

        assertEquals(
                InteropCapabilityLevel.NATIVE,
                service.implementedLevel(openAiCandidate(ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT), webSearchSemantics, InteropFeature.WEB_SEARCH)
        );
        assertEquals(
                InteropCapabilityLevel.NATIVE,
                service.implementedLevel(openAiCandidate(ProviderType.OPENAI_COMPATIBLE, UpstreamSiteKind.PERPLEXITY), webSearchSemantics, InteropFeature.WEB_SEARCH)
        );
        assertEquals(
                InteropCapabilityLevel.UNSUPPORTED,
                service.implementedLevel(openAiCandidate(ProviderType.OPENAI_COMPATIBLE, UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC), webSearchSemantics, InteropFeature.WEB_SEARCH)
        );
    }

    @Test
    void shouldExposeNewAudioImageResourceGapsForOpenAiStyleAndGeminiEditSites() {
        GatewayRequestSemantics audioTranslation = new GatewayRequestSemantics(
                TranslationResourceType.AUDIO,
                TranslationOperation.AUDIO_TRANSLATION,
                List.of(InteropFeature.AUDIO_TRANSLATION),
                true
        );
        GatewayRequestSemantics imageEdit = new GatewayRequestSemantics(
                TranslationResourceType.IMAGE,
                TranslationOperation.IMAGE_EDIT,
                List.of(InteropFeature.IMAGE_EDIT),
                true
        );

        assertEquals(
                InteropCapabilityLevel.NATIVE,
                service.implementedLevel(openAiCandidate(ProviderType.OPENAI_COMPATIBLE, UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC), audioTranslation, InteropFeature.AUDIO_TRANSLATION)
        );
        assertEquals(
                InteropCapabilityLevel.NATIVE,
                service.implementedLevel(openAiCandidate(ProviderType.OPENAI_COMPATIBLE, UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC), imageEdit, InteropFeature.IMAGE_EDIT)
        );
        assertEquals(
                InteropCapabilityLevel.UNSUPPORTED,
                service.implementedLevel(geminiCandidate(UpstreamSiteKind.GEMINI_DIRECT), audioTranslation, InteropFeature.AUDIO_TRANSLATION)
        );
        assertEquals(
                InteropCapabilityLevel.NATIVE,
                service.implementedLevel(geminiCandidate(UpstreamSiteKind.GEMINI_DIRECT), imageEdit, InteropFeature.IMAGE_EDIT)
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

    private CatalogCandidateView anthropicCandidate() {
        return new CatalogCandidateView(
                202L,
                "anthropic-candidate",
                ProviderType.ANTHROPIC_DIRECT,
                2L,
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

    private CatalogCandidateView openAiCandidate(ProviderType providerType, UpstreamSiteKind siteKind) {
        return new CatalogCandidateView(
                303L,
                "openai-candidate",
                providerType,
                3L,
                ProviderFamily.OPENAI,
                siteKind,
                AuthStrategy.BEARER,
                PathStrategy.OPENAI_V1,
                ErrorSchemaStrategy.OPENAI_ERROR,
                "https://example.com",
                "gpt-4o-mini",
                "gpt-4o-mini",
                List.of("openai"),
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                false,
                ReasoningTransport.OPENAI_CHAT,
                InteropCapabilityLevel.NATIVE
        );
    }
}
