package com.prodigalgal.xaigateway.gateway.core.execution;

import com.google.genai.types.BlockedReason;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponsePromptFeedback;
import com.google.genai.types.HarmCategory;
import com.google.genai.types.HarmProbability;
import com.google.genai.types.Part;
import com.google.genai.types.SafetyRating;
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
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiModerationsGatewayResourceExecutorTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSupportOnlyGeminiDirectModerationCandidates() {
        TestGeminiModerationsExecutor executor = new TestGeminiModerationsExecutor(List.of(safeResponse()));

        assertTrue(executor.supports(request("/v1/moderations"), candidate(UpstreamSiteKind.GEMINI_DIRECT)));
        assertFalse(executor.supports(request("/v1/moderations"), candidate(UpstreamSiteKind.VERTEX_AI)));
        assertFalse(executor.supports(request("/v1/images/generations"), candidate(UpstreamSiteKind.GEMINI_DIRECT)));
    }

    @Test
    void shouldMapSingleStringModerationToOpenAiCompatibleResult() {
        TestGeminiModerationsExecutor executor = new TestGeminiModerationsExecutor(List.of(
                responseWithSafetyRatings(
                        SafetyRating.builder()
                                .category(HarmCategory.Known.HARM_CATEGORY_HATE_SPEECH)
                                .probability(HarmProbability.Known.HIGH)
                                .blocked(false)
                                .build(),
                        SafetyRating.builder()
                                .category(HarmCategory.Known.HARM_CATEGORY_HARASSMENT)
                                .probability(HarmProbability.Known.MEDIUM)
                                .blocked(false)
                                .build()
                )
        ));
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("input", "I hate you");

        ResponseEntity<JsonNode> response = executor.executeJson(context(), payload, "gemini-2.0-flash");

        JsonNode result = response.getBody().path("results").get(0);
        assertEquals("gemini-2.0-flash", response.getBody().path("model").asText());
        assertTrue(result.path("flagged").asBoolean());
        assertTrue(result.path("categories").path("hate").asBoolean());
        assertTrue(result.path("categories").path("harassment").asBoolean());
        assertEquals(1.0d, result.path("category_scores").path("hate").asDouble());
        assertEquals(0.5d, result.path("category_scores").path("harassment").asDouble());
        assertEquals(1, executor.recordedInputs.size());
        assertEquals("I hate you", executor.recordedInputs.getFirst());
    }

    @Test
    void shouldHandleStringArrayInputAndPromptSafetyBlocks() {
        TestGeminiModerationsExecutor executor = new TestGeminiModerationsExecutor(List.of(
                responseWithPromptBlock(),
                safeResponse()
        ));
        ArrayNode inputs = objectMapper.createArrayNode().add("bad").add("safe");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("input", inputs);

        ResponseEntity<JsonNode> response = executor.executeJson(context(), payload, "gemini-2.0-flash");

        assertEquals(2, response.getBody().path("results").size());
        assertTrue(response.getBody().path("results").get(0).path("flagged").asBoolean());
        assertFalse(response.getBody().path("results").get(1).path("flagged").asBoolean());
        assertEquals(List.of("bad", "safe"), List.copyOf(executor.recordedInputs));
    }

    @Test
    void shouldRejectUnsupportedModerationInputShape() {
        TestGeminiModerationsExecutor executor = new TestGeminiModerationsExecutor(List.of(safeResponse()));
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("input", objectMapper.createObjectNode().put("text", "bad"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> executor.executeJson(context(), payload, "gemini-2.0-flash")
        );

        assertEquals("Gemini moderations 当前仅支持字符串或字符串数组输入。", error.getMessage());
    }

    private GenerateContentResponse safeResponse() {
        return GenerateContentResponse.builder()
                .candidates(List.of(
                        Candidate.builder()
                                .content(Content.fromParts(Part.fromText("OK")))
                                .build()
                ))
                .build();
    }

    private GenerateContentResponse responseWithPromptBlock() {
        return GenerateContentResponse.builder()
                .promptFeedback(
                        GenerateContentResponsePromptFeedback.builder()
                                .blockReason(BlockedReason.Known.SAFETY)
                                .safetyRatings(
                                        SafetyRating.builder()
                                                .category(HarmCategory.Known.HARM_CATEGORY_DANGEROUS_CONTENT)
                                                .probability(HarmProbability.Known.HIGH)
                                                .blocked(true)
                                                .build()
                                )
                                .build()
                )
                .candidates(List.of(
                        Candidate.builder()
                                .content(Content.fromParts(Part.fromText("blocked")))
                                .finishReason(FinishReason.Known.SAFETY)
                                .build()
                ))
                .build();
    }

    private GenerateContentResponse responseWithSafetyRatings(SafetyRating... ratings) {
        return GenerateContentResponse.builder()
                .candidates(List.of(
                        Candidate.builder()
                                .content(Content.fromParts(Part.fromText("OK")))
                                .safetyRatings(ratings)
                                .build()
                ))
                .build();
    }

    private GatewayResourceExecutionContext context() {
        CatalogCandidateView candidate = candidate(UpstreamSiteKind.GEMINI_DIRECT);
        RouteCandidateView routeCandidateView = new RouteCandidateView(candidate, 11L, 10, 100);
        RouteSelectionResult selectionResult = new RouteSelectionResult(
                1L,
                "sk-gw-test",
                "gemini-2.0-flash",
                "gemini-2.0-flash",
                "gemini-2.0-flash",
                "openai",
                "prefix",
                "fingerprint",
                "gemini-2.0-flash",
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
                request("/v1/moderations")
        );
    }

    private CanonicalResourceRequest request(String path) {
        return new CanonicalResourceRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.OPENAI,
                "POST",
                path,
                path,
                java.util.Map.of(),
                "gemini-2.0-flash",
                TranslationResourceType.MODERATION,
                TranslationOperation.MODERATION_CREATE,
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
                "gemini-moderation",
                ProviderType.GEMINI_DIRECT,
                1L,
                ProviderFamily.GEMINI,
                siteKind,
                siteKind == UpstreamSiteKind.GEMINI_DIRECT ? AuthStrategy.API_KEY_QUERY : AuthStrategy.BEARER,
                PathStrategy.GEMINI_V1BETA_MODELS,
                ErrorSchemaStrategy.GEMINI_ERROR,
                "https://example.com",
                "gemini-2.0-flash",
                "gemini-2.0-flash",
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

    private final class TestGeminiModerationsExecutor extends GeminiModerationsGatewayResourceExecutor {

        private final Queue<GenerateContentResponse> responses;
        private final ArrayDeque<String> recordedInputs = new ArrayDeque<>();

        private TestGeminiModerationsExecutor(List<GenerateContentResponse> responses) {
            super(Mockito.mock(GeminiChatModelFactory.class), objectMapper);
            this.responses = new ArrayDeque<>(responses);
        }

        @Override
        GenerateContentResponse generateModeration(GatewayResourceExecutionContext context, String input) {
            recordedInputs.add(input);
            GenerateContentResponse response = responses.poll();
            if (response == null) {
                throw new IllegalStateException("missing test response");
            }
            return response;
        }
    }
}
