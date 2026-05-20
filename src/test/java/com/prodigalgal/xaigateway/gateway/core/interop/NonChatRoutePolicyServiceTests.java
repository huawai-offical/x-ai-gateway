package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendPolicyService;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NonChatRoutePolicyServiceTests {

    private final SiteCapabilityTruthService siteCapabilityTruthService = Mockito.mock(SiteCapabilityTruthService.class);
    private final NonChatRoutePolicyService service = NonChatRoutePolicyService.forTests(
            siteCapabilityTruthService,
            new ExecutionBackendPolicyService()
    );

    @Test
    void shouldKeepOpenAiDirectObjectLifecycleSurfaceUsable() {
        GatewayRequestSemantics semantics = new GatewayRequestSemantics(
                TranslationResourceType.FILE,
                TranslationOperation.FILE_CREATE,
                List.of(InteropFeature.FILE_OBJECT),
                RouteSelectionMode.CATALOG_SELECTION
        );
        CatalogCandidateView candidate = new CatalogCandidateView(
                101L,
                "openai-direct",
                ProviderType.OPENAI_DIRECT,
                "https://api.openai.com",
                "gpt-4o-mini",
                "gpt-4o-mini",
                List.of("openai"),
                true,
                true,
                true,
                true,
                true,
                true,
                ReasoningTransport.OPENAI_CHAT
        );

        Mockito.when(siteCapabilityTruthService.resolve(Mockito.eq(candidate), Mockito.eq(semantics)))
                .thenReturn(report(InteropFeature.FILE_OBJECT, InteropCapabilityLevel.NATIVE, List.of()));

        NonChatRoutePolicyDecision decision = service.evaluateCandidate(
                "openai",
                "/v1/files",
                semantics,
                candidate,
                canonicalRequest("/v1/files", "gpt-4o-mini"),
                null
        );

        assertEquals(RouteSelectionMode.CATALOG_SELECTION, decision.selectionMode());
        assertEquals(ExecutionBackend.ORCHESTRATION, decision.preferredBackend());
        assertEquals(SupportStatus.ORCHESTRATION, decision.supportStatus());
        assertEquals(InteropCapabilityLevel.NATIVE, decision.overallCapabilityLevel());
        assertTrue(decision.blockedReasons().isEmpty());
        assertTrue(decision.policyReason().contains("selection_mode=catalog_selection"));
    }

    @Test
    void shouldFreezeOpenAiCompatibleObjectLifecycleAsBlockedAcceptedException() {
        GatewayRequestSemantics semantics = new GatewayRequestSemantics(
                TranslationResourceType.FILE,
                TranslationOperation.FILE_CREATE,
                List.of(InteropFeature.FILE_OBJECT),
                RouteSelectionMode.CATALOG_SELECTION
        );
        CatalogCandidateView candidate = new CatalogCandidateView(
                201L,
                "openai-compatible",
                ProviderType.OPENAI_COMPATIBLE,
                "https://compatible.example.com",
                "compatible-model",
                "compatible-model",
                List.of("openai"),
                true,
                true,
                true,
                true,
                true,
                true,
                ReasoningTransport.OPENAI_CHAT
        );
        String blocker = "OpenAI-compatible 站点当前只冻结为 embeddings/audio/images/moderations 的 OpenAI-style 兼容面；files 仍作为 accepted exception，不在当前实现面内。";

        Mockito.when(siteCapabilityTruthService.resolve(Mockito.eq(candidate), Mockito.eq(semantics)))
                .thenReturn(report(InteropFeature.FILE_OBJECT, InteropCapabilityLevel.UNSUPPORTED, List.of(blocker)));

        NonChatRoutePolicyDecision decision = service.evaluateCandidate(
                "openai",
                "/v1/files",
                semantics,
                candidate,
                canonicalRequest("/v1/files", "compatible-model"),
                null
        );

        assertEquals(SupportStatus.BLOCKED, decision.supportStatus());
        assertEquals(InteropCapabilityLevel.UNSUPPORTED, decision.overallCapabilityLevel());
        assertTrue(decision.blockedReasons().stream().anyMatch(reason -> reason.contains("accepted exception")));
    }

    @Test
    void shouldLiftGeminiUploadSurfaceToOrchestrationEvenWhenFeatureIsBlocked() {
        GatewayRequestSemantics semantics = new GatewayRequestSemantics(
                TranslationResourceType.UPLOAD,
                TranslationOperation.UPLOAD_CREATE,
                List.of(InteropFeature.UPLOAD_CREATE, InteropFeature.FILE_OBJECT),
                RouteSelectionMode.CATALOG_SELECTION
        );
        CatalogCandidateView candidate = new CatalogCandidateView(
                301L,
                "gemini-direct",
                ProviderType.GEMINI_DIRECT,
                "https://generativelanguage.googleapis.com",
                "gemini-2.5-pro",
                "gemini-2.5-pro",
                List.of("openai"),
                true,
                true,
                true,
                true,
                true,
                true,
                ReasoningTransport.GEMINI_THOUGHTS
        );
        String blocker = "Gemini Files API 存在，但不等价于 OpenAI /v1/uploads 的 create/parts/complete/cancel contract，因此仅开放 gateway-local orchestration surface。";

        Mockito.when(siteCapabilityTruthService.resolve(Mockito.eq(candidate), Mockito.eq(semantics)))
                .thenReturn(report(InteropFeature.UPLOAD_CREATE, InteropCapabilityLevel.UNSUPPORTED, List.of(blocker)));

        NonChatRoutePolicyDecision decision = service.evaluateCandidate(
                "openai",
                "/v1/uploads",
                semantics,
                candidate,
                canonicalRequest("/v1/uploads", "gemini-2.5-pro"),
                null
        );

        assertEquals(ExecutionBackend.ORCHESTRATION, decision.preferredBackend());
        assertEquals(SupportStatus.ORCHESTRATION, decision.supportStatus());
        assertEquals(InteropCapabilityLevel.NATIVE, decision.executionCapabilityLevel());
        assertEquals(InteropCapabilityLevel.NATIVE, decision.overallCapabilityLevel());
        assertTrue(decision.blockedReasons().isEmpty());
    }

    @Test
    void shouldKeepRealtimeClientSecretInDistributedTargetModeWhenNoTargetIsResolved() {
        GatewayRequestSemantics semantics = new GatewayRequestSemantics(
                TranslationResourceType.REALTIME,
                TranslationOperation.REALTIME_CLIENT_SECRET_CREATE,
                List.of(InteropFeature.REALTIME_CLIENT_SECRET),
                RouteSelectionMode.DISTRIBUTED_TARGET
        );

        NonChatRoutePolicyDecision decision = service.evaluateWithoutCandidate(
                "openai",
                "/v1/realtime/client_secrets",
                semantics,
                canonicalRequest("/v1/realtime/client_secrets", "resource-orchestration"),
                null,
                "distributed_target_missing",
                List.of("未找到可用的 DistributedKey 绑定。")
        );

        assertEquals(RouteSelectionMode.DISTRIBUTED_TARGET, decision.selectionMode());
        assertEquals(SupportStatus.BLOCKED, decision.supportStatus());
        assertTrue(decision.policyReason().contains("selection_mode=distributed_target"));
    }

    private CapabilityResolutionReport report(
            InteropFeature feature,
            InteropCapabilityLevel effectiveLevel,
            List<String> blockedReasons) {
        CapabilityResolution resolution = new CapabilityResolution(
                feature,
                effectiveLevel,
                effectiveLevel,
                effectiveLevel,
                effectiveLevel,
                blockedReasons,
                List.of()
        );
        ExecutionKind executionKind = effectiveLevel == InteropCapabilityLevel.UNSUPPORTED
                ? ExecutionKind.BLOCKED
                : ExecutionKind.NATIVE;
        return new CapabilityResolutionReport(
                Map.of(feature.wireName(), resolution),
                effectiveLevel,
                effectiveLevel,
                effectiveLevel,
                executionKind,
                executionKind == ExecutionKind.BLOCKED ? "blocked" : "upstream_object_with_local_lineage",
                blockedReasons,
                List.of()
        );
    }

    private CanonicalRequest canonicalRequest(String requestPath, String model) {
        return new CanonicalRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.OPENAI,
                requestPath,
                model,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null
        );
    }
}
