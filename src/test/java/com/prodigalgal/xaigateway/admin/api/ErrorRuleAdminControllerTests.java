package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.ErrorRuleService;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.List;

@WebFluxTest(controllers = ErrorRuleAdminController.class)
@Import(PermitAllSecurityTestConfig.class)
class ErrorRuleAdminControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ErrorRuleService errorRuleService;

    @Test
    void shouldPreviewRules() {
        Mockito.when(errorRuleService.preview(Mockito.any()))
                .thenReturn(new ErrorRulePreviewResponse(List.of(new ErrorRuleResponse(
                        1L, true, 100, "OPENAI_DIRECT", "openai", null, "/v1/chat/completions", 500,
                        "UPSTREAM_ERROR", "UPSTREAM", "REWRITE", 502, "REWRITTEN", "rewritten", null,
                        Instant.now(), Instant.now()
                ))));

        webTestClient.post()
                .uri("/admin/error-rules/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "protocol":"openai",
                          "requestPath":"/v1/chat/completions",
                          "httpStatus":500,
                          "errorCode":"UPSTREAM_ERROR",
                          "matchScope":"UPSTREAM"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.matchedRules[0].action").isEqualTo("REWRITE");
    }
}
