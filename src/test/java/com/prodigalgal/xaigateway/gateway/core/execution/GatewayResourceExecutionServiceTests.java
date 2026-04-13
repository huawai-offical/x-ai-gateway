package com.prodigalgal.xaigateway.gateway.core.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyGovernanceService;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureService;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompiler;
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
        AccountSelectionService accountSelectionService = Mockito.mock(AccountSelectionService.class);
        GatewayRequestFeatureService gatewayRequestFeatureService = Mockito.mock(GatewayRequestFeatureService.class);
        TranslationExecutionPlanCompiler translationExecutionPlanCompiler = Mockito.mock(TranslationExecutionPlanCompiler.class);
        GatewayResourceExecutor embeddingsExecutor = Mockito.mock(GatewayResourceExecutor.class);
        GatewayResourceExecutor fallbackExecutor = Mockito.mock(GatewayResourceExecutor.class);

        GatewayResourceExecutionService service = new GatewayResourceExecutionService(
                gatewayRouteSelectionService,
                upstreamCredentialRepository,
                credentialCryptoService,
                distributedKeyGovernanceService,
                accountSelectionService,
                gatewayRequestFeatureService,
                translationExecutionPlanCompiler,
                List.of(embeddingsExecutor, fallbackExecutor)
        );

        RouteSelectionResult selectionResult = selectionResult(ProviderType.GEMINI_DIRECT, UpstreamSiteKind.GEMINI_DIRECT);
        UpstreamCredentialEntity credential = credential(selectionResult.selectedCandidate().candidate().credentialId(), ProviderType.GEMINI_DIRECT);
        ObjectNode requestBody = new ObjectMapper().createObjectNode();
        requestBody.put("model", "text-embedding-004");
        requestBody.put("input", "hello");

        Mockito.when(gatewayRouteSelectionService.select(any())).thenReturn(selectionResult);
        Mockito.when(upstreamCredentialRepository.findById(101L)).thenReturn(Optional.of(credential));
        Mockito.when(credentialCryptoService.decrypt("cipher")).thenReturn("api-key");
        Mockito.when(accountSelectionService.resolveActiveAccount(anyLong(), any(), any(), anyInt())).thenReturn(Optional.empty());
        Mockito.when(gatewayRequestFeatureService.describe(eq("/v1/embeddings"), any()))
                .thenReturn(new GatewayRequestSemantics(
                        com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType.EMBEDDING,
                        com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation.EMBEDDING_CREATE,
                        List.of(com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature.EMBEDDINGS),
                        true
                ));
        Mockito.when(translationExecutionPlanCompiler.compileSelected(any(), eq("/v1/embeddings"), any(), any()))
                .thenReturn(Mockito.mock(TranslationExecutionPlan.class));
        Mockito.when(embeddingsExecutor.supports(eq("/v1/embeddings"), any())).thenReturn(true);
        Mockito.when(embeddingsExecutor.executeJson(any(), any(), eq("text-embedding-004")))
                .thenReturn(ResponseEntity.ok(new ObjectMapper().createObjectNode().put("object", "list")));

        ResponseEntity<com.fasterxml.jackson.databind.JsonNode> response = service.executeJson(
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
        AccountSelectionService accountSelectionService = Mockito.mock(AccountSelectionService.class);
        GatewayRequestFeatureService gatewayRequestFeatureService = Mockito.mock(GatewayRequestFeatureService.class);
        TranslationExecutionPlanCompiler translationExecutionPlanCompiler = Mockito.mock(TranslationExecutionPlanCompiler.class);
        GatewayResourceExecutor unsupportedExecutor = Mockito.mock(GatewayResourceExecutor.class);

        GatewayResourceExecutionService service = new GatewayResourceExecutionService(
                gatewayRouteSelectionService,
                upstreamCredentialRepository,
                credentialCryptoService,
                distributedKeyGovernanceService,
                accountSelectionService,
                gatewayRequestFeatureService,
                translationExecutionPlanCompiler,
                List.of(unsupportedExecutor)
        );

        RouteSelectionResult selectionResult = selectionResult(ProviderType.OPENAI_DIRECT, UpstreamSiteKind.OPENAI_DIRECT);
        UpstreamCredentialEntity credential = credential(selectionResult.selectedCandidate().candidate().credentialId(), ProviderType.OPENAI_DIRECT);
        ObjectNode requestBody = new ObjectMapper().createObjectNode();
        requestBody.put("model", "omni-moderation-latest");

        Mockito.when(gatewayRouteSelectionService.select(any())).thenReturn(selectionResult);
        Mockito.when(upstreamCredentialRepository.findById(101L)).thenReturn(Optional.of(credential));
        Mockito.when(credentialCryptoService.decrypt("cipher")).thenReturn("api-key");
        Mockito.when(accountSelectionService.resolveActiveAccount(anyLong(), any(), any(), anyInt())).thenReturn(Optional.empty());
        Mockito.when(gatewayRequestFeatureService.describe(eq("/v1/moderations"), any()))
                .thenReturn(new GatewayRequestSemantics(
                        com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType.MODERATION,
                        com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation.MODERATION_CREATE,
                        List.of(com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature.MODERATION),
                        true
                ));
        Mockito.when(translationExecutionPlanCompiler.compileSelected(any(), eq("/v1/moderations"), any(), any()))
                .thenReturn(Mockito.mock(TranslationExecutionPlan.class));
        Mockito.when(unsupportedExecutor.supports(eq("/v1/moderations"), any())).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.executeJson(
                "sk-gw-test",
                "/v1/moderations",
                requestBody,
                "omni-moderation-latest"
        ));
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

    private UpstreamCredentialEntity credential(Long id, ProviderType providerType) {
        UpstreamCredentialEntity entity = new UpstreamCredentialEntity();
        org.springframework.test.util.ReflectionTestUtils.setField(entity, "id", id);
        entity.setProviderType(providerType);
        entity.setBaseUrl("https://example.com");
        entity.setApiKeyCiphertext("cipher");
        return entity;
    }
}
