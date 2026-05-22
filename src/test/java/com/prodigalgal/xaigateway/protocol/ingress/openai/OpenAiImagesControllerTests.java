package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayTokenAuthenticationResolver;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionService;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = OpenAiImagesController.class)
@Import(PermitAllSecurityTestConfig.class)
class OpenAiImagesControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver;

    @MockitoBean
    private GatewayResourceExecutionService gatewayResourceExecutionService;

    @Test
    void shouldCreateImageGeneration() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("created", 1);
        response.putArray("data").addObject().put("url", "https://example.com/image.png");

        Mockito.when(gatewayTokenAuthenticationResolver.authenticate("Bearer sk-gw-test.secret", null, null, null))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayResourceExecutionService.executeJson(
                        Mockito.eq("sk-gw-test"),
                        Mockito.eq("/v1/images/generations"),
                        Mockito.any(),
                        Mockito.eq("gpt-image-1")))
                .thenReturn(ResponseEntity.ok(response));

        webTestClient.post()
                .uri("/v1/images/generations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "prompt":"draw a gateway dashboard"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].url").isEqualTo("https://example.com/image.png");
    }

    @Test
    void shouldCreateGeminiCompatibleImageGenerationReturningB64Json() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("created", 1);
        response.putArray("data").addObject().put("b64_json", "AQID");

        Mockito.when(gatewayTokenAuthenticationResolver.authenticate("Bearer sk-gw-test.secret", null, null, null))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayResourceExecutionService.executeJson(
                        Mockito.eq("sk-gw-test"),
                        Mockito.eq("/v1/images/generations"),
                        Mockito.any(),
                        Mockito.eq("gpt-image-1")))
                .thenReturn(ResponseEntity.ok(response));

        webTestClient.post()
                .uri("/v1/images/generations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"gemini-2.0-flash-preview-image-generation",
                          "prompt":"draw a gateway dashboard"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].b64_json").isEqualTo("AQID");
    }
}
