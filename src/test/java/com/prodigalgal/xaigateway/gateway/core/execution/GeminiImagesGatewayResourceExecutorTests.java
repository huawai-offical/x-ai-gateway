package com.prodigalgal.xaigateway.gateway.core.execution;

import com.google.genai.types.GenerateImagesConfig;
import com.google.genai.types.GenerateImagesResponse;
import com.google.genai.types.GeneratedImage;
import com.google.genai.types.Image;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiChatModelFactory;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiImagesGatewayResourceExecutorTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSupportOnlyGeminiDirectImageGenerationCandidates() {
        TestGeminiImagesExecutor executor = new TestGeminiImagesExecutor(singleImageResponse(new byte[] {1, 2, 3}));

        assertTrue(executor.supports(request("/v1/images/generations", TranslationOperation.IMAGE_GENERATION), candidate(UpstreamSiteKind.GEMINI_DIRECT)));
        assertFalse(executor.supports(request("/v1/images/edits", TranslationOperation.IMAGE_EDIT), candidate(UpstreamSiteKind.GEMINI_DIRECT)));
        assertTrue(executor.supports(request("/v1/images/generations", TranslationOperation.IMAGE_GENERATION), candidate(UpstreamSiteKind.VERTEX_AI)));
    }

    @Test
    void shouldReturnOpenAiCompatibleB64JsonResponse() {
        byte[] imageBytes = new byte[] {1, 2, 3, 4};
        TestGeminiImagesExecutor executor = new TestGeminiImagesExecutor(singleImageResponse(imageBytes));
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("prompt", "draw a fox");
        payload.put("n", 2);

        ResponseEntity<JsonNode> response = executor.executeJson(
                context(),
                payload,
                "gemini-2.0-flash-preview-image-generation"
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals("gemini-2.0-flash-preview-image-generation", response.getBody().path("model").asText());
        assertEquals(Base64.getEncoder().encodeToString(imageBytes), response.getBody().path("data").get(0).path("b64_json").asText());
        assertEquals(2, executor.lastConfig.numberOfImages().orElseThrow());
        assertEquals("image/png", executor.lastConfig.outputMimeType().orElseThrow());
    }

    @Test
    void shouldRejectUnsupportedImageResponseFormat() {
        TestGeminiImagesExecutor executor = new TestGeminiImagesExecutor(singleImageResponse(new byte[] {1}));
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("prompt", "draw a fox");
        payload.put("response_format", "url");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> executor.executeJson(context(), payload, "gemini-2.0-flash-preview-image-generation")
        );

        assertEquals("Gemini image generation 当前仅返回 b64_json。", error.getMessage());
    }

    @Test
    void shouldRejectNonGenerationOperations() {
        TestGeminiImagesExecutor executor = new TestGeminiImagesExecutor(singleImageResponse(new byte[] {1}));
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("prompt", "draw a fox");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> executor.executeJson(context(TranslationOperation.IMAGE_EDIT, "/v1/images/edits"), payload, "gemini-2.0-flash-preview-image-generation")
        );

        assertEquals("Gemini images executor 当前仅支持 /v1/images/generations。", error.getMessage());
    }

    private GenerateImagesResponse singleImageResponse(byte[] imageBytes) {
        return GenerateImagesResponse.builder()
                .generatedImages(
                        GeneratedImage.builder()
                                .image(Image.builder().imageBytes(imageBytes).mimeType("image/png").build())
                                .build()
                )
                .build();
    }

    private GatewayResourceExecutionContext context() {
        return context(TranslationOperation.IMAGE_GENERATION, "/v1/images/generations");
    }

    private GatewayResourceExecutionContext context(TranslationOperation operation, String path) {
        CatalogCandidateView candidate = candidate(UpstreamSiteKind.GEMINI_DIRECT);
        RouteCandidateView routeCandidateView = new RouteCandidateView(candidate, 11L, 10, 100);
        RouteSelectionResult selectionResult = new RouteSelectionResult(
                1L,
                "sk-gw-test",
                "gemini-2.0-flash-preview-image-generation",
                "gemini-2.0-flash-preview-image-generation",
                "gemini-2.0-flash-preview-image-generation",
                "openai",
                "prefix",
                "fingerprint",
                "gemini-2.0-flash-preview-image-generation",
                RouteSelectionSource.WEIGHTED_HASH,
                routeCandidateView,
                List.of(routeCandidateView)
        );
        UpstreamCredentialEntity credential = new UpstreamCredentialEntity();
        credential.setBaseUrl("https://generativelanguage.googleapis.com");
        credential.setProviderType(ProviderType.GEMINI_DIRECT);
        return new GatewayResourceExecutionContext(
                1L,
                selectionResult,
                credential,
                "api-key",
                request(path, operation)
        );
    }

    private CanonicalResourceRequest request(String path, TranslationOperation operation) {
        return new CanonicalResourceRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.OPENAI,
                "POST",
                path,
                path,
                java.util.Map.of(),
                "gemini-2.0-flash-preview-image-generation",
                TranslationResourceType.IMAGE,
                operation,
                objectMapper.createObjectNode(),
                java.util.Map.of(),
                java.util.List.of(),
                false,
                false
        );
    }

    private CatalogCandidateView candidate(UpstreamSiteKind siteKind) {
        return new CatalogCandidateView(
                101L,
                "gemini-image",
                ProviderType.GEMINI_DIRECT,
                1L,
                ProviderFamily.GEMINI,
                siteKind,
                siteKind == UpstreamSiteKind.GEMINI_DIRECT ? AuthStrategy.API_KEY_QUERY : AuthStrategy.BEARER,
                PathStrategy.GEMINI_V1BETA_MODELS,
                ErrorSchemaStrategy.GEMINI_ERROR,
                "https://example.com",
                "gemini-2.0-flash-preview-image-generation",
                "gemini-2.0-flash-preview-image-generation",
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

    private final class TestGeminiImagesExecutor extends GeminiImagesGatewayResourceExecutor {

        private final GenerateImagesResponse response;
        private GenerateImagesConfig lastConfig;
        private String lastPrompt;

        private TestGeminiImagesExecutor(GenerateImagesResponse response) {
            super(Mockito.mock(GeminiChatModelFactory.class), objectMapper);
            this.response = response;
        }

        @Override
        GenerateImagesResponse generateImages(
                GatewayResourceExecutionContext context,
                String prompt,
                GenerateImagesConfig config) {
            this.lastPrompt = prompt;
            this.lastConfig = config;
            return response;
        }
    }
}
