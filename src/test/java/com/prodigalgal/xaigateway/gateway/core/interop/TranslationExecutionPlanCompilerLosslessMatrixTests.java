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

    @Test
    void shouldBlockResponsesHostedToolWhenResponsesIngressTargetsOpenAiCompatibleChatSurface() {
        TranslationExecutionPlanCompiler compiler = compilerFor(openAiCompatibleCandidate(UpstreamSiteKind.XIAOMI_MIMO));
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", "mimo-v2.5-pro");
        body.put("input", "find project notes");
        body.putArray("tools")
                .addObject()
                .put("type", "file_search");

        var compilation = compiler.compilePreview(
                "sk-gw-test",
                "responses",
                "POST",
                "/v1/responses",
                "mimo-v2.5-pro",
                GatewayDegradationPolicy.ALLOW_LOSSY,
                GatewayClientFamily.GENERIC_OPENAI,
                body
        );

        assertEquals(ExecutionKind.BLOCKED, compilation.canonicalPlan().executionKind());
        assertEquals(SupportStatus.BLOCKED, compilation.canonicalPlan().supportStatus());
        assertTrue(compilation.canonicalPlan().blockerReasons().stream()
                .anyMatch(reason -> reason.contains("response.hosted_tool.file_search")
                        && reason.contains("failure_code=native_hosted_tool_required")));
    }

    @Test
    void shouldBlockFileLifecycleWhenOpenAiRequestWouldTranslateToAnthropic() {
        TranslationExecutionPlanCompiler compiler = compilerFor(anthropicCandidate());
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", "claude-sonnet-4");
        body.put("purpose", "assistants");

        var compilation = compiler.compilePreview(
                "sk-gw-test",
                "openai",
                "POST",
                "/v1/files",
                "claude-sonnet-4",
                GatewayDegradationPolicy.ALLOW_LOSSY,
                GatewayClientFamily.GENERIC_OPENAI,
                body
        );

        assertEquals(ExecutionKind.BLOCKED, compilation.canonicalPlan().executionKind());
        assertTrue(compilation.canonicalPlan().blockerReasons().stream()
                .anyMatch(reason -> reason.contains("file.object_lifecycle")
                        && reason.contains("failure_code=native_file_lifecycle_required")));
    }

    @Test
    void shouldBlockMediaResourceWhenOpenAiRequestWouldTranslateToGemini() {
        TranslationExecutionPlanCompiler compiler = compilerFor(geminiCandidate());
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", "gemini-2.5-flash-image");
        body.put("prompt", "draw a precise diagram");

        var compilation = compiler.compilePreview(
                "sk-gw-test",
                "openai",
                "POST",
                "/v1/images/edits",
                "gemini-2.5-flash-image",
                GatewayDegradationPolicy.ALLOW_LOSSY,
                GatewayClientFamily.GENERIC_OPENAI,
                body
        );

        assertEquals(ExecutionKind.BLOCKED, compilation.canonicalPlan().executionKind());
        assertTrue(compilation.canonicalPlan().blockerReasons().stream()
                .anyMatch(reason -> reason.contains("image.edit.request")
                        && reason.contains("failure_code=native_image_edit_required")));
    }

    @Test
    void shouldBlockToolStreamingWhenOpenAiRequestWouldTranslateToAnthropic() {
        TranslationExecutionPlanCompiler compiler = compilerFor(anthropicCandidate());
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", "claude-sonnet-4");
        body.put("stream", true);
        body.putArray("messages")
                .addObject()
                .put("role", "user")
                .put("content", "stream a tool call");
        body.putArray("tools")
                .addObject()
                .put("type", "function")
                .putObject("function")
                .put("name", "lookup");

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
        assertTrue(compilation.canonicalPlan().blockerReasons().stream()
                .anyMatch(reason -> reason.contains("stream.tool_call_delta")
                        && reason.contains("failure_code=native_route_required")));
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

    private CatalogCandidateView geminiCandidate() {
        return new CatalogCandidateView(
                11L,
                "gemini",
                ProviderType.GEMINI_DIRECT,
                21L,
                ProviderFamily.GEMINI,
                UpstreamSiteKind.GEMINI_DIRECT,
                AuthStrategy.API_KEY_QUERY,
                PathStrategy.GEMINI_V1BETA_MODELS,
                ErrorSchemaStrategy.GEMINI_ERROR,
                "https://generativelanguage.googleapis.com",
                "gemini-2.5-flash-image",
                "gemini-2.5-flash-image",
                List.of("google_native"),
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                false,
                ReasoningTransport.GEMINI_THOUGHTS,
                InteropCapabilityLevel.NATIVE
        );
    }

    private CatalogCandidateView openAiCompatibleCandidate(UpstreamSiteKind siteKind) {
        return new CatalogCandidateView(
                12L,
                "mimo",
                ProviderType.OPENAI_COMPATIBLE,
                22L,
                ProviderFamily.OPENAI,
                siteKind,
                AuthStrategy.BEARER,
                PathStrategy.OPENAI_V1,
                ErrorSchemaStrategy.OPENAI_ERROR,
                "https://token-plan-sgp.xiaomimimo.com/v1",
                "mimo-v2.5-pro",
                "mimo-v2.5-pro",
                List.of("openai", "responses"),
                true,
                true,
                true,
                false,
                false,
                true,
                true,
                false,
                ReasoningTransport.OPENAI_CHAT,
                InteropCapabilityLevel.NATIVE
        );
    }
}
