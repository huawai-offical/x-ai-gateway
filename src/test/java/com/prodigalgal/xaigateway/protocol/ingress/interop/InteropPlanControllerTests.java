package com.prodigalgal.xaigateway.protocol.ingress.interop;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayTokenAuthenticationResolver;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayInteropPlanService;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = InteropPlanController.class)
@Import(PermitAllSecurityTestConfig.class)
class InteropPlanControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver;

    @MockitoBean
    private GatewayInteropPlanService gatewayInteropPlanService;

    @Test
    void shouldReturnInteropPlan() {
        Mockito.when(gatewayTokenAuthenticationResolver.authenticate(
                        "Bearer sk-gw-test.secret", null, null, null))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayInteropPlanService.preview(Mockito.eq("sk-gw-test"), Mockito.any()))
                .thenReturn(new InteropPlanResponse(
                        new CanonicalExecutionPlan(
                                true,
                                CanonicalIngressProtocol.OPENAI,
                                "/v1/audio/transcriptions",
                                "gpt-4o-mini-transcribe",
                                "gpt-4o-mini-transcribe",
                                "gpt-4o-mini-transcribe",
                                TranslationResourceType.AUDIO,
                                TranslationOperation.AUDIO_TRANSCRIPTION,
                                ExecutionKind.NATIVE,
                                InteropCapabilityLevel.NATIVE,
                                InteropCapabilityLevel.NATIVE,
                                InteropCapabilityLevel.NATIVE,
                                List.of(com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature.AUDIO_TRANSCRIPTION),
                                Map.of("audio_transcription", InteropCapabilityLevel.NATIVE),
                                List.of(),
                                List.of()
                        ),
                        null,
                        Map.of("executable", true),
                        Map.of("featureLevels", Map.of("audio_transcription", "native"))
                ));

        webTestClient.post()
                .uri("/api/v1/interop/plan")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer sk-gw-test.secret")
                .bodyValue("""
                        {
                          "protocol":"openai",
                          "requestPath":"/v1/audio/transcriptions",
                          "requestedModel":"gpt-4o-mini-transcribe",
                          "degradationPolicy":"allow_emulated",
                          "body":{"model":"gpt-4o-mini-transcribe"}
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.plan.executable").isEqualTo(true)
                .jsonPath("$.plan.requiredFeatures[0]").isEqualTo("AUDIO_TRANSCRIPTION")
                .jsonPath("$.summary.executable").isEqualTo(true);
    }
}
