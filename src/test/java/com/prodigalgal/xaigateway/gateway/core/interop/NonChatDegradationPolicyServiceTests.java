package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NonChatDegradationPolicyServiceTests {

    private final NonChatDegradationPolicyService service = new NonChatDegradationPolicyService();

    @Test
    void shouldBlockBeforeExecutionWhenRenderCapabilityIsUnsupported() {
        GatewayRequestSemantics semantics = new GatewayRequestSemantics(
                TranslationResourceType.FILE,
                TranslationOperation.FILE_GET,
                List.of(InteropFeature.FILE_OBJECT),
                RouteSelectionMode.STORED_LINEAGE
        );
        NonChatRoutePolicyDecision policyDecision = new NonChatRoutePolicyDecision(
                RouteSelectionMode.STORED_LINEAGE,
                ExecutionBackend.ORCHESTRATION,
                List.of(ExecutionBackend.ORCHESTRATION),
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.UNSUPPORTED,
                InteropCapabilityLevel.UNSUPPORTED,
                SupportStatus.BLOCKED,
                "gateway-object-lineage",
                List.of(),
                List.of(),
                "selection_mode=stored_lineage"
        );

        NonChatDegradationOutcome outcome = service.evaluate(
                semantics,
                GatewayDegradationPolicy.ALLOW_LOSSY,
                policyDecision
        );

        assertEquals(SupportStatus.BLOCKED, outcome.supportStatus());
        assertEquals(InteropCapabilityLevel.UNSUPPORTED, outcome.degradationLevel());
        assertEquals("render_capability=unsupported", outcome.renderPolicyReason());
        assertEquals("fallback=blocked_before_execution(render_unsupported)", outcome.fallbackPolicyReason());
        assertTrue(outcome.blockedReasons().stream().anyMatch(reason -> reason.contains("render shape")));
    }

    @Test
    void shouldDisableFallbackForStoredLineageEvenWhenCapabilityIsNative() {
        GatewayRequestSemantics semantics = new GatewayRequestSemantics(
                TranslationResourceType.UPLOAD,
                TranslationOperation.UPLOAD_GET,
                List.of(InteropFeature.UPLOAD_CREATE),
                RouteSelectionMode.STORED_LINEAGE
        );
        NonChatRoutePolicyDecision policyDecision = new NonChatRoutePolicyDecision(
                RouteSelectionMode.STORED_LINEAGE,
                ExecutionBackend.ORCHESTRATION,
                List.of(ExecutionBackend.ORCHESTRATION),
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                SupportStatus.NATIVE,
                "gateway-object-lineage",
                List.of(),
                List.of(),
                "selection_mode=stored_lineage"
        );

        NonChatDegradationOutcome outcome = service.evaluate(
                semantics,
                GatewayDegradationPolicy.ALLOW_LOSSY,
                policyDecision
        );

        assertEquals(SupportStatus.ORCHESTRATION, outcome.supportStatus());
        assertEquals(InteropCapabilityLevel.NATIVE, outcome.degradationLevel());
        assertEquals("render_capability=native", outcome.renderPolicyReason());
        assertEquals("fallback=disabled(selection_mode=stored_lineage)", outcome.fallbackPolicyReason());
        assertTrue(outcome.blockedReasons().isEmpty());
    }
}
