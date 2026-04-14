package com.prodigalgal.xaigateway.gateway.core.execution;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class EmbeddingsGatewayResourceExecutor implements GatewayResourceExecutor {

    private static final String DEFAULT_AZURE_API_VERSION = "2024-10-21";

    private final ObjectMapper objectMapper;

    public EmbeddingsGatewayResourceExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ExecutionBackend backend() {
        return ExecutionBackend.NATIVE;
    }

    @Override
    public boolean supports(String requestPath, CatalogCandidateView candidate) {
        if (!"/v1/embeddings".equals(requestPath) || candidate == null) {
            return false;
        }
        return switch (candidate.providerType()) {
            case OPENAI_DIRECT, OPENAI_COMPATIBLE, GEMINI_DIRECT -> true;
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
            case GEMINI_DIRECT -> executeGeminiEmbeddings(context, payload);
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

    private ResponseEntity<JsonNode> executeGeminiEmbeddings(
            GatewayResourceExecutionContext context,
            ObjectNode payload) {
        List<String> inputs = readEmbeddingInputs(payload.path("input"));
        CatalogSiteRequest siteRequest = buildSiteRequest(context);
        boolean batch = inputs.size() > 1;
        ObjectNode geminiPayload = objectMapper.createObjectNode();
        if (batch) {
            ArrayNode requests = geminiPayload.putArray("requests");
            for (String input : inputs) {
                requests.addObject()
                        .putObject("content")
                        .putArray("parts")
                        .addObject()
                        .put("text", input);
            }
        } else {
            geminiPayload.putObject("content")
                    .putArray("parts")
                    .addObject()
                    .put("text", inputs.isEmpty() ? "" : inputs.get(0));
        }

        String path = batch
                ? "/v1beta/models/" + encodePath(context.selectionResult().resolvedModelKey()) + ":batchEmbedContents?key=" + encodeQuery(context.apiKey())
                : "/v1beta/models/" + encodePath(context.selectionResult().resolvedModelKey()) + ":embedContent?key=" + encodeQuery(context.apiKey());

        JsonNode response = siteRequest.client().post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(geminiPayload)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null) {
            throw new IllegalStateException("Gemini embeddings 响应为空。");
        }

        ObjectNode openAiResponse = objectMapper.createObjectNode();
        openAiResponse.put("object", "list");
        openAiResponse.put("model", context.selectionResult().publicModel());
        ArrayNode data = openAiResponse.putArray("data");
        if (batch) {
            int index = 0;
            for (JsonNode item : response.path("embeddings")) {
                data.add(buildEmbeddingItem(index++, item.path("values")));
            }
        } else {
            data.add(buildEmbeddingItem(0, response.path("embedding").path("values")));
        }
        int promptTokens = response.path("usageMetadata").path("promptTokenCount").asInt(0);
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

    private ObjectNode buildEmbeddingItem(int index, JsonNode valuesNode) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("object", "embedding");
        item.put("index", index);
        ArrayNode embedding = item.putArray("embedding");
        for (JsonNode value : valuesNode) {
            embedding.add(value.asDouble());
        }
        return item;
    }

    private String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record CatalogSiteRequest(
            WebClient client,
            String path
    ) {
    }
}
