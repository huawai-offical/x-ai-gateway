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

    @Test
    void shouldBlockEmulatedAndLossyCapabilitiesEvenWhenLegacyPolicyAllowsThem() {
        GatewayRequestSemantics semantics = new GatewayRequestSemantics(
                TranslationResourceType.RESPONSE,
                TranslationOperation.RESPONSE_CREATE,
                List.of(InteropFeature.RESPONSE_OBJECT),
                RouteSelectionMode.CATALOG_SELECTION
        );
        NonChatRoutePolicyDecision policyDecision = new NonChatRoutePolicyDecision(
                RouteSelectionMode.CATALOG_SELECTION,
                ExecutionBackend.NATIVE,
                List.of(ExecutionBackend.NATIVE),
                InteropCapabilityLevel.EMULATED,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.EMULATED,
                SupportStatus.DEGRADED,
                "translated_execution",
                List.of(),
                List.of("response_object 当前为非 native 兼容展示，只能观测，不能作为执行成功条件。"),
                "catalog_selection"
        );

        NonChatDegradationOutcome outcome = service.evaluate(
                semantics,
                GatewayDegradationPolicy.ALLOW_LOSSY,
                policyDecision
        );

        assertEquals(SupportStatus.BLOCKED, outcome.supportStatus());
        assertEquals(InteropCapabilityLevel.UNSUPPORTED, outcome.degradationLevel());
        assertTrue(outcome.blockedReasons().stream().anyMatch(reason -> reason.contains("不能作为真实执行成功条件")));
        assertTrue(outcome.blockedReasons().stream().anyMatch(reason -> reason.contains("只允许 native 执行")));
    }
}
