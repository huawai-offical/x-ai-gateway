package com.prodigalgal.xaigateway.gateway.core.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyGovernanceService;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayObservabilityService;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.support.DefaultClientCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayEmbeddingExecutionServiceTests {

    @Test
    void shouldRewriteModelForUpstreamAndRestorePublicModelInResponse() throws Exception {
        GatewayRouteSelectionService gatewayRouteSelectionService = Mockito.mock(GatewayRouteSelectionService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        GatewayObservabilityService gatewayObservabilityService = Mockito.mock(GatewayObservabilityService.class);
        DistributedKeyGovernanceService distributedKeyGovernanceService = Mockito.mock(DistributedKeyGovernanceService.class);
        AccountSelectionService accountSelectionService = Mockito.mock(AccountSelectionService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        AtomicReference<String> upstreamUrl = new AtomicReference<>();
        AtomicReference<String> upstreamAuthorization = new AtomicReference<>();
        AtomicReference<String> upstreamBody = new AtomicReference<>();

        ExchangeFunction exchangeFunction = request -> {
            upstreamUrl.set(request.url().toString());
            upstreamAuthorization.set(request.headers().getFirst(HttpHeaders.AUTHORIZATION));
            upstreamBody.set(extractBody(request));
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("""
                            {
                              "object":"list",
                              "data":[{"object":"embedding","index":0,"embedding":[0.1,0.2]}],
                              "model":"text-embedding-3-large"
                            }
                            """)
                    .build());
        };

        GatewayEmbeddingExecutionService service = new GatewayEmbeddingExecutionService(
                gatewayRouteSelectionService,
                upstreamCredentialRepository,
                credentialCryptoService,
                gatewayObservabilityService,
                distributedKeyGovernanceService,
                accountSelectionService,
                WebClient.builder().exchangeFunction(exchangeFunction)
        );

        when(gatewayObservabilityService.nextRequestId()).thenReturn("req-embedding-1");
        when(gatewayRouteSelectionService.select(any())).thenReturn(selectionResult());

        UpstreamCredentialEntity credential = new UpstreamCredentialEntity();
        credential.setProviderType(ProviderType.OPENAI_DIRECT);
        credential.setBaseUrl("https://api.openai.com");
        credential.setApiKeyCiphertext("cipher");
        when(upstreamCredentialRepository.findById(101L)).thenReturn(Optional.of(credential));
        when(credentialCryptoService.decrypt("cipher")).thenReturn("upstream-key");

        JsonNode response = service.executeOpenAiEmbeddings(
                        "sk-gw-test",
                        objectMapper.readTree("""
                                {
                                  "model":"writer-fast",
                                  "input":"hello embeddings"
                                }
                                """))
                .getBody();

        assertEquals("writer-fast", response.path("model").asText());
        assertEquals("https://api.openai.com/v1/embeddings", upstreamUrl.get());
        assertEquals("Bearer upstream-key", upstreamAuthorization.get());
        assertTrue(upstreamBody.get().contains("\"model\":\"text-embedding-3-large\""));
        assertTrue(upstreamBody.get().contains("\"input\":\"hello embeddings\""));
        verify(gatewayObservabilityService).recordRouteDecision(Mockito.eq("req-embedding-1"), any());
        verify(gatewayRouteSelectionService).recordSuccessfulSelection(any());
    }

    private RouteSelectionResult selectionResult() {
        CatalogCandidateView candidate = new CatalogCandidateView(
                101L,
                "openai-primary",
                ProviderType.OPENAI_DIRECT,
                "https://api.openai.com",
                "text-embedding-3-large",
                "text-embedding-3-large",
                List.of("openai"),
                false,
                true,
                false,
                false,
                false,
                false,
                ReasoningTransport.NONE
        );
        RouteCandidateView routeCandidateView = new RouteCandidateView(candidate, 11L, 10, 100);
        return new RouteSelectionResult(
                1L,
                "sk-gw-test",
                "writer-fast",
                "writer-fast",
                "text-embedding-3-large",
                "openai",
                "prefix-hash",
                "fingerprint",
                "text-embedding-3-large",
                RouteSelectionSource.PREFIX_AFFINITY,
                routeCandidateView,
                List.of(routeCandidateView)
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
            public java.util.Map<String, Object> hints() {
                return Collections.emptyMap();
            }
        }).block();
        return mockRequest.getBodyAsString().block();
    }
}
