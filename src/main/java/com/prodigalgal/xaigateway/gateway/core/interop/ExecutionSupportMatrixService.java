package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
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
                case GEMINI_DIRECT -> siteKind == UpstreamSiteKind.VERTEX_AI
                        ? InteropCapabilityLevel.UNSUPPORTED
                        : InteropCapabilityLevel.NATIVE;
                default -> InteropCapabilityLevel.UNSUPPORTED;
            };
            case AUDIO_TRANSCRIPTION, AUDIO_TRANSLATION, AUDIO_SPEECH ->
                    supportsOpenAiStyleSite(siteKind) ? InteropCapabilityLevel.NATIVE : InteropCapabilityLevel.UNSUPPORTED;
            case IMAGE_GENERATION, IMAGE_EDIT, IMAGE_VARIATION ->
                    supportsOpenAiStyleSite(siteKind) ? InteropCapabilityLevel.NATIVE : InteropCapabilityLevel.UNSUPPORTED;
            case MODERATION -> supportsOpenAiStyleSite(siteKind) ? InteropCapabilityLevel.NATIVE : InteropCapabilityLevel.UNSUPPORTED;
            case FILE_OBJECT -> siteKind == UpstreamSiteKind.OPENAI_DIRECT
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case UPLOAD_CREATE, BATCH_CREATE, TUNING_CREATE, REALTIME_CLIENT_SECRET ->
                    siteKind == UpstreamSiteKind.OPENAI_DIRECT ? InteropCapabilityLevel.NATIVE : InteropCapabilityLevel.UNSUPPORTED;
        };
    }

    private boolean supportsOpenAiStyleSite(UpstreamSiteKind siteKind) {
        return switch (siteKind) {
            case OPENAI_DIRECT, OPENAI_COMPATIBLE_GENERIC, DEEPSEEK, GROK, MISTRAL, COHERE, TOGETHER, FIREWORKS, OPENROUTER -> true;
            default -> false;
        };
    }
}
