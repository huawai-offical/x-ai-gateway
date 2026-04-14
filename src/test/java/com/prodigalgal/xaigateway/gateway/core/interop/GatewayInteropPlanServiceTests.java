package com.prodigalgal.xaigateway.gateway.core.interop;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlanCompilation;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.protocol.ingress.interop.InteropPlanRequest;
import com.prodigalgal.xaigateway.protocol.ingress.interop.InteropPlanResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
                                List.of("response_object 以 emulated 执行。"),
                                List.of("当前策略不允许 emulated 执行。")
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
        assertTrue(response.plan().blockers().stream().anyMatch(item -> item.contains("emulated")));
    }

    @Test
    void shouldAllowNativeFeatureForOpenAiAudioTranscription() {
        ObjectNode body = new ObjectMapper().createObjectNode();
        body.put("model", "gpt-4o-mini-transcribe");
        Mockito.when(translationExecutionPlanCompiler.compilePreview(
                        Mockito.eq("sk-gw-test"),
                        Mockito.eq("openai"),
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
        assertTrue(response.plan().blockers().isEmpty());
    }
}
