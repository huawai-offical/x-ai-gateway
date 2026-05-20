package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ExecutionSupportMatrixService {

    public InteropCapabilityLevel implementedLevel(CatalogCandidateView candidate, GatewayRequestSemantics semantics, InteropFeature feature) {
        if (candidate == null || semantics == null || feature == null) {
            return InteropCapabilityLevel.UNSUPPORTED;
        }
        ProviderType providerType = candidate.providerType();
        UpstreamSiteKind siteKind = candidate.siteKind();

        return switch (feature) {
            case CHAT_TEXT -> switch (providerType) {
                case OPENAI_DIRECT, OPENAI_COMPATIBLE, ANTHROPIC_DIRECT, GEMINI_DIRECT, OLLAMA_DIRECT -> InteropCapabilityLevel.NATIVE;
            };
            case TOOLS -> switch (providerType) {
                case OPENAI_DIRECT, OPENAI_COMPATIBLE, ANTHROPIC_DIRECT, GEMINI_DIRECT, OLLAMA_DIRECT -> InteropCapabilityLevel.NATIVE;
            };
            case IMAGE_INPUT -> providerType == ProviderType.OLLAMA_DIRECT
                    ? InteropCapabilityLevel.NATIVE
                    : switch (providerType) {
                        case OPENAI_DIRECT, OPENAI_COMPATIBLE, ANTHROPIC_DIRECT, GEMINI_DIRECT -> InteropCapabilityLevel.NATIVE;
                        default -> InteropCapabilityLevel.UNSUPPORTED;
                    };
            case FILE_INPUT -> switch (providerType) {
                case OPENAI_DIRECT, OPENAI_COMPATIBLE, ANTHROPIC_DIRECT, GEMINI_DIRECT -> InteropCapabilityLevel.NATIVE;
                default -> InteropCapabilityLevel.UNSUPPORTED;
            };
            case REASONING -> switch (providerType) {
                case OPENAI_DIRECT, OPENAI_COMPATIBLE, ANTHROPIC_DIRECT, GEMINI_DIRECT, OLLAMA_DIRECT -> InteropCapabilityLevel.NATIVE;
            };
            case RESPONSE_OBJECT -> switch (providerType) {
                case OPENAI_DIRECT, OPENAI_COMPATIBLE, ANTHROPIC_DIRECT, GEMINI_DIRECT, OLLAMA_DIRECT -> InteropCapabilityLevel.EMULATED;
            };
            case EMBEDDINGS -> switch (providerType) {
                case OPENAI_DIRECT, OPENAI_COMPATIBLE -> InteropCapabilityLevel.NATIVE;
                case GEMINI_DIRECT -> supportsGoogleGenAiSite(siteKind)
                        ? InteropCapabilityLevel.NATIVE
                        : InteropCapabilityLevel.UNSUPPORTED;
                default -> InteropCapabilityLevel.UNSUPPORTED;
            };
            case AUDIO_TRANSCRIPTION, AUDIO_TRANSLATION, AUDIO_SPEECH ->
                    supportsGoogleGenAiAudio(siteKind) || supportsOpenAiStyleSite(siteKind)
                            ? InteropCapabilityLevel.NATIVE
                            : InteropCapabilityLevel.UNSUPPORTED;
            case IMAGE_GENERATION ->
                    supportsGoogleGenAiImages(siteKind) || supportsOpenAiStyleSite(siteKind)
                            ? InteropCapabilityLevel.NATIVE
                            : InteropCapabilityLevel.UNSUPPORTED;
            case IMAGE_EDIT, IMAGE_VARIATION ->
                    supportsOpenAiStyleSite(siteKind) ? InteropCapabilityLevel.NATIVE : InteropCapabilityLevel.UNSUPPORTED;
            case MODERATION ->
                    supportsGoogleGenAiModeration(siteKind) || supportsOpenAiStyleSite(siteKind)
                            ? InteropCapabilityLevel.NATIVE
                            : InteropCapabilityLevel.UNSUPPORTED;
            case FILE_OBJECT -> (siteKind == UpstreamSiteKind.OPENAI_DIRECT
                    || siteKind == UpstreamSiteKind.ANTHROPIC_DIRECT
                    || supportsGoogleGenAiSite(siteKind))
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case UPLOAD_CREATE, REALTIME_CLIENT_SECRET ->
                    siteKind == UpstreamSiteKind.OPENAI_DIRECT ? InteropCapabilityLevel.NATIVE : InteropCapabilityLevel.UNSUPPORTED;
            case RERANK ->
                    (siteKind == UpstreamSiteKind.COHERE || siteKind == UpstreamSiteKind.JINA)
                            ? InteropCapabilityLevel.NATIVE
                            : InteropCapabilityLevel.UNSUPPORTED;
            case VIDEO_GENERATION, MUSIC_GENERATION, ASYNC_TASK ->
                    (siteKind == UpstreamSiteKind.OPENAI_DIRECT || siteKind == UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC)
                            ? InteropCapabilityLevel.NATIVE
                            : InteropCapabilityLevel.UNSUPPORTED;
            case WEB_SEARCH ->
                    (siteKind == UpstreamSiteKind.OPENAI_DIRECT || siteKind == UpstreamSiteKind.PERPLEXITY)
                            ? InteropCapabilityLevel.NATIVE
                            : InteropCapabilityLevel.UNSUPPORTED;
        };
    }

    public InteropCapabilityLevel degradationLevel(
            InteropCapabilityLevel effectiveLevel,
            List<String> blockerReasons) {
        return SupportStatus.normalizeDegradationLevel(effectiveLevel, blockerReasons);
    }

    public SupportStatus supportStatus(
            ExecutionBackend executionBackend,
            InteropCapabilityLevel effectiveLevel,
            List<String> blockerReasons) {
        return SupportStatus.resolve(executionBackend, effectiveLevel, blockerReasons);
    }

    private boolean supportsOpenAiStyleSite(UpstreamSiteKind siteKind) {
        return switch (siteKind) {
            case OPENAI_DIRECT, OPENAI_COMPATIBLE_GENERIC, DEEPSEEK, QWEN, MOONSHOT, SILICONFLOW, VOLCENGINE,
                    MINIMAX, GROK, MISTRAL, COHERE, JINA, TOGETHER, FIREWORKS, OPENROUTER -> true;
            default -> false;
        };
    }

    private boolean supportsGoogleGenAiAudio(UpstreamSiteKind siteKind) {
        return supportsGoogleGenAiSite(siteKind);
    }

    private boolean supportsGoogleGenAiImages(UpstreamSiteKind siteKind) {
        return supportsGoogleGenAiSite(siteKind);
    }

    private boolean supportsGoogleGenAiModeration(UpstreamSiteKind siteKind) {
        return supportsGoogleGenAiSite(siteKind);
    }

    private boolean supportsGoogleGenAiSite(UpstreamSiteKind siteKind) {
        return siteKind == UpstreamSiteKind.GEMINI_DIRECT || siteKind == UpstreamSiteKind.VERTEX_AI;
    }
}
