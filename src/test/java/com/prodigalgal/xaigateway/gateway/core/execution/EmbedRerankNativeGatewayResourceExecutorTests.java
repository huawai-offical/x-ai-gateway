package com.prodigalgal.xaigateway.gateway.core.execution;

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
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbedRerankNativeGatewayResourceExecutorTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExecuteCohereNativeEmbedWithV2ShapeAndBearerAuth() throws Exception {
        AtomicReference<String> upstreamUrl = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> upstreamBody = new AtomicReference<>();
        EmbedRerankNativeGatewayResourceExecutor executor = executor(request -> {
            upstreamUrl.set(request.url().toString());
            authorization.set(request.headers().getFirst(HttpHeaders.AUTHORIZATION));
            upstreamBody.set(extractBody(request));
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("""
                            {"id":"embd_1","embeddings":{"float":[[0.1,0.2]]},"meta":{"billed_units":{"input_tokens":1}}}
                            """)
                    .build());
        });

        JsonNode response = executor.executeJson(
                context(UpstreamSiteKind.COHERE, "https://api.cohere.ai", "/v1/embeddings", "embed-v4.0", "embed-v4"),
                objectMapper.readTree("""
                        {"model":"embed-v4","input":["hello"],"input_type":"search_query"}
                        """),
                null
        ).getBody();

        assertEquals("https://api.cohere.ai/v2/embed", upstreamUrl.get());
        assertEquals("Bearer upstream-key", authorization.get());
        assertTrue(upstreamBody.get().contains("\"model\":\"embed-v4.0\""));
        assertTrue(upstreamBody.get().contains("\"texts\":[\"hello\"]"));
        assertEquals("embed-v4", response.path("model").asText());
        assertEquals(0.1d, response.path("embeddings").path("float").path(0).path(0).asDouble());
        assertEquals(1, response.path("meta").path("billed_units").path("input_tokens").asInt());
    }

    @Test
    void shouldExecuteCohereNativeRerankWithV2ShapeAndResultEvidence() throws Exception {
        AtomicReference<String> upstreamUrl = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> upstreamBody = new AtomicReference<>();
        EmbedRerankNativeGatewayResourceExecutor executor = executor(request -> {
            upstreamUrl.set(request.url().toString());
            authorization.set(request.headers().getFirst(HttpHeaders.AUTHORIZATION));
            upstreamBody.set(extractBody(request));
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("""
                            {"id":"rank_1","results":[{"index":0,"relevance_score":0.98}],"meta":{"billed_units":{"search_units":1}}}
                            """)
                    .build());
        });

        JsonNode response = executor.executeJson(
                context(UpstreamSiteKind.COHERE, "https://api.cohere.ai", "/v1/rerank", "rerank-v3.5", "rerank-v3"),
                objectMapper.readTree("""
                        {"model":"rerank-v3","query":"hello","documents":["hello","bye"],"top_n":1}
                        """),
                null
        ).getBody();

        assertEquals("https://api.cohere.ai/v2/rerank", upstreamUrl.get());
        assertEquals("Bearer upstream-key", authorization.get());
        assertTrue(upstreamBody.get().contains("\"model\":\"rerank-v3.5\""));
        assertTrue(upstreamBody.get().contains("\"query\":\"hello\""));
        assertTrue(upstreamBody.get().contains("\"documents\":[\"hello\",\"bye\"]"));
        assertEquals(0, response.path("results").path(0).path("index").asInt());
        assertEquals(0.98d, response.path("results").path(0).path("relevance_score").asDouble());
        assertEquals(1, response.path("meta").path("billed_units").path("search_units").asInt());
    }

    @Test
    void shouldExecuteJinaNativeRerankWithoutChatOrFileAssumptions() throws Exception {
        AtomicReference<String> upstreamUrl = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> upstreamBody = new AtomicReference<>();
        EmbedRerankNativeGatewayResourceExecutor executor = executor(request -> {
            upstreamUrl.set(request.url().toString());
            authorization.set(request.headers().getFirst(HttpHeaders.AUTHORIZATION));
            upstreamBody.set(extractBody(request));
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("""
                            {"model":"jina-reranker-v2-base-multilingual","results":[{"index":0,"relevance_score":0.9}]}
                            """)
                    .build());
        });

        JsonNode response = executor.executeJson(
                context(UpstreamSiteKind.JINA, "https://api.jina.ai/v1", "/v1/rerank",
                        "jina-reranker-v2-base-multilingual", "jina-reranker"),
                objectMapper.readTree("""
                        {"model":"jina-reranker","query":"hello","documents":["hello","bye"],"top_n":1}
                        """),
                null
        ).getBody();

        assertEquals("https://api.jina.ai/v1/rerank", upstreamUrl.get());
        assertEquals("Bearer upstream-key", authorization.get());
        assertTrue(upstreamBody.get().contains("\"model\":\"jina-reranker-v2-base-multilingual\""));
        assertEquals(1, response.path("results").size());
    }

    @Test
    void shouldOnlySupportCohereAndJinaEmbedRerankPaths() {
        EmbedRerankNativeGatewayResourceExecutor executor = executor(request -> Mono.empty());
        CatalogCandidateView cohere = candidate(UpstreamSiteKind.COHERE, "https://api.cohere.ai", "embed-v4.0", true);

        assertTrue(executor.supports(request("/v1/embeddings", TranslationResourceType.EMBEDDING, TranslationOperation.EMBEDDING_CREATE), cohere));
        assertTrue(executor.supports(request("/v1/rerank", TranslationResourceType.RERANK, TranslationOperation.RERANK_CREATE), cohere));
        assertFalse(executor.supports(request("/v1/embeddings", TranslationResourceType.CHAT, TranslationOperation.CHAT_COMPLETION), cohere));
        assertFalse(executor.supports(request("/v1/rerank", TranslationResourceType.FILE, TranslationOperation.FILE_CREATE), cohere));
        assertFalse(executor.supports(request("/v1/chat/completions", TranslationResourceType.CHAT, TranslationOperation.CHAT_COMPLETION), cohere));
        assertFalse(executor.supports(request("/v1/files", TranslationResourceType.FILE, TranslationOperation.FILE_CREATE), cohere));
    }

    @Test
    void shouldRejectCohereCompatibilityBaseUrlForNativeExecutor() {
        EmbedRerankNativeGatewayResourceExecutor executor = executor(request -> Mono.empty());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> executor.executeJson(
                context(UpstreamSiteKind.COHERE, "https://api.cohere.ai/compatibility/v1",
                        "/v1/embeddings", "embed-v4.0", "embed-v4"),
                objectMapper.createObjectNode().put("model", "embed-v4").put("input", "hello"),
                null
        ));

        assertTrue(error.getMessage().contains("不能使用 compatibility/v1"));
    }

    private EmbedRerankNativeGatewayResourceExecutor executor(ExchangeFunction exchangeFunction) {
        return new EmbedRerankNativeGatewayResourceExecutor(objectMapper, WebClient.builder().exchangeFunction(exchangeFunction));
    }

    private GatewayResourceExecutionContext context(
            UpstreamSiteKind siteKind,
            String baseUrl,
            String path,
            String resolvedModel,
            String publicModel) {
        RouteCandidateView routeCandidate = new RouteCandidateView(
                candidate(siteKind, baseUrl, resolvedModel, true),
                11L,
                10,
                100
        );
        RouteSelectionResult selectionResult = new RouteSelectionResult(
                1L,
                "sk-gw-test",
                publicModel,
                publicModel,
                resolvedModel,
                "openai",
                "prefix",
                "fingerprint",
                resolvedModel,
                RouteSelectionSource.WEIGHTED_HASH,
                routeCandidate,
                List.of(routeCandidate)
        );
        UpstreamCredentialEntity credential = new UpstreamCredentialEntity();
        credential.setBaseUrl(baseUrl);
        credential.setProviderType(ProviderType.OPENAI_COMPATIBLE);
        return new GatewayResourceExecutionContext(selectionResult, credential, "upstream-key", path);
    }

    private CatalogCandidateView candidate(UpstreamSiteKind siteKind, String baseUrl, String model, boolean supportsEmbeddings) {
        return new CatalogCandidateView(
                101L,
                "candidate",
                ProviderType.OPENAI_COMPATIBLE,
                1L,
                ProviderFamily.OPENAI,
                siteKind,
                AuthStrategy.BEARER,
                PathStrategy.OPENAI_V1,
                ErrorSchemaStrategy.OPENAI_ERROR,
                baseUrl,
                model,
                model,
                List.of("openai"),
                false,
                false,
                false,
                supportsEmbeddings,
                false,
                false,
                false,
                false,
                ReasoningTransport.NONE,
                InteropCapabilityLevel.NATIVE
        );
    }

    private CanonicalResourceRequest request(
            String path,
            TranslationResourceType resourceType,
            TranslationOperation operation) {
        ObjectNode body = objectMapper.createObjectNode().put("model", "model");
        return new CanonicalResourceRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.OPENAI,
                "POST",
                path,
                path,
                Map.of(),
                "model",
                resourceType,
                operation,
                body,
                Map.of(),
                List.of(),
                false,
                false
        );
    }

    private String extractBody(org.springframework.web.reactive.function.client.ClientRequest request) {
        MockClientHttpRequest mockRequest = new MockClientHttpRequest(request.method(), URI.create("https://example.com"));
        request.headers().forEach((key, values) -> mockRequest.getHeaders().put(key, new ArrayList<>(values)));
        request.body().insert(mockRequest, new BodyInserter.Context() {
            @Override
            public List<HttpMessageWriter<?>> messageWriters() {
                return ExchangeStrategies.withDefaults().messageWriters();
            }

            @Override
            public Optional<ServerHttpRequest> serverRequest() {
                return Optional.empty();
            }

            @Override
            public Map<String, Object> hints() {
                return Collections.emptyMap();
            }
        }).block();
        return mockRequest.getBodyAsString().block();
    }
}
