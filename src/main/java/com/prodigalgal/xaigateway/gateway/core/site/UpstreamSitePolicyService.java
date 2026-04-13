package com.prodigalgal.xaigateway.gateway.core.site;

import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelAddressingStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class UpstreamSitePolicyService {

    public UpstreamSiteKind inferSiteKind(ProviderType providerType, String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim().toLowerCase(Locale.ROOT);
        return switch (providerType) {
            case OPENAI_DIRECT -> normalized.contains(".openai.azure.com")
                    ? UpstreamSiteKind.AZURE_OPENAI
                    : UpstreamSiteKind.OPENAI_DIRECT;
            case OPENAI_COMPATIBLE -> {
                if (normalized.contains("api.deepseek.com")) {
                    yield UpstreamSiteKind.DEEPSEEK;
                }
                if (normalized.contains("api.x.ai")) {
                    yield UpstreamSiteKind.GROK;
                }
                if (normalized.contains("api.mistral.ai")) {
                    yield UpstreamSiteKind.MISTRAL;
                }
                if (normalized.contains("api.cohere.ai")) {
                    yield UpstreamSiteKind.COHERE;
                }
                if (normalized.contains("api.together.xyz")) {
                    yield UpstreamSiteKind.TOGETHER;
                }
                if (normalized.contains("api.fireworks.ai")) {
                    yield UpstreamSiteKind.FIREWORKS;
                }
                if (normalized.contains("openrouter.ai")) {
                    yield UpstreamSiteKind.OPENROUTER;
                }
                yield UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC;
            }
            case ANTHROPIC_DIRECT -> UpstreamSiteKind.ANTHROPIC_DIRECT;
            case GEMINI_DIRECT -> normalized.contains("aiplatform.googleapis.com")
                    ? UpstreamSiteKind.VERTEX_AI
                    : UpstreamSiteKind.GEMINI_DIRECT;
            case OLLAMA_DIRECT -> UpstreamSiteKind.OLLAMA_DIRECT;
        };
    }

    public SitePolicy policy(UpstreamSiteKind siteKind) {
        return switch (siteKind) {
            case OPENAI_DIRECT -> new SitePolicy(
                    ProviderFamily.OPENAI,
                    AuthStrategy.BEARER,
                    PathStrategy.OPENAI_V1,
                    ModelAddressingStrategy.MODEL_NAME,
                    ErrorSchemaStrategy.OPENAI_ERROR,
                    List.of("openai", "responses"),
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    "sse",
                    "provider-native",
                    null
            );
            case AZURE_OPENAI -> new SitePolicy(
                    ProviderFamily.OPENAI,
                    AuthStrategy.AZURE_API_KEY,
                    PathStrategy.AZURE_OPENAI_DEPLOYMENT,
                    ModelAddressingStrategy.DEPLOYMENT_NAME,
                    ErrorSchemaStrategy.AZURE_OPENAI_ERROR,
                    List.of("openai", "responses"),
                    true,
                    true,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    "sse",
                    "provider-native",
                    null
            );
            case DEEPSEEK, GROK, TOGETHER, FIREWORKS, OPENROUTER, OPENAI_COMPATIBLE_GENERIC -> new SitePolicy(
                    ProviderFamily.OPENAI,
                    AuthStrategy.BEARER,
                    PathStrategy.OPENAI_V1,
                    ModelAddressingStrategy.MODEL_NAME,
                    ErrorSchemaStrategy.OPENAI_ERROR,
                    List.of("openai", "responses"),
                    true,
                    true,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    "sse",
                    "provider-specific-fallback",
                    null
            );
            case MISTRAL -> new SitePolicy(
                    ProviderFamily.OPENAI,
                    AuthStrategy.BEARER,
                    PathStrategy.OPENAI_V1,
                    ModelAddressingStrategy.MODEL_NAME,
                    ErrorSchemaStrategy.OPENAI_ERROR,
                    List.of("openai", "responses"),
                    true,
                    true,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    "sse",
                    "path-adapter-required",
                    null
            );
            case ANTHROPIC_DIRECT -> new SitePolicy(
                    ProviderFamily.ANTHROPIC,
                    AuthStrategy.API_KEY_HEADER,
                    PathStrategy.ANTHROPIC_V1_MESSAGES,
                    ModelAddressingStrategy.MODEL_NAME,
                    ErrorSchemaStrategy.ANTHROPIC_ERROR,
                    List.of("openai", "anthropic_native", "responses"),
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    "sse",
                    "translation-layer",
                    null
            );
            case GEMINI_DIRECT -> new SitePolicy(
                    ProviderFamily.GEMINI,
                    AuthStrategy.API_KEY_QUERY,
                    PathStrategy.GEMINI_V1BETA_MODELS,
                    ModelAddressingStrategy.MODEL_NAME,
                    ErrorSchemaStrategy.GEMINI_ERROR,
                    List.of("openai", "google_native", "responses"),
                    true,
                    true,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    "sse",
                    "translation-layer",
                    null
            );
            case OLLAMA_DIRECT -> new SitePolicy(
                    ProviderFamily.OLLAMA,
                    AuthStrategy.UNSUPPORTED,
                    PathStrategy.OLLAMA_API_CHAT,
                    ModelAddressingStrategy.OLLAMA_MODEL_TAG,
                    ErrorSchemaStrategy.OLLAMA_ERROR,
                    List.of("openai", "responses", "anthropic_native", "google_native"),
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    "ndjson",
                    "translation-layer",
                    null
            );
            case COHERE -> new SitePolicy(
                    ProviderFamily.OPENAI,
                    AuthStrategy.BEARER,
                    PathStrategy.OPENAI_V1,
                    ModelAddressingStrategy.MODEL_NAME,
                    ErrorSchemaStrategy.OPENAI_ERROR,
                    List.of("openai"),
                    false,
                    true,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    "sse",
                    "compatibility-api",
                    null
            );
            case VERTEX_AI -> new SitePolicy(
                    ProviderFamily.GEMINI,
                    AuthStrategy.BEARER,
                    PathStrategy.GEMINI_V1BETA_MODELS,
                    ModelAddressingStrategy.MODEL_NAME,
                    ErrorSchemaStrategy.GEMINI_ERROR,
                    List.of("google_native"),
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    "sse",
                    "vertex-google-native",
                    null
            );
        };
    }

    public InteropCapabilityLevel capabilityLevel(UpstreamSiteKind siteKind, boolean supportsEmbeddings, boolean supportsThinking, InteropFeature feature) {
        SitePolicy policy = policy(siteKind);
        if (policy.blockedReason() != null) {
            return InteropCapabilityLevel.UNSUPPORTED;
        }

        return switch (feature) {
            case CHAT_TEXT -> InteropCapabilityLevel.NATIVE;
            case TOOLS -> siteKind == UpstreamSiteKind.OLLAMA_DIRECT
                    ? InteropCapabilityLevel.UNSUPPORTED
                    : InteropCapabilityLevel.NATIVE;
            case IMAGE_INPUT -> switch (siteKind) {
                case ANTHROPIC_DIRECT, GEMINI_DIRECT, VERTEX_AI, OPENAI_DIRECT, AZURE_OPENAI -> InteropCapabilityLevel.NATIVE;
                case OLLAMA_DIRECT -> InteropCapabilityLevel.UNSUPPORTED;
                default -> InteropCapabilityLevel.EMULATED;
            };
            case FILE_INPUT -> switch (siteKind) {
                case GEMINI_DIRECT, VERTEX_AI, OPENAI_DIRECT, AZURE_OPENAI -> InteropCapabilityLevel.NATIVE;
                case ANTHROPIC_DIRECT -> InteropCapabilityLevel.EMULATED;
                default -> InteropCapabilityLevel.UNSUPPORTED;
            };
            case FILE_OBJECT -> policy.supportsFiles()
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case REASONING -> supportsThinking ? InteropCapabilityLevel.NATIVE : InteropCapabilityLevel.UNSUPPORTED;
            case RESPONSE_OBJECT -> policy.supportedProtocols().contains("responses")
                    ? InteropCapabilityLevel.EMULATED
                    : InteropCapabilityLevel.UNSUPPORTED;
            case EMBEDDINGS -> supportsEmbeddings && policy.supportsEmbeddings()
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case AUDIO_TRANSCRIPTION, AUDIO_TRANSLATION, AUDIO_SPEECH -> policy.supportsAudio()
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case IMAGE_GENERATION, IMAGE_EDIT, IMAGE_VARIATION -> policy.supportsImages()
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case MODERATION -> policy.supportsModeration()
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case UPLOAD_CREATE -> policy.supportsUploads()
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case BATCH_CREATE -> policy.supportsBatches()
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case TUNING_CREATE -> policy.supportsTuning()
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
            case REALTIME_CLIENT_SECRET -> policy.supportsRealtime()
                    ? InteropCapabilityLevel.NATIVE
                    : InteropCapabilityLevel.UNSUPPORTED;
        };
    }

    public record SitePolicy(
            ProviderFamily providerFamily,
            AuthStrategy authStrategy,
            PathStrategy pathStrategy,
            ModelAddressingStrategy modelAddressingStrategy,
            ErrorSchemaStrategy errorSchemaStrategy,
            List<String> supportedProtocols,
            boolean supportsResponses,
            boolean supportsEmbeddings,
            boolean supportsAudio,
            boolean supportsImages,
            boolean supportsModeration,
            boolean supportsFiles,
            boolean supportsUploads,
            boolean supportsBatches,
            boolean supportsTuning,
            boolean supportsRealtime,
            String streamTransport,
            String fallbackStrategy,
            String blockedReason
    ) {
    }
}
