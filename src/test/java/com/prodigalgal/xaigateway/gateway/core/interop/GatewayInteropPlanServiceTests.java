package com.prodigalgal.xaigateway.gateway.core.interop;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlanCompilation;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.protocol.ingress.interop.InteropPlanRequest;
import com.prodigalgal.xaigateway.protocol.ingress.interop.InteropPlanResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayInteropPlanServiceTests {

    private final TranslationExecutionPlanCompiler translationExecutionPlanCompiler = Mockito.mock(TranslationExecutionPlanCompiler.class);
    private final GatewayInteropPlanService gatewayInteropPlanService =
            new GatewayInteropPlanService(null, translationExecutionPlanCompiler);

    @Test
    void shouldBlockWhenDegradationPolicyDoesNotAllowSelectedCapability() {
        ObjectNode body = new ObjectMapper().createObjectNode();
        body.put("model", "claude-3-7-sonnet");
        Mockito.when(translationExecutionPlanCompiler.compilePreview(
                        Mockito.eq("sk-gw-test"),
                        Mockito.eq("openai"),
                        Mockito.isNull(),
                        Mockito.eq("/v1/responses"),
                        Mockito.isNull(),
                        Mockito.eq(GatewayDegradationPolicy.STRICT),
                        Mockito.any(),
                        Mockito.eq(body)
                ))
                .thenReturn(new CanonicalExecutionPlanCompilation(
                        new CanonicalExecutionPlan(
                                false,
                                CanonicalIngressProtocol.OPENAI,
                                "/v1/responses",
                                "claude-3-7-sonnet",
                                "claude-3-7-sonnet",
                                "claude-3-7-sonnet",
                                TranslationResourceType.RESPONSE,
                                TranslationOperation.RESPONSE_CREATE,
                                com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind.BLOCKED,
                                InteropCapabilityLevel.EMULATED,
                                InteropCapabilityLevel.EMULATED,
                                InteropCapabilityLevel.EMULATED,
                                List.of(InteropFeature.RESPONSE_OBJECT),
                                java.util.Map.of("response_object", InteropCapabilityLevel.EMULATED),
                                List.of("response_object 当前为非 native 兼容展示，只能观测，不能作为执行成功条件。"),
                                List.of("当前策略只允许 native 执行；非 native、不可无损或历史兼容能力必须阻断。")
                        ),
                        null,
                        new GatewayRequestSemantics(
                                TranslationResourceType.RESPONSE,
                                TranslationOperation.RESPONSE_CREATE,
                                List.of(InteropFeature.RESPONSE_OBJECT),
                                true
                        ),
                        new CanonicalRequest("sk-gw-test", CanonicalIngressProtocol.OPENAI, "/v1/responses", "claude-3-7-sonnet", List.of(), List.of(), null, null, null, null, body)
                ));
        InteropPlanRequest request = new InteropPlanRequest(
                "openai",
                "/v1/responses",
                null,
                "strict",
                body
        );

        InteropPlanResponse response = gatewayInteropPlanService.preview("sk-gw-test", request);

        assertFalse(response.plan().executable());
        assertEquals("/v1/responses", response.plan().normalizedPath());
        assertEquals("responses", response.plan().surface());
        assertEquals(SupportStatus.BLOCKED, response.plan().supportStatus());
        assertEquals(InteropCapabilityLevel.UNSUPPORTED, response.plan().degradationLevel());
        assertEquals(RouteSelectionMode.CATALOG_SELECTION, response.plan().routeSelectionMode());
        assertEquals("catalog_selection", response.summary().get("routeSelectionMode"));
        assertEquals("", response.summary().get("routePolicyReason"));
        assertEquals("", response.summary().get("renderPolicyReason"));
        assertEquals("", response.summary().get("fallbackPolicyReason"));
        assertEquals("responses", response.summary().get("surface"));
        assertEquals("/v1/responses", response.summary().get("normalizedPath"));
        assertEquals("blocked", response.summary().get("supportStatus"));
        assertEquals("catalog_selection", response.debug().get("routeSelectionMode"));
        assertEquals("", response.debug().get("renderPolicyReason"));
        assertEquals("", response.debug().get("fallbackPolicyReason"));
        assertTrue(response.plan().blockers().stream().anyMatch(item -> item.contains("native")));
    }

    @Test
    void shouldAllowNativeFeatureForOpenAiAudioTranscription() {
        ObjectNode body = new ObjectMapper().createObjectNode();
        body.put("model", "gpt-4o-mini-transcribe");
        Mockito.when(translationExecutionPlanCompiler.compilePreview(
                        Mockito.eq("sk-gw-test"),
                        Mockito.eq("openai"),
                        Mockito.isNull(),
                        Mockito.eq("/v1/audio/transcriptions"),
                        Mockito.isNull(),
                        Mockito.eq(GatewayDegradationPolicy.STRICT),
                        Mockito.any(),
                        Mockito.eq(body)
                ))
                .thenReturn(new CanonicalExecutionPlanCompilation(
                        new CanonicalExecutionPlan(
                                true,
                                CanonicalIngressProtocol.OPENAI,
                                "/v1/audio/transcriptions",
                                "gpt-4o-mini-transcribe",
                                "gpt-4o-mini-transcribe",
                                "gpt-4o-mini-transcribe",
                                TranslationResourceType.AUDIO,
                                TranslationOperation.AUDIO_TRANSCRIPTION,
                                com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind.NATIVE,
                                InteropCapabilityLevel.NATIVE,
                                InteropCapabilityLevel.NATIVE,
                                InteropCapabilityLevel.NATIVE,
                                List.of(InteropFeature.AUDIO_TRANSCRIPTION),
                                java.util.Map.of("audio_transcription", InteropCapabilityLevel.NATIVE),
                                List.of(),
                                List.of()
                        ),
                        null,
                        new GatewayRequestSemantics(
                                TranslationResourceType.AUDIO,
                                TranslationOperation.AUDIO_TRANSCRIPTION,
                                List.of(InteropFeature.AUDIO_TRANSCRIPTION),
                                true
                        ),
                        new CanonicalRequest("sk-gw-test", CanonicalIngressProtocol.OPENAI, "/v1/audio/transcriptions", "gpt-4o-mini-transcribe", List.of(), List.of(), null, null, null, null, body)
                ));
        InteropPlanRequest request = new InteropPlanRequest(
                "openai",
                "/v1/audio/transcriptions",
                null,
                "strict",
                body
        );

        InteropPlanResponse response = gatewayInteropPlanService.preview("sk-gw-test", request);

        assertTrue(response.plan().executable());
        assertEquals("/v1/audio/transcriptions", response.plan().normalizedPath());
        assertEquals("audio", response.plan().surface());
        assertEquals(SupportStatus.NATIVE, response.plan().supportStatus());
        assertEquals(InteropCapabilityLevel.NATIVE, response.plan().degradationLevel());
        assertEquals(RouteSelectionMode.CATALOG_SELECTION, response.plan().routeSelectionMode());
        assertEquals("catalog_selection", response.summary().get("routeSelectionMode"));
        assertEquals("", response.summary().get("renderPolicyReason"));
        assertEquals("", response.summary().get("fallbackPolicyReason"));
        assertEquals("audio", response.summary().get("surface"));
        assertEquals("/v1/audio/transcriptions", response.summary().get("normalizedPath"));
        assertEquals("native", response.summary().get("supportStatus"));
        assertEquals("", response.debug().get("routePolicyReason"));
        assertEquals("", response.debug().get("renderPolicyReason"));
        assertEquals("", response.debug().get("fallbackPolicyReason"));
        assertTrue(response.plan().blockers().isEmpty());
    }

    @Test
    void shouldExposeRuntimeProviderDebugForProviderSpecificOpenAiStyleSelection() {
        assertRuntimeProviderDebug(
                "https://token-plan-sgp.xiaomimimo.com/v1",
                "mimo-v2.5-pro",
                "XIAOMI_MIMO",
                "xiaomi_mimo.openai_compatible",
                "XIAOMI_MIMO"
        );
        assertRuntimeProviderDebug(
                "https://api.deepseek.com",
                "deepseek-chat",
                "DEEPSEEK",
                "deepseek.openai_compatible",
                "DEEPSEEK"
        );
        assertRuntimeProviderDebug(
                "https://api.x.ai/v1",
                "grok-4.3",
                "XAI",
                "grok.openai_compatible",
                "GROK"
        );
    }

    private void assertRuntimeProviderDebug(
            String baseUrl,
            String model,
            String expectedRuntimeProvider,
            String expectedProtocolSuite,
            String expectedSiteKind) {
        ObjectNode body = new ObjectMapper().createObjectNode();
        body.put("model", model);
        CatalogCandidateView candidate = new CatalogCandidateView(
                101L,
                expectedRuntimeProvider.toLowerCase(java.util.Locale.ROOT),
                ProviderType.OPENAI_COMPATIBLE,
                baseUrl,
                model,
                model,
                List.of("openai", "responses"),
                true,
                true,
                false,
                false,
                true,
                true,
                ReasoningTransport.OPENAI_CHAT
        );
        RouteCandidateView routeCandidate = new RouteCandidateView(candidate, 11L, 10, 100);
        RouteSelectionResult selectionResult = new RouteSelectionResult(
                1L,
                "sk-gw-test",
                model,
                model,
                model,
                "openai",
                "prefix",
                "fingerprint",
                model,
                RouteSelectionSource.WEIGHTED_HASH,
                routeCandidate,
                List.of(routeCandidate)
        );
        Mockito.when(translationExecutionPlanCompiler.compilePreview(
                        Mockito.eq("sk-gw-test"),
                        Mockito.eq("openai"),
                        Mockito.isNull(),
                        Mockito.eq("/v1/chat/completions"),
                        Mockito.isNull(),
                        Mockito.eq(GatewayDegradationPolicy.STRICT),
                        Mockito.any(),
                        Mockito.eq(body)
                ))
                .thenReturn(new CanonicalExecutionPlanCompilation(
                        new CanonicalExecutionPlan(
                                true,
                                CanonicalIngressProtocol.OPENAI,
                                "/v1/chat/completions",
                                "/v1/chat/completions",
                                "chat",
                                model,
                                model,
                                model,
                                TranslationResourceType.CHAT,
                                TranslationOperation.CHAT_COMPLETION,
                                ExecutionKind.NATIVE,
                                ExecutionBackend.NATIVE,
                                SupportStatus.NATIVE,
                                "direct_upstream_execution",
                                List.of(ExecutionBackend.NATIVE),
                                "test",
                                InteropCapabilityLevel.NATIVE,
                                InteropCapabilityLevel.NATIVE,
                                InteropCapabilityLevel.NATIVE,
                                InteropCapabilityLevel.NATIVE,
                                List.of(),
                                List.of(InteropFeature.CHAT_TEXT),
                                java.util.Map.of("chat_text", InteropCapabilityLevel.NATIVE),
                                List.of(),
                                List.of()
                        ),
                        selectionResult,
                        new GatewayRequestSemantics(
                                TranslationResourceType.CHAT,
                                TranslationOperation.CHAT_COMPLETION,
                                List.of(InteropFeature.CHAT_TEXT),
                                true
                        ),
                        new CanonicalRequest("sk-gw-test", CanonicalIngressProtocol.OPENAI, "/v1/chat/completions", model, List.of(), List.of(), null, null, null, null, body)
                ));

        InteropPlanResponse response = gatewayInteropPlanService.preview("sk-gw-test", new InteropPlanRequest(
                "openai",
                "/v1/chat/completions",
                null,
                "strict",
                body
        ));

        assertEquals(expectedRuntimeProvider, response.debug().get("runtimeProvider"));
        assertEquals(expectedProtocolSuite, response.debug().get("runtimeProtocolSuite"));
        assertEquals(expectedSiteKind, response.debug().get("siteKind"));
    }
}
