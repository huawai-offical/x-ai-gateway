package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.TranslationExplainService;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
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

@WebFluxTest(controllers = TranslationExplainAdminController.class)
@Import(PermitAllSecurityTestConfig.class)
class TranslationExplainAdminControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private TranslationExplainService translationExplainService;

    @Test
    void shouldExplainTranslationPlan() {
        Mockito.when(translationExplainService.explain(Mockito.any())).thenReturn(new TranslationExecutionPlan(
                true,
                "chat",
                "chat_completion",
                ProviderFamily.OPENAI,
                1L,
                ExecutionKind.NATIVE,
                InteropCapabilityLevel.NATIVE,
                "direct_upstream_execution",
                List.of(),
                List.of(),
                AuthStrategy.BEARER,
                PathStrategy.OPENAI_V1,
                ErrorSchemaStrategy.OPENAI_ERROR,
                Map.of("protocol", "openai"),
                Map.of("publicModel", "gpt-4o")
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
                .jsonPath("$.providerFamily").isEqualTo("OPENAI")
                .jsonPath("$.executionKind").isEqualTo("NATIVE")
                .jsonPath("$.authStrategy").isEqualTo("BEARER");
    }
}
