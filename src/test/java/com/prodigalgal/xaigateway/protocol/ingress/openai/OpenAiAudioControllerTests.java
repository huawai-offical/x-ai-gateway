package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayTokenAuthenticationResolver;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionService;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = OpenAiAudioController.class)
@Import(PermitAllSecurityTestConfig.class)
class OpenAiAudioControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver;

    @MockitoBean
    private GatewayResourceExecutionService gatewayResourceExecutionService;

    @Test
    void shouldCreateTranscription() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("text", "hello");

        Mockito.when(gatewayTokenAuthenticationResolver.authenticate("Bearer sk-gw-test.secret", null, null, null))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayResourceExecutionService.executeMultipartJson(
                        Mockito.eq("sk-gw-test"),
                        Mockito.eq("/v1/audio/transcriptions"),
                        Mockito.eq("gpt-4o-mini-transcribe"),
                        Mockito.anyMap(),
                        Mockito.anyMap()))
                .thenReturn(Mono.just(ResponseEntity.ok(body)));

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("model", "gpt-4o-mini-transcribe");
        builder.part("file", new ByteArrayResource("voice".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "voice.wav";
            }
        }).header(HttpHeaders.CONTENT_TYPE, "audio/wav");

        webTestClient.post()
                .uri("/v1/audio/transcriptions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(builder.build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.text").isEqualTo("hello");
    }

    @Test
    void shouldCreateSpeech() {
        Mockito.when(gatewayTokenAuthenticationResolver.authenticate("Bearer sk-gw-test.secret", null, null, null))
                .thenReturn(new AuthenticatedDistributedKey(1L, "sk-gw-test", "test-key"));
        Mockito.when(gatewayResourceExecutionService.executeBinaryJson(
                        Mockito.eq("sk-gw-test"),
                        Mockito.eq("/v1/audio/speech"),
                        Mockito.any(),
                        Mockito.eq("gpt-4o-mini-tts")))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body("audio".getBytes(StandardCharsets.UTF_8)));

        webTestClient.post()
                .uri("/v1/audio/speech")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model":"gpt-4o-mini-tts",
                          "input":"hello",
                          "voice":"alloy"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody(byte[].class).isEqualTo("audio".getBytes(StandardCharsets.UTF_8));
    }
}
