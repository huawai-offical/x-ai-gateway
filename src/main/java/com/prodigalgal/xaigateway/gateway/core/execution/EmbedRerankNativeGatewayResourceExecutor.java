package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
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
public class EmbedRerankNativeGatewayResourceExecutor implements GatewayResourceExecutor {

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    public EmbedRerankNativeGatewayResourceExecutor(ObjectMapper objectMapper, WebClient.Builder webClientBuilder) {
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public ExecutionBackend backend() {
        return ExecutionBackend.NATIVE;
    }

    @Override
    public boolean supports(CanonicalResourceRequest request, CatalogCandidateView candidate) {
        if (request == null || candidate == null) {
            return false;
        }
        if (candidate.siteKind() != UpstreamSiteKind.COHERE && candidate.siteKind() != UpstreamSiteKind.JINA) {
            return false;
        }
        return switch (request.normalizedPath()) {
            case "/v1/embeddings" -> request.resourceType() == TranslationResourceType.EMBEDDING
                    && candidate.supportsEmbeddings();
            case "/v1/rerank" -> request.resourceType() == TranslationResourceType.RERANK;
            default -> false;
        };
    }

    @Override
    public ResponseEntity<JsonNode> executeJson(
            GatewayResourceExecutionContext context,
            JsonNode requestBody,
            String defaultModel) {
        ObjectNode payload = requireObjectPayload(requestBody, defaultModel);
        return switch (context.selectionResult().selectedCandidate().candidate().siteKind()) {
            case COHERE -> executeCohere(context, payload);
            case JINA -> executeJina(context, payload);
            default -> throw new IllegalArgumentException("当前站点不是 Cohere/Jina native embed/rerank provider。");
        };
    }

    private ResponseEntity<JsonNode> executeCohere(GatewayResourceExecutionContext context, ObjectNode payload) {
        return switch (context.normalizedPath()) {
            case "/v1/embeddings" -> executeNativeJson(context, cohereEmbedPayload(context, payload), "/v2/embed", true);
            case "/v1/rerank" -> executeNativeJson(context, rerankPayload(context, payload), "/v2/rerank", false);
            default -> throw unsupportedPath();
        };
    }

    private ResponseEntity<JsonNode> executeJina(GatewayResourceExecutionContext context, ObjectNode payload) {
        return switch (context.normalizedPath()) {
            case "/v1/embeddings" -> executeNativeJson(context, openAiEmbeddingPayload(context, payload), "/v1/embeddings", true);
            case "/v1/rerank" -> executeNativeJson(context, rerankPayload(context, payload), "/v1/rerank", false);
            default -> throw unsupportedPath();
        };
    }

    private ResponseEntity<JsonNode> executeNativeJson(
            GatewayResourceExecutionContext context,
            ObjectNode upstreamPayload,
            String upstreamPath,
            boolean rewriteModel) {
        CatalogCandidateView candidate = context.selectionResult().selectedCandidate().candidate();
        String baseUrl = normalizeBaseUrl(context.credential().getBaseUrl());
        WebClient client = webClientBuilder.clone()
                .baseUrl(baseUrl)
                .defaultHeaders(headers -> applyAuth(headers, candidate.authStrategy(), context.apiKey()))
                .build();
        ResponseEntity<JsonNode> upstreamResponse = client.post()
                .uri(resolvePath(baseUrl, upstreamPath))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(upstreamPayload)
                .exchangeToMono(response -> response.toEntity(JsonNode.class))
                .block();
        if (upstreamResponse == null) {
            throw new IllegalStateException("上游 embed/rerank 响应为空。");
        }
        JsonNode body = upstreamResponse.getBody();
        if (rewriteModel && body instanceof ObjectNode objectNode) {
            objectNode.put("model", context.selectionResult().publicModel());
        }
        return ResponseEntity.status(upstreamResponse.getStatusCode())
                .headers(upstreamResponse.getHeaders())
                .contentType(upstreamResponse.getHeaders().getContentType() == null
                        ? MediaType.APPLICATION_JSON
                        : upstreamResponse.getHeaders().getContentType())
                .body(body);
    }

    private ObjectNode cohereEmbedPayload(GatewayResourceExecutionContext context, ObjectNode payload) {
        ObjectNode upstreamPayload = objectMapper.createObjectNode();
        upstreamPayload.put("model", context.selectionResult().resolvedModelKey());
        JsonNode input = payload.path("input");
        if (input.isTextual()) {
            upstreamPayload.putArray("texts").add(input.asText());
        } else if (input.isArray()) {
            ArrayNode texts = upstreamPayload.putArray("texts");
            for (JsonNode item : input) {
                if (!item.isTextual()) {
                    throw new IllegalArgumentException("Cohere native embeddings 当前仅支持文本 input。");
                }
                texts.add(item.asText());
            }
        } else {
            throw new IllegalArgumentException("Cohere native embeddings 请求缺少文本 input。");
        }
        if (!payload.hasNonNull("input_type")) {
            upstreamPayload.put("input_type", "search_document");
        }
        copyIfPresent(payload, upstreamPayload, "input_type", "embedding_types", "truncate");
        return upstreamPayload;
    }

    private ObjectNode openAiEmbeddingPayload(GatewayResourceExecutionContext context, ObjectNode payload) {
        ObjectNode upstreamPayload = payload.deepCopy();
        upstreamPayload.put("model", context.selectionResult().resolvedModelKey());
        return upstreamPayload;
    }

    private ObjectNode rerankPayload(GatewayResourceExecutionContext context, ObjectNode payload) {
        ObjectNode upstreamPayload = objectMapper.createObjectNode();
        upstreamPayload.put("model", context.selectionResult().resolvedModelKey());
        copyRequired(payload, upstreamPayload, "query");
        copyRequired(payload, upstreamPayload, "documents");
        copyIfPresent(payload, upstreamPayload, "top_n", "return_documents", "rank_fields", "max_chunks_per_doc");
        return upstreamPayload;
    }

    private ObjectNode requireObjectPayload(JsonNode requestBody, String defaultModel) {
        if (requestBody == null || !requestBody.isObject()) {
            throw new IllegalArgumentException("embed/rerank 请求体必须是 JSON object。");
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

    private void copyRequired(ObjectNode source, ObjectNode target, String field) {
        JsonNode value = source.path(field);
        if (value.isMissingNode() || value.isNull()) {
            throw new IllegalArgumentException("native rerank 请求缺少 " + field + "。");
        }
        target.set(field, value.deepCopy());
    }

    private void copyIfPresent(ObjectNode source, ObjectNode target, String... fields) {
        for (String field : fields) {
            JsonNode value = source.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                target.set(field, value.deepCopy());
            }
        }
    }

    private void applyAuth(HttpHeaders headers, AuthStrategy authStrategy, String secret) {
        if (secret == null || secret.isBlank()) {
            return;
        }
        if (authStrategy == AuthStrategy.API_KEY_HEADER) {
            headers.set("x-api-key", secret);
            return;
        }
        headers.setBearerAuth(secret);
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Cohere/Jina native executor 缺少 baseUrl。");
        }
        return baseUrl.replaceAll("/+$", "");
    }

    private String resolvePath(String baseUrl, String upstreamPath) {
        if (baseUrl.endsWith("/v1") && upstreamPath.startsWith("/v1/")) {
            return upstreamPath.substring(3);
        }
        if (baseUrl.endsWith("/v2") && upstreamPath.startsWith("/v2/")) {
            return upstreamPath.substring(3);
        }
        if (baseUrl.endsWith("/compatibility/v1") && upstreamPath.startsWith("/v2/")) {
            throw new IllegalArgumentException("Cohere native embed/rerank 需要 Cohere native baseUrl，不能使用 compatibility/v1 generic OpenAI-compatible baseUrl。");
        }
        return upstreamPath;
    }

    private IllegalArgumentException unsupportedPath() {
        return new IllegalArgumentException("Cohere/Jina native executor 仅支持 /v1/embeddings 与 /v1/rerank。");
    }
}
