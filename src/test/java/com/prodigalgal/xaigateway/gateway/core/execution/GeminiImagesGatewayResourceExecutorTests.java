package com.prodigalgal.xaigateway.gateway.core.execution;

import com.google.genai.types.EditImageConfig;
import com.google.genai.types.EditImageResponse;
import com.google.genai.types.ReferenceImage;
import com.google.genai.types.GenerateImagesConfig;
import com.google.genai.types.GenerateImagesResponse;
import com.google.genai.types.GeneratedImage;
import com.google.genai.types.Image;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiImagesGatewayResourceExecutorTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSupportOnlyGeminiDirectImageGenerationCandidates() {
        TestGeminiImagesExecutor executor = new TestGeminiImagesExecutor(singleImageResponse(new byte[] {1, 2, 3}));

        assertTrue(executor.supports(request("/v1/images/generations", TranslationOperation.IMAGE_GENERATION), candidate(UpstreamSiteKind.GEMINI_DIRECT)));
        assertTrue(executor.supports(request("/v1/images/edits", TranslationOperation.IMAGE_EDIT), candidate(UpstreamSiteKind.GEMINI_DIRECT)));
        assertTrue(executor.supports(request("/v1/images/variations", TranslationOperation.IMAGE_VARIATION), candidate(UpstreamSiteKind.GEMINI_DIRECT)));
        assertFalse(executor.supports(request("/v1/moderations", TranslationOperation.MODERATION_CREATE), candidate(UpstreamSiteKind.GEMINI_DIRECT)));
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

        assertEquals("Gemini images 当前仅返回 b64_json。", error.getMessage());
    }

    @Test
    void shouldRejectNonGenerationOperations() {
        TestGeminiImagesExecutor executor = new TestGeminiImagesExecutor(singleImageResponse(new byte[] {1}));
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("prompt", "draw a fox");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> executor.executeJson(context(TranslationOperation.MODERATION_CREATE, "/v1/moderations"), payload, "gemini-2.0-flash-preview-image-generation")
        );

        assertEquals("Gemini images executor 当前仅支持 /v1/images/generations。", error.getMessage());
    }

    @Test
    void shouldReturnOpenAiCompatibleB64JsonForEditAndReferenceInputImage() {
        byte[] imageBytes = new byte[] {9, 8, 7};
        byte[] editedBytes = new byte[] {1, 4, 9};
        TestGeminiImagesExecutor executor = new TestGeminiImagesExecutor(singleEditImageResponse(editedBytes));

        ResponseEntity<JsonNode> response = executor.executeEdit(
                context(TranslationOperation.IMAGE_EDIT, "/v1/images/edits"),
                "把背景换成蓝色",
                java.util.Map.of("n", "2", "output_format", "webp"),
                new GeminiGatewayResourceSupport.ResolvedBinaryFile("input.png", "image/png", imageBytes),
                null
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Base64.getEncoder().encodeToString(editedBytes), response.getBody().path("data").get(0).path("b64_json").asText());
        assertEquals(2, executor.lastEditConfig.numberOfImages().orElseThrow());
        assertEquals("image/webp", executor.lastEditConfig.outputMimeType().orElseThrow());
        assertEquals(1, executor.lastReferenceImages.size());
        assertArrayEquals(imageBytes, executor.lastReferenceImages.get(0).toReferenceImageAPI().referenceImage().orElseThrow().imageBytes().orElseThrow());
    }

    @Test
    void shouldUseProvidedMaskForGeminiImageEdit() {
        byte[] imageBytes = new byte[] {1, 2};
        byte[] maskBytes = new byte[] {3, 4};
        TestGeminiImagesExecutor executor = new TestGeminiImagesExecutor(singleEditImageResponse(new byte[] {5}));

        executor.executeEdit(
                context(TranslationOperation.IMAGE_EDIT, "/v1/images/edits"),
                "只替换遮罩区域",
                java.util.Map.of(),
                new GeminiGatewayResourceSupport.ResolvedBinaryFile("input.png", "image/png", imageBytes),
                new GeminiGatewayResourceSupport.ResolvedBinaryFile("mask.png", "image/png", maskBytes)
        );

        assertEquals(2, executor.lastReferenceImages.size());
        assertArrayEquals(maskBytes, executor.lastReferenceImages.get(1).toReferenceImageAPI().referenceImage().orElseThrow().imageBytes().orElseThrow());
        assertTrue(executor.lastReferenceImages.get(1).toReferenceImageAPI().maskImageConfig().isPresent());
    }

    @Test
    void shouldReturnOpenAiCompatibleB64JsonForVariationAndUseDefaultPrompt() {
        byte[] imageBytes = new byte[] {9, 8, 7};
        byte[] variationBytes = new byte[] {2, 4, 6};
        TestGeminiImagesExecutor executor = new TestGeminiImagesExecutor(singleEditImageResponse(variationBytes));

        ResponseEntity<JsonNode> response = executor.executeVariation(
                context(TranslationOperation.IMAGE_VARIATION, "/v1/images/variations"),
                "Create a visually distinct variation of the provided image. Preserve the main subject and composition, vary visual details naturally, and return only the generated image.",
                java.util.Map.of("n", "1"),
                new GeminiGatewayResourceSupport.ResolvedBinaryFile("input.png", "image/png", imageBytes)
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Base64.getEncoder().encodeToString(variationBytes), response.getBody().path("data").get(0).path("b64_json").asText());
        assertEquals(1, executor.lastEditConfig.numberOfImages().orElseThrow());
        assertEquals("image/png", executor.lastEditConfig.outputMimeType().orElseThrow());
        assertEquals(1, executor.lastReferenceImages.size());
        assertArrayEquals(imageBytes, executor.lastReferenceImages.get(0).toReferenceImageAPI().referenceImage().orElseThrow().imageBytes().orElseThrow());
        assertTrue(executor.lastPrompt.contains("visually distinct variation"));
    }

    @Test
    void shouldRejectUnsupportedEditOutputFormat() {
        TestGeminiImagesExecutor executor = new TestGeminiImagesExecutor(singleEditImageResponse(new byte[] {1}));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> executor.executeEdit(
                        context(TranslationOperation.IMAGE_EDIT, "/v1/images/edits"),
                        "edit",
                        java.util.Map.of("output_format", "gif"),
                        new GeminiGatewayResourceSupport.ResolvedBinaryFile("input.png", "image/png", new byte[] {1}),
                        null
                )
        );

        assertEquals("Gemini image edit 当前仅支持 output_format 为 png、jpeg 或 webp。", error.getMessage());
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

    private EditImageResponse singleEditImageResponse(byte[] imageBytes) {
        return EditImageResponse.builder()
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
        private final EditImageResponse editResponse;
        private GenerateImagesConfig lastConfig;
        private EditImageConfig lastEditConfig;
        private String lastPrompt;
        private java.util.List<ReferenceImage> lastReferenceImages;

        private TestGeminiImagesExecutor(GenerateImagesResponse response) {
            super(Mockito.mock(GeminiChatModelFactory.class), Mockito.mock(GatewayFileService.class), objectMapper);
            this.response = response;
            this.editResponse = null;
        }

        private TestGeminiImagesExecutor(EditImageResponse editResponse) {
            super(Mockito.mock(GeminiChatModelFactory.class), Mockito.mock(GatewayFileService.class), objectMapper);
            this.response = null;
            this.editResponse = editResponse;
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

        @Override
        EditImageResponse editImage(
                GatewayResourceExecutionContext context,
                String prompt,
                java.util.List<ReferenceImage> referenceImages,
                EditImageConfig config) {
            this.lastPrompt = prompt;
            this.lastReferenceImages = referenceImages;
            this.lastEditConfig = config;
            return editResponse;
        }
    }
}
