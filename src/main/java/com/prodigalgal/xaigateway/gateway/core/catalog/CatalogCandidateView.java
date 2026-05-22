package com.prodigalgal.xaigateway.gateway.core.catalog;

import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import java.util.List;

public record CatalogCandidateView(
        Long credentialId,
        String credentialName,
        ProviderType providerType,
        Long siteProfileId,
        ProviderFamily providerFamily,
        String vendorCode,
        UpstreamSiteKind siteKind,
        AuthStrategy authStrategy,
        PathStrategy pathStrategy,
        ErrorSchemaStrategy errorSchemaStrategy,
        String baseUrl,
        String modelName,
        String modelKey,
        List<String> supportedProtocols,
        boolean supportsChat,
        boolean supportsTools,
        boolean supportsImageInput,
        boolean supportsEmbeddings,
        boolean supportsCache,
        boolean supportsThinking,
        boolean supportsVisibleReasoning,
        boolean supportsReasoningReuse,
        ReasoningTransport reasoningTransport,
        InteropCapabilityLevel capabilityLevel
) {
    public CatalogCandidateView {
        supportedProtocols = supportedProtocols == null ? List.of() : List.copyOf(supportedProtocols);
    }

    public CatalogCandidateView(
            Long credentialId,
            String credentialName,
            ProviderType providerType,
            Long siteProfileId,
            ProviderFamily providerFamily,
            String vendorCode,
            UpstreamSiteKind siteKind,
            AuthStrategy authStrategy,
            PathStrategy pathStrategy,
            ErrorSchemaStrategy errorSchemaStrategy,
            String baseUrl,
            String modelName,
            String modelKey,
            List<String> supportedProtocols,
            boolean supportsChat,
            boolean supportsEmbeddings,
            boolean supportsCache,
            boolean supportsThinking,
            boolean supportsVisibleReasoning,
            boolean supportsReasoningReuse,
            ReasoningTransport reasoningTransport,
            InteropCapabilityLevel capabilityLevel
    ) {
        this(
                credentialId,
                credentialName,
                providerType,
                siteProfileId,
                providerFamily,
                vendorCode,
                siteKind,
                authStrategy,
                pathStrategy,
                errorSchemaStrategy,
                baseUrl,
                modelName,
                modelKey,
                supportedProtocols,
                supportsChat,
                supportsChat,
                supportsChat,
                supportsEmbeddings,
                supportsCache,
                supportsThinking,
                supportsVisibleReasoning,
                supportsReasoningReuse,
                reasoningTransport,
                capabilityLevel
        );
    }

    public CatalogCandidateView(
            Long credentialId,
            String credentialName,
            ProviderType providerType,
            Long siteProfileId,
            ProviderFamily providerFamily,
            UpstreamSiteKind siteKind,
            AuthStrategy authStrategy,
            PathStrategy pathStrategy,
            ErrorSchemaStrategy errorSchemaStrategy,
            String baseUrl,
            String modelName,
            String modelKey,
            List<String> supportedProtocols,
            boolean supportsChat,
            boolean supportsEmbeddings,
            boolean supportsCache,
            boolean supportsThinking,
            boolean supportsVisibleReasoning,
            boolean supportsReasoningReuse,
            ReasoningTransport reasoningTransport,
            InteropCapabilityLevel capabilityLevel
    ) {
        this(
                credentialId,
                credentialName,
                providerType,
                siteProfileId,
                providerFamily,
                null,
                siteKind,
                authStrategy,
                pathStrategy,
                errorSchemaStrategy,
                baseUrl,
                modelName,
                modelKey,
                supportedProtocols,
                supportsChat,
                supportsEmbeddings,
                supportsCache,
                supportsThinking,
                supportsVisibleReasoning,
                supportsReasoningReuse,
                reasoningTransport,
                capabilityLevel
        );
    }

    public CatalogCandidateView(
            Long credentialId,
            String credentialName,
            ProviderType providerType,
            Long siteProfileId,
            ProviderFamily providerFamily,
            UpstreamSiteKind siteKind,
            AuthStrategy authStrategy,
            PathStrategy pathStrategy,
            ErrorSchemaStrategy errorSchemaStrategy,
            String baseUrl,
            String modelName,
            String modelKey,
            List<String> supportedProtocols,
            boolean supportsChat,
            boolean supportsTools,
            boolean supportsImageInput,
            boolean supportsEmbeddings,
            boolean supportsCache,
            boolean supportsThinking,
            boolean supportsVisibleReasoning,
            boolean supportsReasoningReuse,
            ReasoningTransport reasoningTransport,
            InteropCapabilityLevel capabilityLevel
    ) {
        this(
                credentialId,
                credentialName,
                providerType,
                siteProfileId,
                providerFamily,
                null,
                siteKind,
                authStrategy,
                pathStrategy,
                errorSchemaStrategy,
                baseUrl,
                modelName,
                modelKey,
                supportedProtocols,
                supportsChat,
                supportsTools,
                supportsImageInput,
                supportsEmbeddings,
                supportsCache,
                supportsThinking,
                supportsVisibleReasoning,
                supportsReasoningReuse,
                reasoningTransport,
                capabilityLevel
        );
    }

    public CatalogCandidateView(
            Long credentialId,
            String credentialName,
            ProviderType providerType,
            String baseUrl,
            String modelName,
            String modelKey,
            List<String> supportedProtocols,
            boolean supportsChat,
            boolean supportsEmbeddings,
            boolean supportsCache,
            boolean supportsThinking,
            boolean supportsVisibleReasoning,
            boolean supportsReasoningReuse,
            ReasoningTransport reasoningTransport
    ) {
        this(
                credentialId,
                credentialName,
                providerType,
                baseUrl,
                modelName,
                modelKey,
                supportedProtocols,
                supportsChat,
                supportsChat,
                supportsChat,
                supportsEmbeddings,
                supportsCache,
                supportsThinking,
                supportsVisibleReasoning,
                supportsReasoningReuse,
                reasoningTransport
        );
    }

    public CatalogCandidateView(
            Long credentialId,
            String credentialName,
            ProviderType providerType,
            String baseUrl,
            String modelName,
            String modelKey,
            List<String> supportedProtocols,
            boolean supportsChat,
            boolean supportsTools,
            boolean supportsImageInput,
            boolean supportsEmbeddings,
            boolean supportsCache,
            boolean supportsThinking,
            boolean supportsVisibleReasoning,
            boolean supportsReasoningReuse,
            ReasoningTransport reasoningTransport
    ) {
        this(
                credentialId,
                credentialName,
                providerType,
                null,
                null,
                inferVendorCode(providerType, baseUrl),
                inferSiteKind(providerType, baseUrl),
                null,
                null,
                null,
                baseUrl,
                modelName,
                modelKey,
                supportedProtocols,
                supportsChat,
                supportsTools,
                supportsImageInput,
                supportsEmbeddings,
                supportsCache,
                supportsThinking,
                supportsVisibleReasoning,
                supportsReasoningReuse,
                reasoningTransport,
                InteropCapabilityLevel.NATIVE
        );
    }

    private static String inferVendorCode(ProviderType providerType, String baseUrl) {
        if (providerType != ProviderType.OPENAI_COMPATIBLE || baseUrl == null) {
            return null;
        }
        String normalizedBaseUrl = baseUrl.toLowerCase(java.util.Locale.ROOT);
        if (normalizedBaseUrl.contains("xiaomimimo.com")) {
            return "xiaomi_mimo";
        }
        if (normalizedBaseUrl.contains("deepseek.com")) {
            return "deepseek";
        }
        return null;
    }

    private static UpstreamSiteKind inferSiteKind(ProviderType providerType, String baseUrl) {
        if (providerType == null) {
            return null;
        }
        return switch (providerType) {
            case OPENAI_DIRECT -> UpstreamSiteKind.OPENAI_DIRECT;
            case OPENAI_COMPATIBLE -> inferOpenAiCompatibleSiteKind(baseUrl);
            case ANTHROPIC_DIRECT -> UpstreamSiteKind.ANTHROPIC_DIRECT;
            case GEMINI_DIRECT -> UpstreamSiteKind.GEMINI_DIRECT;
            case OLLAMA_DIRECT -> UpstreamSiteKind.OLLAMA_DIRECT;
        };
    }

    private static UpstreamSiteKind inferOpenAiCompatibleSiteKind(String baseUrl) {
        if (baseUrl == null) {
            return UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC;
        }
        String normalizedBaseUrl = baseUrl.toLowerCase(java.util.Locale.ROOT);
        if (normalizedBaseUrl.contains("deepseek.com")) {
            return UpstreamSiteKind.DEEPSEEK;
        }
        return UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC;
    }
}
