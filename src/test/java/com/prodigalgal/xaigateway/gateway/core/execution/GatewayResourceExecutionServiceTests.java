package com.prodigalgal.xaigateway.gateway.core.execution;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyGovernanceService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlanCompilation;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureService;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompiler;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayObservabilityService;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestLifecycleService;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

class GatewayResourceExecutionServiceTests {

    @Test
    void shouldDispatchJsonExecutionToMatchingExecutor() {
        GatewayRouteSelectionService gatewayRouteSelectionService = Mockito.mock(GatewayRouteSelectionService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        DistributedKeyGovernanceService distributedKeyGovernanceService = Mockito.mock(DistributedKeyGovernanceService.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        AccountSelectionService accountSelectionService = Mockito.mock(AccountSelectionService.class);
        GatewayRequestFeatureService gatewayRequestFeatureService = Mockito.mock(GatewayRequestFeatureService.class);
        TranslationExecutionPlanCompiler translationExecutionPlanCompiler = Mockito.mock(TranslationExecutionPlanCompiler.class);
        GatewayResourceExecutor embeddingsExecutor = Mockito.mock(GatewayResourceExecutor.class);
        GatewayResourceExecutor fallbackExecutor = Mockito.mock(GatewayResourceExecutor.class);
        GatewayFileService gatewayFileService = Mockito.mock(GatewayFileService.class);

        GatewayResourceExecutionService service = service(
                gatewayRouteSelectionService,
                upstreamCredentialRepository,
                credentialCryptoService,
                distributedKeyGovernanceService,
                distributedKeyQueryService,
                accountSelectionService,
                gatewayRequestFeatureService,
                translationExecutionPlanCompiler,
                List.of(embeddingsExecutor, fallbackExecutor),
                Mockito.mock(GatewayObservabilityService.class),
                Mockito.mock(GatewayRequestLifecycleService.class),
                gatewayFileService
        );

        RouteSelectionResult selectionResult = selectionResult(ProviderType.GEMINI_DIRECT, UpstreamSiteKind.GEMINI_DIRECT);
        UpstreamCredentialEntity credential = credential(selectionResult.selectedCandidate().candidate().credentialId(), ProviderType.GEMINI_DIRECT);
        ObjectNode requestBody = new ObjectMapper().createObjectNode();
        requestBody.put("model", "text-embedding-004");
        requestBody.put("input", "hello");

        Mockito.when(gatewayRouteSelectionService.select(any())).thenReturn(selectionResult);
        Mockito.when(upstreamCredentialRepository.findById(101L)).thenReturn(Optional.of(credential));
        Mockito.when(credentialCryptoService.decrypt("cipher")).thenReturn("api-key");
        Mockito.when(distributedKeyQueryService.findActiveByKeyPrefix("sk-gw-test"))
                .thenReturn(Optional.of(new DistributedKeyView(1L, "test", "sk-gw-test", "masked", List.of(), List.of(), List.of())));
        Mockito.when(accountSelectionService.resolveActiveAccount(anyLong(), any(), any(), anyInt())).thenReturn(Optional.empty());
        Mockito.when(gatewayRequestFeatureService.describe(eq("POST"), eq("/v1/embeddings"), any()))
                .thenReturn(new GatewayRequestSemantics(
                        com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType.EMBEDDING,
                        com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation.EMBEDDING_CREATE,
                        List.of(com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature.EMBEDDINGS),
                        true
                ));
        Mockito.when(translationExecutionPlanCompiler.compileSelected(any(), Mockito.any(com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest.class), any(), any()))
                .thenReturn(compilation("/v1/embeddings", "text-embedding-004"));
        Mockito.when(embeddingsExecutor.supports(Mockito.any(com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest.class), any())).thenReturn(true);
        Mockito.when(embeddingsExecutor.executeJson(any(), any(), eq("text-embedding-004")))
                .thenReturn(ResponseEntity.ok(new ObjectMapper().createObjectNode().put("object", "list")));

        ResponseEntity<tools.jackson.databind.JsonNode> response = service.executeJson(
                "sk-gw-test",
                "/v1/embeddings",
                requestBody,
                "text-embedding-004"
        );

        assertEquals(200, response.getStatusCode().value());
        Mockito.verify(embeddingsExecutor).executeJson(any(), any(), eq("text-embedding-004"));
    }

    @Test
    void shouldFailWhenNoExecutorMatchesSelectedCandidate() {
        GatewayRouteSelectionService gatewayRouteSelectionService = Mockito.mock(GatewayRouteSelectionService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        DistributedKeyGovernanceService distributedKeyGovernanceService = Mockito.mock(DistributedKeyGovernanceService.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        AccountSelectionService accountSelectionService = Mockito.mock(AccountSelectionService.class);
        GatewayRequestFeatureService gatewayRequestFeatureService = Mockito.mock(GatewayRequestFeatureService.class);
        TranslationExecutionPlanCompiler translationExecutionPlanCompiler = Mockito.mock(TranslationExecutionPlanCompiler.class);
        GatewayResourceExecutor unsupportedExecutor = Mockito.mock(GatewayResourceExecutor.class);
        GatewayFileService gatewayFileService = Mockito.mock(GatewayFileService.class);

        GatewayResourceExecutionService service = service(
                gatewayRouteSelectionService,
                upstreamCredentialRepository,
                credentialCryptoService,
                distributedKeyGovernanceService,
                distributedKeyQueryService,
                accountSelectionService,
                gatewayRequestFeatureService,
                translationExecutionPlanCompiler,
                List.of(unsupportedExecutor),
                Mockito.mock(GatewayObservabilityService.class),
                Mockito.mock(GatewayRequestLifecycleService.class),
                gatewayFileService
        );

        RouteSelectionResult selectionResult = selectionResult(ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT);
        UpstreamCredentialEntity credential = credential(selectionResult.selectedCandidate().candidate().credentialId(), ProviderType.OPENAI_DIRECT);
        ObjectNode requestBody = new ObjectMapper().createObjectNode();
        requestBody.put("model", "omni-moderation-latest");

        Mockito.when(gatewayRouteSelectionService.select(any())).thenReturn(selectionResult);
        Mockito.when(upstreamCredentialRepository.findById(101L)).thenReturn(Optional.of(credential));
        Mockito.when(credentialCryptoService.decrypt("cipher")).thenReturn("api-key");
        Mockito.when(distributedKeyQueryService.findActiveByKeyPrefix("sk-gw-test"))
                .thenReturn(Optional.of(new DistributedKeyView(1L, "test", "sk-gw-test", "masked", List.of(), List.of(), List.of())));
        Mockito.when(accountSelectionService.resolveActiveAccount(anyLong(), any(), any(), anyInt())).thenReturn(Optional.empty());
        Mockito.when(gatewayRequestFeatureService.describe(eq("POST"), eq("/v1/moderations"), any()))
                .thenReturn(new GatewayRequestSemantics(
                        com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType.MODERATION,
                        com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation.MODERATION_CREATE,
                        List.of(com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature.MODERATION),
                        true
                ));
        Mockito.when(translationExecutionPlanCompiler.compileSelected(any(), Mockito.any(com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest.class), any(), any()))
                .thenReturn(compilation("/v1/moderations", "omni-moderation-latest"));
        Mockito.when(unsupportedExecutor.supports(Mockito.any(com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest.class), any())).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.executeJson(
                "sk-gw-test",
                "/v1/moderations",
                requestBody,
                "omni-moderation-latest"
        ));
    }

    @Test
    void shouldFallbackToNextResourceCandidateBeforeResponseIsCommitted() {
        GatewayRouteSelectionService gatewayRouteSelectionService = Mockito.mock(GatewayRouteSelectionService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        DistributedKeyGovernanceService distributedKeyGovernanceService = Mockito.mock(DistributedKeyGovernanceService.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        AccountSelectionService accountSelectionService = Mockito.mock(AccountSelectionService.class);
        GatewayRequestFeatureService gatewayRequestFeatureService = Mockito.mock(GatewayRequestFeatureService.class);
        TranslationExecutionPlanCompiler translationExecutionPlanCompiler = Mockito.mock(TranslationExecutionPlanCompiler.class);
        GatewayResourceExecutor embeddingsExecutor = Mockito.mock(GatewayResourceExecutor.class);
        GatewayObservabilityService gatewayObservabilityService = Mockito.mock(GatewayObservabilityService.class);
        GatewayResourceExecutionService service = service(
                gatewayRouteSelectionService,
                upstreamCredentialRepository,
                credentialCryptoService,
                distributedKeyGovernanceService,
                distributedKeyQueryService,
                accountSelectionService,
                gatewayRequestFeatureService,
                translationExecutionPlanCompiler,
                List.of(embeddingsExecutor),
                gatewayObservabilityService,
                Mockito.mock(GatewayRequestLifecycleService.class),
                Mockito.mock(GatewayFileService.class)
        );

        RouteSelectionResult selectionResult = selectionResultWithFallbackCandidates();
        UpstreamCredentialEntity firstCredential = credential(101L, ProviderType.GEMINI_DIRECT);
        UpstreamCredentialEntity secondCredential = credential(202L, ProviderType.GEMINI_DIRECT);
        ObjectNode requestBody = new ObjectMapper().createObjectNode();
        requestBody.put("model", "text-embedding-004");
        requestBody.put("input", "hello");

        Mockito.when(gatewayObservabilityService.nextRequestId()).thenReturn("req-resource-1");
        Mockito.when(gatewayRouteSelectionService.select(any())).thenReturn(selectionResult);
        Mockito.when(upstreamCredentialRepository.findById(101L)).thenReturn(Optional.of(firstCredential));
        Mockito.when(upstreamCredentialRepository.findById(202L)).thenReturn(Optional.of(secondCredential));
        Mockito.when(credentialCryptoService.decrypt("cipher")).thenReturn("api-key");
        Mockito.when(distributedKeyQueryService.findActiveByKeyPrefix("sk-gw-test"))
                .thenReturn(Optional.of(new DistributedKeyView(1L, "test", "sk-gw-test", "masked", List.of(), List.of(), List.of())));
        Mockito.when(accountSelectionService.resolveActiveAccount(anyLong(), any(), any(), anyInt())).thenReturn(Optional.empty());
        Mockito.when(gatewayRequestFeatureService.describe(eq("POST"), eq("/v1/embeddings"), any()))
                .thenReturn(new GatewayRequestSemantics(
                        com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType.EMBEDDING,
                        com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation.EMBEDDING_CREATE,
                        List.of(com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature.EMBEDDINGS),
                        true
                ));
        Mockito.when(translationExecutionPlanCompiler.compileSelected(any(), Mockito.any(com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest.class), any(), any()))
                .thenReturn(compilation("/v1/embeddings", "text-embedding-004"));
        Mockito.when(embeddingsExecutor.supports(Mockito.any(com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest.class), any())).thenReturn(true);
        Mockito.when(embeddingsExecutor.executeJson(any(), any(), eq("text-embedding-004")))
                .thenReturn(ResponseEntity.status(503).body(null))
                .thenReturn(ResponseEntity.ok(new ObjectMapper().createObjectNode().put("object", "list")));

        ResponseEntity<tools.jackson.databind.JsonNode> response = service.executeJson(
                "sk-gw-test",
                "/v1/embeddings",
                requestBody,
                "text-embedding-004"
        );

        assertEquals(200, response.getStatusCode().value());
        Mockito.verify(gatewayRouteSelectionService).markCredentialCooldown(eq(101L), eq("status=503"));
        Mockito.verify(embeddingsExecutor, Mockito.times(2)).executeJson(any(), any(), eq("text-embedding-004"));
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
                "https://example.com",
                "model-a",
                "model-a",
                List.of("openai"),
                true,
                true,
                true,
                true,
                true,
                true,
                ReasoningTransport.OPENAI_CHAT,
                com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel.NATIVE
        );
        RouteCandidateView routeCandidateView = new RouteCandidateView(candidate, 11L, 10, 100);
        return new RouteSelectionResult(
                1L,
                "sk-gw-test",
                "model-a",
                "model-a",
                "model-a",
                "openai",
                "prefix",
                "fingerprint",
                "model-a",
                RouteSelectionSource.WEIGHTED_HASH,
                routeCandidateView,
                List.of(routeCandidateView)
        );
    }

    private RouteSelectionResult selectionResultWithFallbackCandidates() {
        CatalogCandidateView first = new CatalogCandidateView(
                101L,
                "candidate-a",
                ProviderType.GEMINI_DIRECT,
                1L,
                ProviderFamily.GEMINI,
                UpstreamSiteKind.GEMINI_DIRECT,
                AuthStrategy.BEARER,
                PathStrategy.OPENAI_V1,
                ErrorSchemaStrategy.OPENAI_ERROR,
                "https://example.com",
                "model-a",
                "model-a",
                List.of("openai"),
                true,
                true,
                true,
                true,
                true,
                true,
                ReasoningTransport.OPENAI_CHAT,
                com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel.NATIVE
        );
        CatalogCandidateView second = new CatalogCandidateView(
                202L,
                "candidate-b",
                ProviderType.GEMINI_DIRECT,
                1L,
                ProviderFamily.GEMINI,
                UpstreamSiteKind.GEMINI_DIRECT,
                AuthStrategy.BEARER,
                PathStrategy.OPENAI_V1,
                ErrorSchemaStrategy.OPENAI_ERROR,
                "https://example.com",
                "model-a",
                "model-a",
                List.of("openai"),
                true,
                true,
                true,
                true,
                true,
                true,
                ReasoningTransport.OPENAI_CHAT,
                com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel.NATIVE
        );
        RouteCandidateView firstView = new RouteCandidateView(first, 11L, 10, 100, "NATIVE", 3);
        RouteCandidateView secondView = new RouteCandidateView(second, 12L, 10, 90, "NATIVE", 3);
        return new RouteSelectionResult(
                1L,
                "sk-gw-test",
                "model-a",
                "model-a",
                "model-a",
                "openai",
                "prefix",
                "fingerprint",
                "model-a",
                com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily.GENERIC_OPENAI,
                List.of(),
                null,
                RouteSelectionSource.WEIGHTED_HASH,
                firstView,
                List.of(firstView, secondView),
                List.of(
                        new com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateEvaluation(firstView, true, "HEALTHY", null, false, RouteSelectionSource.WEIGHTED_HASH, 100d, List.of(), List.of()),
                        new com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateEvaluation(secondView, true, "HEALTHY", null, false, RouteSelectionSource.WEIGHTED_HASH, 90d, List.of(), List.of())
                ),
                List.of()
        );
    }

    private UpstreamCredentialEntity credential(Long id, ProviderType providerType) {
        UpstreamCredentialEntity entity = new UpstreamCredentialEntity();
        org.springframework.test.util.ReflectionTestUtils.setField(entity, "id", id);
        entity.setProviderType(providerType);
        entity.setBaseUrl("https://example.com");
        entity.setApiKeyCiphertext("cipher");
        return entity;
    }

    private CanonicalExecutionPlanCompilation compilation(String requestPath, String model) {
        return new CanonicalExecutionPlanCompilation(
                new CanonicalExecutionPlan(
                        true,
                        CanonicalIngressProtocol.OPENAI,
                        requestPath,
                        model,
                        model,
                        model,
                        com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType.UNKNOWN,
                        com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation.UNKNOWN,
                        com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind.NATIVE,
                        com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel.NATIVE,
                        com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel.NATIVE,
                        com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel.NATIVE,
                        List.of(),
                        java.util.Map.of(),
                        List.of(),
                        List.of()
                ),
                null,
                new GatewayRequestSemantics(
                        com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType.UNKNOWN,
                        com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation.UNKNOWN,
                        List.of(),
                        true
                ),
                new CanonicalRequest("sk-gw-test", CanonicalIngressProtocol.OPENAI, requestPath, model, List.of(), List.of(), null, null, null, null, null)
        );
    }

    private GatewayResourceExecutionService service(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialCryptoService credentialCryptoService,
            DistributedKeyGovernanceService distributedKeyGovernanceService,
            DistributedKeyQueryService distributedKeyQueryService,
            AccountSelectionService accountSelectionService,
            GatewayRequestFeatureService gatewayRequestFeatureService,
            TranslationExecutionPlanCompiler translationExecutionPlanCompiler,
            List<GatewayResourceExecutor> gatewayResourceExecutors,
            GatewayObservabilityService gatewayObservabilityService,
            GatewayRequestLifecycleService gatewayRequestLifecycleService,
            GatewayFileService gatewayFileService) {
        return new GatewayResourceExecutionService(
                gatewayRouteSelectionService,
                upstreamCredentialRepository,
                credentialCryptoService,
                distributedKeyGovernanceService,
                distributedKeyQueryService,
                accountSelectionService,
                gatewayRequestFeatureService,
                translationExecutionPlanCompiler,
                gatewayResourceExecutors,
                gatewayObservabilityService,
                gatewayRequestLifecycleService,
                gatewayFileService,
                new ObjectMapper(),
                new com.prodigalgal.xaigateway.infra.config.GatewayProperties()
        );
    }
}
