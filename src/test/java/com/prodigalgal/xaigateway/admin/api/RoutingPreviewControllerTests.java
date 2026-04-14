package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlanCompilation;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompiler;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = RoutingPreviewController.class)
@Import(PermitAllSecurityTestConfig.class)
class RoutingPreviewControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private GatewayRouteSelectionService gatewayRouteSelectionService;

    @MockitoBean
    private TranslationExecutionPlanCompiler translationExecutionPlanCompiler;

    @Test
    void shouldReturnRoutePreview() {
        Mockito.when(gatewayRouteSelectionService.select(Mockito.any()))
                .thenReturn(selectionResult());
        Mockito.when(translationExecutionPlanCompiler.compilePreview(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(compilation());

        webTestClient.post()
                .uri("/admin/routing/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "distributedKeyPrefix": "sk-gw-test",
                          "protocol": "openai",
                          "requestPath": "/v1/chat/completions",
                          "requestedModel": "gpt-4o",
                          "requestBody": {"messages":[{"role":"user","content":"hello"}]}
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.selection.distributedKeyId").isEqualTo(1)
                .jsonPath("$.selection.selectionSource").isEqualTo("PREFIX_AFFINITY")
                .jsonPath("$.selection.selectedCandidate.candidate.credentialId").isEqualTo(101)
                .jsonPath("$.requestedSemantics.resourceType").isEqualTo("CHAT")
                .jsonPath("$.plan.ingressProtocol").isEqualTo("OPENAI");
    }

    private RouteSelectionResult selectionResult() {
        CatalogCandidateView candidate = new CatalogCandidateView(
                101L,
                "openai-primary",
                ProviderType.OPENAI_DIRECT,
                "https://api.openai.com",
                "gpt-4o",
                "gpt-4o",
                List.of("openai"),
                true,
                false,
                true,
                true,
                true,
                true,
                ReasoningTransport.OPENAI_CHAT
        );
        RouteCandidateView routeCandidateView = new RouteCandidateView(candidate, 11L, 10, 100);
        return new RouteSelectionResult(
                1L,
                "sk-gw-test",
                "gpt-4o",
                "gpt-4o",
                "gpt-4o",
                "openai",
                "prefix-hash",
                "fingerprint",
                "gpt-4o",
                RouteSelectionSource.PREFIX_AFFINITY,
                routeCandidateView,
                List.of(routeCandidateView)
        );
    }

    private CanonicalExecutionPlanCompilation compilation() {
        return new CanonicalExecutionPlanCompilation(
                new CanonicalExecutionPlan(
                        true,
                        CanonicalIngressProtocol.OPENAI,
                        "/v1/chat/completions",
                        "gpt-4o",
                        "gpt-4o",
                        "gpt-4o",
                        TranslationResourceType.CHAT,
                        TranslationOperation.CHAT_COMPLETION,
                        com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind.NATIVE,
                        InteropCapabilityLevel.NATIVE,
                        InteropCapabilityLevel.NATIVE,
                        InteropCapabilityLevel.NATIVE,
                        List.of(InteropFeature.CHAT_TEXT),
                        java.util.Map.of("chat_text", InteropCapabilityLevel.NATIVE),
                        List.of(),
                        List.of()
                ),
                selectionResult(),
                new GatewayRequestSemantics(TranslationResourceType.CHAT, TranslationOperation.CHAT_COMPLETION, List.of(InteropFeature.CHAT_TEXT), true),
                new CanonicalRequest("sk-gw-test", CanonicalIngressProtocol.OPENAI, "/v1/chat/completions", "gpt-4o", List.of(), List.of(), null, null, null, null, null)
        );
    }
}
