package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
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
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
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

    @Test
    void shouldReturnRoutePreview() {
        Mockito.when(gatewayRouteSelectionService.select(Mockito.any()))
                .thenReturn(selectionResult());

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
                .jsonPath("$.distributedKeyId").isEqualTo(1)
                .jsonPath("$.selectionSource").isEqualTo("PREFIX_AFFINITY")
                .jsonPath("$.selectedCandidate.candidate.credentialId").isEqualTo(101);
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
}
