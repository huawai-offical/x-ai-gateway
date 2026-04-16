package com.prodigalgal.xaigateway.gateway.core.execution;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingsGatewayResourceExecutorTests {

    private final EmbeddingsGatewayResourceExecutor executor = new EmbeddingsGatewayResourceExecutor(
            new ObjectMapper(),
            Mockito.mock(com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiChatModelFactory.class)
    );

    @Test
    void shouldSupportGeminiDirectOnlyWhenPathAndAuthMatchNativeRequirements() {
        assertTrue(executor.supports(request(), candidate(
                ProviderType.GEMINI_DIRECT,
                UpstreamSiteKind.GEMINI_DIRECT,
                AuthStrategy.API_KEY_QUERY,
                PathStrategy.GEMINI_V1BETA_MODELS
        )));
        assertFalse(executor.supports(request(), candidate(
                ProviderType.GEMINI_DIRECT,
                UpstreamSiteKind.GEMINI_DIRECT,
                AuthStrategy.BEARER,
                PathStrategy.GEMINI_V1BETA_MODELS
        )));
        assertFalse(executor.supports(request(), candidate(
                ProviderType.GEMINI_DIRECT,
                UpstreamSiteKind.GEMINI_DIRECT,
                AuthStrategy.API_KEY_QUERY,
                PathStrategy.OPENAI_V1
        )));
    }

    @Test
    void shouldSupportVertexAiEmbeddingsCandidatesWhenPathAndAuthMatchGoogleGenAiRequirements() {
        assertTrue(executor.supports(request(), candidate(
                ProviderType.GEMINI_DIRECT,
                UpstreamSiteKind.VERTEX_AI,
                AuthStrategy.BEARER,
                PathStrategy.GEMINI_V1BETA_MODELS
        )));
        assertFalse(executor.supports(request(), candidate(
                ProviderType.GEMINI_DIRECT,
                UpstreamSiteKind.VERTEX_AI,
                AuthStrategy.API_KEY_QUERY,
                PathStrategy.GEMINI_V1BETA_MODELS
        )));
    }

    @Test
    void shouldKeepOpenAiFamilyEmbeddingsSupported() {
        assertTrue(executor.supports(request(), candidate(
                ProviderType.OPENAI_DIRECT,
                UpstreamSiteKind.OPENAI_DIRECT,
                AuthStrategy.BEARER,
                PathStrategy.OPENAI_V1
        )));
        assertTrue(executor.supports(request(), candidate(
                ProviderType.OPENAI_COMPATIBLE,
                UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC,
                AuthStrategy.API_KEY_HEADER,
                PathStrategy.OPENAI_V1
        )));
    }

    private CanonicalResourceRequest request() {
        return new CanonicalResourceRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.OPENAI,
                "POST",
                "/v1/embeddings",
                "/v1/embeddings",
                java.util.Map.of(),
                "text-embedding-004",
                com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType.EMBEDDING,
                com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation.EMBEDDING_CREATE,
                new ObjectMapper().createObjectNode(),
                java.util.Map.of(),
                java.util.List.of(),
                false,
                false
        );
    }

    private CatalogCandidateView candidate(
            ProviderType providerType,
            UpstreamSiteKind siteKind,
            AuthStrategy authStrategy,
            PathStrategy pathStrategy) {
        return new CatalogCandidateView(
                101L,
                "candidate",
                providerType,
                1L,
                providerType == ProviderType.GEMINI_DIRECT ? ProviderFamily.GEMINI : ProviderFamily.OPENAI,
                siteKind,
                authStrategy,
                pathStrategy,
                ErrorSchemaStrategy.GEMINI_ERROR,
                "https://example.com",
                "text-embedding-004",
                "text-embedding-004",
                List.of("openai"),
                true,
                true,
                false,
                true,
                false,
                true,
                true,
                false,
                ReasoningTransport.GEMINI_THOUGHTS,
                com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel.NATIVE
        );
    }
}
