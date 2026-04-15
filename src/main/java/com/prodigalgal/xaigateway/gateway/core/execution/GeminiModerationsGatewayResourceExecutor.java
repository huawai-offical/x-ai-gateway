package com.prodigalgal.xaigateway.gateway.core.execution;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HarmCategory;
import com.google.genai.types.HarmProbability;
import com.google.genai.types.Part;
import com.google.genai.types.SafetyRating;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiChatModelFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class GeminiModerationsGatewayResourceExecutor implements GatewayResourceExecutor {

    private static final double FLAG_THRESHOLD = 0.5d;

    private final GeminiChatModelFactory geminiChatModelFactory;
    private final ObjectMapper objectMapper;

    public GeminiModerationsGatewayResourceExecutor(
            GeminiChatModelFactory geminiChatModelFactory,
            ObjectMapper objectMapper) {
        this.geminiChatModelFactory = geminiChatModelFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExecutionBackend backend() {
        return ExecutionBackend.NATIVE;
    }

    @Override
    public boolean supports(CanonicalResourceRequest request, CatalogCandidateView candidate) {
        return GeminiGatewayResourceSupport.supportsGeminiDirectCandidate(
                request,
                candidate,
                "/v1/moderations"
        );
    }

    @Override
    public ResponseEntity<JsonNode> executeJson(
            GatewayResourceExecutionContext context,
            JsonNode requestBody,
            String defaultModel) {
        if (context.request().operation() != TranslationOperation.MODERATION_CREATE) {
            throw new IllegalArgumentException("Gemini moderations executor 当前仅支持 /v1/moderations。");
        }
        ObjectNode payload = requireObjectPayload(requestBody, defaultModel);
        List<String> inputs = parseInputs(payload.path("input"));
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("moderations 请求缺少 input。");
        }
        ArrayNode results = objectMapper.createArrayNode();
        for (String input : inputs) {
            GenerateContentResponse response = generateModeration(context, input);
            results.add(buildModerationResult(response));
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("id", "modr_" + UUID.randomUUID().toString().replace("-", ""));
        body.put("model", GeminiGatewayResourceSupport.responseModel(context));
        body.set("results", results);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private Content moderationContent(String input) {
        return Content.fromParts(
                Part.fromText("Analyze the following user content for safety policy categories and reply with OK only."),
                Part.fromText(input)
        );
    }

    GenerateContentResponse generateModeration(GatewayResourceExecutionContext context, String input) {
        try (Client client = GeminiGatewayResourceSupport.createClient(geminiChatModelFactory, context)) {
            return client.models.generateContent(
                    context.selectionResult().resolvedModelKey(),
                    moderationContent(input),
                    GenerateContentConfig.builder()
                            .temperature(0.0f)
                            .responseMimeType("text/plain")
                            .build()
            );
        }
    }

    private ObjectNode buildModerationResult(GenerateContentResponse response) {
        Map<String, Boolean> categories = new LinkedHashMap<>(GeminiGatewayResourceSupport.defaultModerationCategories());
        Map<String, Double> scores = new LinkedHashMap<>(GeminiGatewayResourceSupport.defaultModerationScores());
        boolean blockedBySafety = applySafetyRatings(
                categories,
                scores,
                response.promptFeedback().map(feedback -> feedback.safetyRatings().orElse(List.of())).orElse(List.of())
        );
        blockedBySafety = applySafetyRatings(categories, scores, firstCandidateSafetyRatings(response)) || blockedBySafety;
        blockedBySafety = blockedBySafety
                || response.promptFeedback().flatMap(feedback -> feedback.blockReason()).isPresent()
                || isSafetyFinish(response);
        boolean flagged = blockedBySafety || scores.values().stream().anyMatch(score -> score >= FLAG_THRESHOLD);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("flagged", flagged);
        ObjectNode categoriesNode = result.putObject("categories");
        categories.forEach(categoriesNode::put);
        ObjectNode scoresNode = result.putObject("category_scores");
        scores.forEach(scoresNode::put);
        return result;
    }

    private boolean applySafetyRatings(
            Map<String, Boolean> categories,
            Map<String, Double> scores,
            List<SafetyRating> ratings) {
        boolean blocked = false;
        for (SafetyRating rating : ratings) {
            String categoryKey = mapCategory(rating.category().orElse(null));
            if (categoryKey == null) {
                blocked = blocked || rating.blocked().orElse(false);
                continue;
            }
            double score = mapProbability(rating.probability().orElse(null));
            scores.put(categoryKey, Math.max(scores.getOrDefault(categoryKey, 0.0d), score));
            categories.put(categoryKey, scores.get(categoryKey) >= FLAG_THRESHOLD || rating.blocked().orElse(false));
            blocked = blocked || rating.blocked().orElse(false);
        }
        return blocked;
    }

    private List<SafetyRating> firstCandidateSafetyRatings(GenerateContentResponse response) {
        return response.candidates()
                .flatMap(candidates -> candidates.stream().findFirst())
                .flatMap(candidate -> candidate.safetyRatings())
                .orElse(List.of());
    }

    private boolean isSafetyFinish(GenerateContentResponse response) {
        FinishReason finishReason = response.finishReason();
        if (finishReason == null || finishReason.knownEnum() == null) {
            return false;
        }
        return switch (finishReason.knownEnum()) {
            case SAFETY, BLOCKLIST, PROHIBITED_CONTENT, SPII, IMAGE_SAFETY, IMAGE_PROHIBITED_CONTENT -> true;
            default -> false;
        };
    }

    private String mapCategory(HarmCategory category) {
        if (category == null || category.knownEnum() == null) {
            return null;
        }
        return switch (category.knownEnum()) {
            case HARM_CATEGORY_HATE_SPEECH -> "hate";
            case HARM_CATEGORY_HARASSMENT -> "harassment";
            case HARM_CATEGORY_SEXUALLY_EXPLICIT -> "sexual";
            case HARM_CATEGORY_DANGEROUS_CONTENT -> "violence";
            default -> null;
        };
    }

    private double mapProbability(HarmProbability probability) {
        if (probability == null || probability.knownEnum() == null) {
            return 0.0d;
        }
        return switch (probability.knownEnum()) {
            case HIGH -> 1.0d;
            case MEDIUM -> 0.5d;
            case LOW -> 0.25d;
            case NEGLIGIBLE, HARM_PROBABILITY_UNSPECIFIED -> 0.0d;
        };
    }

    private List<String> parseInputs(JsonNode inputNode) {
        List<String> inputs = new ArrayList<>();
        if (inputNode == null || inputNode.isMissingNode() || inputNode.isNull()) {
            return inputs;
        }
        if (inputNode.isTextual()) {
            inputs.add(inputNode.asText());
            return inputs;
        }
        if (inputNode.isArray()) {
            for (JsonNode item : inputNode) {
                if (!item.isTextual()) {
                    throw new IllegalArgumentException("Gemini moderations 当前仅支持字符串或字符串数组输入。");
                }
                inputs.add(item.asText());
            }
            return inputs;
        }
        throw new IllegalArgumentException("Gemini moderations 当前仅支持字符串或字符串数组输入。");
    }

    private ObjectNode requireObjectPayload(JsonNode requestBody, String defaultModel) {
        if (requestBody == null || !requestBody.isObject()) {
            throw new IllegalArgumentException("请求体必须是 JSON object。");
        }
        ObjectNode payload = (ObjectNode) requestBody;
        if (!payload.hasNonNull("model") && defaultModel != null && !defaultModel.isBlank()) {
            payload.put("model", defaultModel);
        }
        return payload;
    }
}
