package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayTokenAuthenticationResolver;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionService;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

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
    void shouldCreateImageVariation() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("created", 1);

        Mockito.when(gatewayTokenAuthenticationResolver.authenticate("Bearer sk-gw-test.secret", null, null, null))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayResourceExecutionService.executeMultipartJson(
                        Mockito.eq("sk-gw-test"),
                        Mockito.eq("/v1/images/variations"),
                        Mockito.eq("gpt-image-1"),
                        Mockito.anyMap(),
                        Mockito.anyMap()))
                .thenReturn(Mono.just(ResponseEntity.ok(response)));

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("image", new ByteArrayResource("png".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "image.png";
            }
        }).header(HttpHeaders.CONTENT_TYPE, "image/png");

        webTestClient.post()
                .uri("/v1/images/variations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(builder.build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.created").isEqualTo(1);
    }
}
