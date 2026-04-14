package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.TranslationExplainService;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = TranslationExplainAdminController.class)
@Import(PermitAllSecurityTestConfig.class)
class TranslationExplainAdminControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private TranslationExplainService translationExplainService;

    @Test
    void shouldExplainTranslationPlan() {
        Mockito.when(translationExplainService.explain(Mockito.any())).thenReturn(new CanonicalExecutionPlan(
                true,
                CanonicalIngressProtocol.OPENAI,
                "/v1/chat/completions",
                "gpt-4o",
                "gpt-4o",
                "gpt-4o",
                TranslationResourceType.CHAT,
                TranslationOperation.CHAT_COMPLETION,
                ExecutionKind.NATIVE,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                List.of(InteropFeature.CHAT_TEXT),
                java.util.Map.of("chat_text", InteropCapabilityLevel.NATIVE),
                List.of(),
                List.of()
        ));

        webTestClient.post()
                .uri("/admin/translation/explain")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "distributedKeyPrefix":"sk-gw-test",
                          "protocol":"openai",
                          "requestPath":"/v1/chat/completions",
                          "requestedModel":"gpt-4o",
                          "body":{"model":"gpt-4o","messages":[{"role":"user","content":"hi"}]}
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ingressProtocol").isEqualTo("OPENAI")
                .jsonPath("$.executionKind").isEqualTo("NATIVE")
                .jsonPath("$.executionCapabilityLevel").isEqualTo("NATIVE");
    }
}
