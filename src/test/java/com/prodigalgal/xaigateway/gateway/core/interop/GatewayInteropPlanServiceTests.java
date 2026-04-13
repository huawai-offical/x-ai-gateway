package com.prodigalgal.xaigateway.gateway.core.interop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
                .thenReturn(new TranslationExecutionPlanCompilation(
                        new TranslationExecutionPlan(
                                false,
                                "openai",
                                "/v1/responses",
                                "claude-3-7-sonnet",
                                "claude-3-7-sonnet",
                                "claude-3-7-sonnet",
                                com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily.GENERIC_OPENAI,
                                TranslationResourceType.RESPONSE,
                                TranslationOperation.RESPONSE_CREATE,
                                List.of(InteropFeature.RESPONSE_OBJECT),
                                java.util.Map.of("response_object", InteropCapabilityLevel.EMULATED),
                                null,
                                null,
                                null,
                                com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind.BLOCKED,
                                InteropCapabilityLevel.EMULATED,
                                "blocked",
                                List.of("response_object 以 emulated 执行。"),
                                List.of("当前策略不允许 emulated 执行。"),
                                null,
                                null,
                                null,
                                new TranslationExecutionRequestMapping(
                                        "openai",
                                        "/v1/responses",
                                        "claude-3-7-sonnet",
                                        "claude-3-7-sonnet",
                                        "claude-3-7-sonnet",
                                        com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily.GENERIC_OPENAI,
                                        List.of(InteropFeature.RESPONSE_OBJECT),
                                        java.util.Map.of("response_object", InteropCapabilityLevel.EMULATED)
                                ),
                                new TranslationExecutionResponseMapping(
                                        null,
                                        null,
                                        null,
                                        com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind.BLOCKED,
                                        InteropCapabilityLevel.EMULATED,
                                        "blocked",
                                        null,
                                        null,
                                        null
                                )
                        ),
                        null,
                        new GatewayRequestSemantics(
                                TranslationResourceType.RESPONSE,
                                TranslationOperation.RESPONSE_CREATE,
                                List.of(InteropFeature.RESPONSE_OBJECT),
                                true
                        )
                ));
        InteropPlanRequest request = new InteropPlanRequest(
                "openai",
                "/v1/responses",
                null,
                "strict",
                body
        );

        InteropPlanResponse response = gatewayInteropPlanService.preview("sk-gw-test", request);

        assertFalse(response.executable());
        assertTrue(response.blockers().stream().anyMatch(item -> item.contains("emulated")));
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
                .thenReturn(new TranslationExecutionPlanCompilation(
                        new TranslationExecutionPlan(
                                true,
                                "openai",
                                "/v1/audio/transcriptions",
                                "gpt-4o-mini-transcribe",
                                "gpt-4o-mini-transcribe",
                                "gpt-4o-mini-transcribe",
                                com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily.GENERIC_OPENAI,
                                TranslationResourceType.AUDIO,
                                TranslationOperation.AUDIO_TRANSCRIPTION,
                                List.of(InteropFeature.AUDIO_TRANSCRIPTION),
                                java.util.Map.of("audio_transcription", InteropCapabilityLevel.NATIVE),
                                null,
                                null,
                                null,
                                com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind.NATIVE,
                                InteropCapabilityLevel.NATIVE,
                                "direct_upstream_execution",
                                List.of(),
                                List.of(),
                                null,
                                null,
                                null,
                                new TranslationExecutionRequestMapping(
                                        "openai",
                                        "/v1/audio/transcriptions",
                                        "gpt-4o-mini-transcribe",
                                        "gpt-4o-mini-transcribe",
                                        "gpt-4o-mini-transcribe",
                                        com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily.GENERIC_OPENAI,
                                        List.of(InteropFeature.AUDIO_TRANSCRIPTION),
                                        java.util.Map.of("audio_transcription", InteropCapabilityLevel.NATIVE)
                                ),
                                new TranslationExecutionResponseMapping(
                                        null,
                                        null,
                                        null,
                                        com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind.NATIVE,
                                        InteropCapabilityLevel.NATIVE,
                                        "direct_upstream_execution",
                                        null,
                                        null,
                                        null
                                )
                        ),
                        null,
                        new GatewayRequestSemantics(
                                TranslationResourceType.AUDIO,
                                TranslationOperation.AUDIO_TRANSCRIPTION,
                                List.of(InteropFeature.AUDIO_TRANSCRIPTION),
                                true
                        )
                ));
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
}
