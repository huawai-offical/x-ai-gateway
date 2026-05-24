package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendPolicyService;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationExecutionPlanCompilerLosslessMatrixTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldBlockProviderFileIdWhenOpenAiRequestWouldTranslateToAnthropic() {
        TranslationExecutionPlanCompiler compiler = compilerFor(anthropicCandidate());
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", "claude-sonnet-4");
        body.putArray("messages")
                .addObject()
                .put("role", "user")
                .putArray("content")
                .addObject()
                .put("type", "input_file")
                .putObject("input_file")
                .put("file_id", "file_123");

        var compilation = compiler.compilePreview(
                "sk-gw-test",
                "openai",
                "POST",
                "/v1/chat/completions",
                "claude-sonnet-4",
                GatewayDegradationPolicy.ALLOW_LOSSY,
                GatewayClientFamily.GENERIC_OPENAI,
                body
        );

        assertEquals(ExecutionKind.BLOCKED, compilation.canonicalPlan().executionKind());
        assertEquals(SupportStatus.BLOCKED, compilation.canonicalPlan().supportStatus());
        assertEquals(InteropCapabilityLevel.UNSUPPORTED, compilation.canonicalPlan().degradationLevel());
        assertTrue(compilation.canonicalPlan().blockerReasons().stream()
                .anyMatch(reason -> reason.contains("content.file.provider_file_id")
                        && reason.contains("failure_code=native_route_required")));
    }

    @Test
    void shouldAllowLosslessTextWhenOpenAiRequestTranslatesToAnthropic() {
        TranslationExecutionPlanCompiler compiler = compilerFor(anthropicCandidate());
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", "claude-sonnet-4");
        body.putArray("messages")
                .addObject()
                .put("role", "user")
                .put("content", "hello");

        var compilation = compiler.compilePreview(
                "sk-gw-test",
                "openai",
                "POST",
                "/v1/chat/completions",
                "claude-sonnet-4",
                GatewayDegradationPolicy.ALLOW_LOSSY,
                GatewayClientFamily.GENERIC_OPENAI,
                body
        );

        assertEquals(ExecutionKind.NATIVE, compilation.canonicalPlan().executionKind());
        assertEquals(SupportStatus.NATIVE, compilation.canonicalPlan().supportStatus());
        assertTrue(compilation.canonicalPlan().blockerReasons().isEmpty());
    }

    private TranslationExecutionPlanCompiler compilerFor(CatalogCandidateView candidate) {
        RouteCandidateView selectedCandidate = new RouteCandidateView(candidate, 1L, 1, 100);
        RouteSelectionResult selectionResult = new RouteSelectionResult(
                1L,
                "sk-gw-test",
                candidate.modelName(),
                candidate.modelName(),
                candidate.modelKey(),
                "openai",
                "prefix",
                "fingerprint",
                "default",
                GatewayClientFamily.GENERIC_OPENAI,
                List.of(),
                null,
                RouteSelectionSource.MODEL_AFFINITY,
                selectedCandidate,
                List.of(selectedCandidate)
        );
        GatewayRouteSelectionService routeSelectionService = Mockito.mock(GatewayRouteSelectionService.class);
        Mockito.when(routeSelectionService.select(Mockito.any())).thenReturn(selectionResult);
        SiteCapabilityTruthService truthService = Mockito.mock(SiteCapabilityTruthService.class);
        Mockito.when(truthService.resolve(Mockito.eq(candidate), Mockito.any())).thenReturn(nativeReport());

        return new TranslationExecutionPlanCompiler(
                routeSelectionService,
                new GatewayRequestFeatureService(),
                truthService,
                NonChatRoutePolicyService.forTests(truthService, new ExecutionBackendPolicyService()),
                NonChatTargetResolutionService.createDefault(),
                new NonChatDegradationPolicyService(),
                new LosslessTranslationMatrixService()
        );
    }

    private CapabilityResolutionReport nativeReport() {
        return new CapabilityResolutionReport(
                Map.of(
                        InteropFeature.CHAT_TEXT.wireName(),
                        new CapabilityResolution(
                                InteropFeature.CHAT_TEXT,
                                InteropCapabilityLevel.NATIVE,
                                InteropCapabilityLevel.NATIVE,
                                InteropCapabilityLevel.NATIVE,
                                InteropCapabilityLevel.NATIVE,
                                List.of(),
                                List.of()
                        )
                ),
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                ExecutionKind.NATIVE,
                "direct_upstream_execution",
                List.of(),
                List.of()
        );
    }

    private CatalogCandidateView anthropicCandidate() {
        return new CatalogCandidateView(
                10L,
                "anthropic",
                ProviderType.ANTHROPIC_DIRECT,
                20L,
                ProviderFamily.ANTHROPIC,
                UpstreamSiteKind.ANTHROPIC_DIRECT,
                AuthStrategy.BEARER,
                PathStrategy.ANTHROPIC_V1_MESSAGES,
                ErrorSchemaStrategy.ANTHROPIC_ERROR,
                "https://api.anthropic.com",
                "claude-sonnet-4",
                "claude-sonnet-4",
                List.of("anthropic_native"),
                true,
                true,
                true,
                false,
                false,
                true,
                true,
                false,
                ReasoningTransport.ANTHROPIC,
                InteropCapabilityLevel.NATIVE
        );
    }
}
