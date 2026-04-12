package com.prodigalgal.xaigateway.gateway.core.interop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.protocol.ingress.interop.InteropPlanRequest;
import com.prodigalgal.xaigateway.protocol.ingress.interop.InteropPlanResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayInteropPlanServiceTests {

    private final GatewayRouteSelectionService gatewayRouteSelectionService = Mockito.mock(GatewayRouteSelectionService.class);
    private final SiteCapabilityTruthService siteCapabilityTruthService = Mockito.mock(SiteCapabilityTruthService.class);
    private final GatewayRequestFeatureService gatewayRequestFeatureService = Mockito.mock(GatewayRequestFeatureService.class);
    private final GatewayInteropPlanService gatewayInteropPlanService =
            new GatewayInteropPlanService(gatewayRouteSelectionService, new ObjectMapper(), null, siteCapabilityTruthService, gatewayRequestFeatureService);

    @Test
    void shouldBlockWhenDegradationPolicyDoesNotAllowSelectedCapability() {
        Mockito.when(gatewayRouteSelectionService.select(Mockito.any()))
                .thenReturn(selectionResult(ProviderType.ANTHROPIC_DIRECT));

        ObjectNode body = new ObjectMapper().createObjectNode();
        body.put("model", "claude-3-7-sonnet");
        InteropPlanRequest request = new InteropPlanRequest(
                "openai",
                "/v1/responses",
                null,
                "strict",
                body
        );

        InteropPlanResponse response = gatewayInteropPlanService.preview("sk-gw-test", request);

        assertFalse(response.executable());
        assertTrue(response.blockers().stream().anyMatch(item -> item.contains("response_object")));
    }

    @Test
    void shouldAllowNativeFeatureForOpenAiAudioTranscription() {
        Mockito.when(gatewayRouteSelectionService.select(Mockito.any()))
                .thenReturn(selectionResult(ProviderType.OPENAI_DIRECT));

        ObjectNode body = new ObjectMapper().createObjectNode();
        body.put("model", "gpt-4o-mini-transcribe");
        InteropPlanRequest request = new InteropPlanRequest(
                "openai",
                "/v1/audio/transcriptions",
                null,
                "strict",
                body
        );

        InteropPlanResponse response = gatewayInteropPlanService.preview("sk-gw-test", request);

        assertTrue(response.executable());
        assertTrue(response.blockers().isEmpty());
    }

    private RouteSelectionResult selectionResult(ProviderType providerType) {
        CatalogCandidateView candidate = new CatalogCandidateView(
                101L,
                "candidate",
                providerType,
                "https://example.com",
                "model-a",
                "model-a",
                List.of("openai", "responses"),
                true,
                true,
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

    GatewayInteropPlanServiceTests() {
        Mockito.when(gatewayRequestFeatureService.detectRequiredFeatures(Mockito.anyString(), Mockito.any()))
                .thenAnswer(invocation -> {
                    String path = invocation.getArgument(0);
                    if ("/v1/responses".equals(path)) {
                        return List.of(InteropFeature.RESPONSE_OBJECT);
                    }
                    if ("/v1/audio/transcriptions".equals(path)) {
                        return List.of(InteropFeature.AUDIO_TRANSCRIPTION);
                    }
                    return List.of(InteropFeature.CHAT_TEXT);
                });
        Mockito.when(siteCapabilityTruthService.capabilityLevel(Mockito.any(), Mockito.eq(InteropFeature.RESPONSE_OBJECT)))
                .thenReturn(InteropCapabilityLevel.EMULATED);
        Mockito.when(siteCapabilityTruthService.capabilityLevel(Mockito.any(), Mockito.eq(InteropFeature.AUDIO_TRANSCRIPTION)))
                .thenReturn(InteropCapabilityLevel.NATIVE);
        Mockito.when(siteCapabilityTruthService.capabilityLevel(Mockito.any(), Mockito.eq(InteropFeature.CHAT_TEXT)))
                .thenReturn(InteropCapabilityLevel.NATIVE);
    }
}
