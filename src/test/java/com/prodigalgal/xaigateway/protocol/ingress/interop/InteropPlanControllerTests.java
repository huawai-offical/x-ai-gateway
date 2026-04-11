package com.prodigalgal.xaigateway.protocol.ingress.interop;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayTokenAuthenticationResolver;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayInteropPlanService;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
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
                        true,
                        "openai",
                        "/v1/audio/transcriptions",
                        "gpt-4o-mini-transcribe",
                        "allow_emulated",
                        List.of("audio_transcription"),
                        List.of(),
                        List.of(),
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
                .jsonPath("$.executable").isEqualTo(true)
                .jsonPath("$.requiredFeatures[0]").isEqualTo("audio_transcription")
                .jsonPath("$.degradationPolicy").isEqualTo("allow_emulated");
    }
}
