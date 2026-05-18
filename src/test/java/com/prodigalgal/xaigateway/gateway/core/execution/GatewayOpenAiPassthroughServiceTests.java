package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.admin.application.ErrorRuleService;
import com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyGovernanceService;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialAuthKind;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialMaterialResolver;
import com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayObservabilityService;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
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
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayOpenAiPassthroughServiceTests {

    @Test
    void shouldExecuteOpenAiDirectInputTokensWithResolvedModelAndOriginalInput() throws Exception {
        GatewayRouteSelectionService routeSelectionService = Mockito.mock(GatewayRouteSelectionService.class);
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        GatewayObservabilityService observabilityService = Mockito.mock(GatewayObservabilityService.class);
        DistributedKeyGovernanceService governanceService = Mockito.mock(DistributedKeyGovernanceService.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        ErrorRuleService errorRuleService = Mockito.mock(ErrorRuleService.class);
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
                            {"object":"response.input_tokens","input_tokens":23}
                            """)
                    .build());
        };

        GatewayOpenAiPassthroughService service = new GatewayOpenAiPassthroughService(
                routeSelectionService,
                credentialRepository,
                Mockito.mock(CredentialCryptoService.class),
                observabilityService,
                governanceService,
                Mockito.mock(AccountSelectionService.class),
                credentialMaterialResolver,
                errorRuleService,
                WebClient.builder().exchangeFunction(exchangeFunction)
        );
        RouteSelectionResult selectionResult = selectionResult(ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT);
        when(observabilityService.nextRequestId()).thenReturn("req-input-tokens-1");
        when(routeSelectionService.select(any())).thenReturn(selectionResult);
        UpstreamCredentialEntity credential = credential(101L, ProviderType.OPENAI_DIRECT, "https://api.openai.com");
        when(credentialRepository.findById(101L)).thenReturn(Optional.of(credential));
        when(credentialMaterialResolver.resolve(selectionResult, credential))
                .thenReturn(new ResolvedCredentialMaterial(101L, 1L, CredentialAuthKind.API_KEY, "upstream-key", null, Map.of(), null, "credential"));

        JsonNode response = service.executeOpenAiDirectJson(
                "sk-gw-test",
                "/v1/responses/input_tokens",
                objectMapper.readTree("""
                        {
                          "model":"writer-fast",
                          "input":"hello"
                        }
                        """),
                null
        ).getBody();

        assertEquals("response.input_tokens", response.path("object").asText());
        assertEquals(23, response.path("input_tokens").asInt());
        assertEquals("https://api.openai.com/v1/responses/input_tokens", upstreamUrl.get());
        assertEquals("Bearer upstream-key", upstreamAuthorization.get());
        assertTrue(upstreamBody.get().contains("\"model\":\"gpt-4.1\""));
        assertTrue(upstreamBody.get().contains("\"input\":\"hello\""));
        verify(routeSelectionService).recordSuccessfulSelection(selectionResult);
        verify(governanceService).releaseConcurrency(selectionResult.governanceReservationKey());
    }

    @Test
    void shouldExecuteOpenAiDirectCompactWithResolvedModelAndOriginalInput() throws Exception {
        GatewayRouteSelectionService routeSelectionService = Mockito.mock(GatewayRouteSelectionService.class);
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        GatewayObservabilityService observabilityService = Mockito.mock(GatewayObservabilityService.class);
        DistributedKeyGovernanceService governanceService = Mockito.mock(DistributedKeyGovernanceService.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        ErrorRuleService errorRuleService = Mockito.mock(ErrorRuleService.class);
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
                            {"id":"resp_compact_1","object":"response.compaction","output":[{"id":"cmp_1","type":"compaction","encrypted_content":"opaque"}],"usage":{"input_tokens":8,"output_tokens":4,"total_tokens":12}}
                            """)
                    .build());
        };

        GatewayOpenAiPassthroughService service = new GatewayOpenAiPassthroughService(
                routeSelectionService,
                credentialRepository,
                Mockito.mock(CredentialCryptoService.class),
                observabilityService,
                governanceService,
                Mockito.mock(AccountSelectionService.class),
                credentialMaterialResolver,
                errorRuleService,
                WebClient.builder().exchangeFunction(exchangeFunction)
        );
        RouteSelectionResult selectionResult = selectionResult(ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT);
        when(observabilityService.nextRequestId()).thenReturn("req-compact-1");
        when(routeSelectionService.select(any())).thenReturn(selectionResult);
        UpstreamCredentialEntity credential = credential(101L, ProviderType.OPENAI_DIRECT, "https://api.openai.com");
        when(credentialRepository.findById(101L)).thenReturn(Optional.of(credential));
        when(credentialMaterialResolver.resolve(selectionResult, credential))
                .thenReturn(new ResolvedCredentialMaterial(101L, 1L, CredentialAuthKind.API_KEY, "upstream-key", null, Map.of(), null, "credential"));

        JsonNode response = service.executeOpenAiDirectJson(
                "sk-gw-test",
                "/v1/responses/compact",
                objectMapper.readTree("""
                        {
                          "model":"writer-fast",
                          "input":[{"role":"user","content":"summarize"}]
                        }
                        """),
                null
        ).getBody();

        assertEquals("response.compaction", response.path("object").asText());
        assertEquals("compaction", response.path("output").path(0).path("type").asText());
        assertEquals("https://api.openai.com/v1/responses/compact", upstreamUrl.get());
        assertEquals("Bearer upstream-key", upstreamAuthorization.get());
        assertTrue(upstreamBody.get().contains("\"model\":\"gpt-4.1\""));
        assertTrue(upstreamBody.get().contains("\"role\":\"user\""));
        verify(routeSelectionService).recordSuccessfulSelection(selectionResult);
        verify(governanceService).releaseConcurrency(selectionResult.governanceReservationKey());
    }

    @Test
    void shouldRejectOpenAiDirectPassthroughWhenRouteSelectsCompatibleProviderWithoutInvalidatingRoute() throws Exception {
        GatewayRouteSelectionService routeSelectionService = Mockito.mock(GatewayRouteSelectionService.class);
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        GatewayObservabilityService observabilityService = Mockito.mock(GatewayObservabilityService.class);
        DistributedKeyGovernanceService governanceService = Mockito.mock(DistributedKeyGovernanceService.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        RouteSelectionResult selectionResult = selectionResult(ProviderType.OPENAI_COMPATIBLE, UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC);
        UpstreamCredentialEntity credential = credential(101L, ProviderType.OPENAI_COMPATIBLE, "https://compatible.example");

        when(observabilityService.nextRequestId()).thenReturn("req-input-tokens-2");
        when(routeSelectionService.select(any())).thenReturn(selectionResult);
        when(credentialRepository.findById(101L)).thenReturn(Optional.of(credential));
        when(credentialMaterialResolver.resolve(selectionResult, credential))
                .thenReturn(new ResolvedCredentialMaterial(101L, 1L, CredentialAuthKind.API_KEY, "upstream-key", null, Map.of(), null, "credential"));

        GatewayOpenAiPassthroughService service = new GatewayOpenAiPassthroughService(
                routeSelectionService,
                credentialRepository,
                Mockito.mock(CredentialCryptoService.class),
                observabilityService,
                governanceService,
                Mockito.mock(AccountSelectionService.class),
                credentialMaterialResolver,
                Mockito.mock(ErrorRuleService.class),
                WebClient.builder()
        );

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.executeOpenAiDirectJson(
                "sk-gw-test",
                "/v1/responses/input_tokens",
                new ObjectMapper().readTree("""
                        {"model":"writer-fast","input":"hello"}
                        """),
                null
        ));

        assertEquals("当前路由不是 OpenAI Direct native passthrough。", error.getMessage());
        verify(routeSelectionService, never()).invalidateSelection(selectionResult);
        verify(governanceService).releaseConcurrency(selectionResult.governanceReservationKey());
    }

    private RouteSelectionResult selectionResult(ProviderType providerType, UpstreamSiteKind siteKind) {
        CatalogCandidateView candidate = new CatalogCandidateView(
                101L,
                "candidate",
                providerType,
                1L,
                ProviderFamily.OPENAI,
                siteKind,
                AuthStrategy.BEARER,
                PathStrategy.OPENAI_V1,
                ErrorSchemaStrategy.OPENAI_ERROR,
                "https://api.openai.com",
                "gpt-4.1",
                "gpt-4.1",
                List.of("openai"),
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                ReasoningTransport.RESPONSES,
                InteropCapabilityLevel.NATIVE
        );
        RouteCandidateView routeCandidate = new RouteCandidateView(candidate, 11L, 10, 100);
        return new RouteSelectionResult(
                1L,
                "sk-gw-test",
                "writer-fast",
                "writer-fast",
                "gpt-4.1",
                "openai",
                "prefix-hash",
                "fingerprint",
                "gpt-4.1",
                RouteSelectionSource.PREFIX_AFFINITY,
                routeCandidate,
                List.of(routeCandidate)
        );
    }

    private UpstreamCredentialEntity credential(Long id, ProviderType providerType, String baseUrl) {
        UpstreamCredentialEntity credential = new UpstreamCredentialEntity();
        org.springframework.test.util.ReflectionTestUtils.setField(credential, "id", id);
        credential.setProviderType(providerType);
        credential.setBaseUrl(baseUrl);
        credential.setApiKeyCiphertext("cipher");
        credential.setActive(true);
        return credential;
    }

    private String extractBody(ClientRequest request) {
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
