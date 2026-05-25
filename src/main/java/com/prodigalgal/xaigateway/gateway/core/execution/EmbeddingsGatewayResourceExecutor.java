package com.prodigalgal.xaigateway.gateway.core.execution;

import com.google.genai.Client;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiChatModelFactory;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class EmbeddingsGatewayResourceExecutor implements GatewayResourceExecutor {

    private static final String DEFAULT_AZURE_API_VERSION = "2024-10-21";

    private final ObjectMapper objectMapper;
    private final GeminiChatModelFactory geminiChatModelFactory;

    public EmbeddingsGatewayResourceExecutor(
            ObjectMapper objectMapper,
            GeminiChatModelFactory geminiChatModelFactory) {
        this.objectMapper = objectMapper;
        this.geminiChatModelFactory = geminiChatModelFactory;
    }

    @Override
    public ExecutionBackend backend() {
        return ExecutionBackend.NATIVE;
    }

    @Override
    public boolean supports(CanonicalResourceRequest request, CatalogCandidateView candidate) {
        if (request == null || !"/v1/embeddings".equals(request.normalizedPath()) || candidate == null) {
            return false;
        }
        if (candidate.siteKind() == UpstreamSiteKind.COHERE || candidate.siteKind() == UpstreamSiteKind.JINA) {
            return false;
        }
        return switch (candidate.providerType()) {
            case OPENAI_DIRECT, OPENAI_COMPATIBLE -> true;
            case GEMINI_DIRECT -> supportsGoogleGenAiEmbeddingsCandidate(candidate);
            case ANTHROPIC_DIRECT, OLLAMA_DIRECT -> false;
        };
    }

    @Override
    public ResponseEntity<JsonNode> executeJson(
            GatewayResourceExecutionContext context,
            JsonNode requestBody,
            String defaultModel) {
        ObjectNode payload = requireObjectPayload(requestBody, defaultModel);
        return switch (context.selectionResult().selectedCandidate().candidate().providerType()) {
            case OPENAI_DIRECT, OPENAI_COMPATIBLE -> executeOpenAiCompatibleEmbeddings(context, payload);
            case GEMINI_DIRECT -> executeGoogleGenAiEmbeddings(context, payload);
            case ANTHROPIC_DIRECT, OLLAMA_DIRECT ->
                    throw new IllegalArgumentException("当前站点不支持 embeddings 执行。");
        };
    }

    private ResponseEntity<JsonNode> executeOpenAiCompatibleEmbeddings(
            GatewayResourceExecutionContext context,
            ObjectNode payload) {
        ObjectNode upstreamPayload = payload.deepCopy();
        upstreamPayload.put("model", context.selectionResult().resolvedModelKey());
        CatalogSiteRequest siteRequest = buildSiteRequest(context);
        ResponseEntity<JsonNode> upstreamResponse = siteRequest.client().post()
                .uri(siteRequest.path())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(upstreamPayload)
                .exchangeToMono(response -> response.toEntity(JsonNode.class))
                .block();
        if (upstreamResponse == null) {
            throw new IllegalStateException("上游 embeddings 响应为空。");
        }
        JsonNode body = upstreamResponse.getBody();
        if (body instanceof ObjectNode objectNode) {
            objectNode.put("model", context.selectionResult().publicModel());
        }
        return ResponseEntity.status(upstreamResponse.getStatusCode())
                .headers(upstreamResponse.getHeaders())
                .contentType(upstreamResponse.getHeaders().getContentType() == null
                        ? MediaType.APPLICATION_JSON
                        : upstreamResponse.getHeaders().getContentType())
                .body(body);
    }

    private ResponseEntity<JsonNode> executeGoogleGenAiEmbeddings(
            GatewayResourceExecutionContext context,
            ObjectNode payload) {
        List<String> inputs = readEmbeddingInputs(payload.path("input"));
        EmbedContentResponse response;
        try (Client client = GeminiGatewayResourceSupport.createClient(geminiChatModelFactory, context)) {
            response = client.models.embedContent(
                    context.selectionResult().resolvedModelKey(),
                    inputs,
                    EmbedContentConfig.builder().build()
            );
        }
        List<ContentEmbedding> embeddings = response == null ? List.of() : response.embeddings().orElse(List.of());
        if (embeddings.isEmpty()) {
            throw new IllegalStateException("Google GenAI embeddings 响应为空。");
        }

        ObjectNode openAiResponse = objectMapper.createObjectNode();
        openAiResponse.put("object", "list");
        openAiResponse.put("model", GeminiGatewayResourceSupport.responseModel(context));
        ArrayNode data = openAiResponse.putArray("data");
        for (int index = 0; index < embeddings.size(); index++) {
            data.add(buildEmbeddingItem(index, embeddings.get(index).values().orElse(List.of())));
        }
        int promptTokens = response.metadata()
                .flatMap(metadata -> metadata.billableCharacterCount())
                .orElse(0);
        openAiResponse.putObject("usage")
                .put("prompt_tokens", promptTokens)
                .put("total_tokens", promptTokens);
        return ResponseEntity.ok(openAiResponse);
    }

    private CatalogSiteRequest buildSiteRequest(GatewayResourceExecutionContext context) {
        CatalogCandidateView candidate = context.selectionResult().selectedCandidate().candidate();
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(context.credential().getBaseUrl().replaceAll("/+$", ""));
        String path = "/v1/embeddings";
        if (candidate.pathStrategy() == PathStrategy.AZURE_OPENAI_DEPLOYMENT) {
            builder.defaultHeader("api-key", context.apiKey());
            path = "/openai/deployments/" + encodePath(context.selectionResult().resolvedModelKey()) + "/embeddings?api-version=" + DEFAULT_AZURE_API_VERSION;
        } else if (candidate.authStrategy() == AuthStrategy.BEARER) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + context.apiKey());
        } else if (candidate.authStrategy() == AuthStrategy.API_KEY_HEADER) {
            builder.defaultHeader("x-api-key", context.apiKey());
        }
        return new CatalogSiteRequest(builder.build(), path);
    }

    private ObjectNode requireObjectPayload(JsonNode requestBody, String defaultModel) {
        if (requestBody == null || !requestBody.isObject()) {
            throw new IllegalArgumentException("请求体必须是 JSON object。");
        }
        ObjectNode payload = (ObjectNode) requestBody;
        if (!payload.hasNonNull("model")) {
            if (defaultModel == null || defaultModel.isBlank()) {
                throw new IllegalArgumentException("请求缺少 model。");
            }
            payload.put("model", defaultModel);
        }
        return payload;
    }

    private List<String> readEmbeddingInputs(JsonNode inputNode) {
        List<String> values = new ArrayList<>();
        if (inputNode == null || inputNode.isMissingNode() || inputNode.isNull()) {
            return values;
        }
        if (inputNode.isTextual()) {
            values.add(inputNode.asText());
            return values;
        }
        if (inputNode.isArray()) {
            for (JsonNode item : inputNode) {
                if (item.isTextual()) {
                    values.add(item.asText());
                }
            }
            return values;
        }
        throw new IllegalArgumentException("当前 embeddings 仅支持文本输入。");
    }

    private ObjectNode buildEmbeddingItem(int index, List<Float> values) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("object", "embedding");
        item.put("index", index);
        ArrayNode embedding = item.putArray("embedding");
        for (Float value : values) {
            embedding.add(value == null ? 0.0d : value.doubleValue());
        }
        return item;
    }

    private String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private boolean supportsGoogleGenAiEmbeddingsCandidate(CatalogCandidateView candidate) {
        return GeminiGatewayResourceSupport.supportsGoogleGenAiSite(candidate.siteKind(), candidate.authStrategy())
                && candidate.pathStrategy() == PathStrategy.GEMINI_V1BETA_MODELS;
    }

    private record CatalogSiteRequest(
            WebClient client,
            String path
    ) {
    }
}
