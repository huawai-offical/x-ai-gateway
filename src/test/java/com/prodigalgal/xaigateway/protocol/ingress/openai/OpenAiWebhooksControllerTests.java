package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

@WebFluxTest(controllers = OpenAiWebhooksController.class)
@Import(PermitAllSecurityTestConfig.class)
class OpenAiWebhooksControllerTests {

    private static final Instant NOW = Instant.parse("2026-05-16T00:00:00Z");

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OpenAiWebhookSignatureVerifier openAiWebhookSignatureVerifier;

    @MockitoBean
    private OpenAiWebhookEventService openAiWebhookEventService;

    @Test
    void shouldAcceptVerifiedOpenAiWebhookRawPayload() throws Exception {
        String payload = "{\"object\":\"event\",\"id\":\"evt_1\",\"type\":\"response.completed\"}";
        var verification = new OpenAiWebhookSignatureVerifier.OpenAiWebhookVerificationResult("wh_1", NOW, false);
        Mockito.when(openAiWebhookSignatureVerifier.verify("wh_1", Long.toString(NOW.getEpochSecond()), "v1,signature", payload))
                .thenReturn(verification);
        Mockito.when(openAiWebhookEventService.accept(verification, payload))
                .thenReturn(objectMapper.readTree("""
                        {
                          "object":"webhook.delivery",
                          "id":"wh_1",
                          "event_id":"evt_1",
                          "type":"response.completed",
                          "received":true,
                          "duplicate":false
                        }
                        """));

        webTestClient.post()
                .uri("/v1/webhooks/openai")
                .header(OpenAiWebhookSignatureVerifier.WEBHOOK_ID_HEADER, "wh_1")
                .header(OpenAiWebhookSignatureVerifier.WEBHOOK_TIMESTAMP_HEADER, Long.toString(NOW.getEpochSecond()))
                .header(OpenAiWebhookSignatureVerifier.WEBHOOK_SIGNATURE_HEADER, "v1,signature")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.received").isEqualTo(true)
                .jsonPath("$.duplicate").isEqualTo(false)
                .jsonPath("$.event_id").isEqualTo("evt_1");
    }

    @Test
    void shouldReturnOpenAiStyleErrorForInvalidSignature() {
        String payload = "{\"id\":\"evt_bad\",\"type\":\"response.completed\"}";
        Mockito.when(openAiWebhookSignatureVerifier.verify("wh_bad", Long.toString(NOW.getEpochSecond()), "v1,bad", payload))
                .thenThrow(new IllegalArgumentException("OpenAI webhook signature 校验失败。"));

        webTestClient.post()
                .uri("/v1/webhooks/openai")
                .header(OpenAiWebhookSignatureVerifier.WEBHOOK_ID_HEADER, "wh_bad")
                .header(OpenAiWebhookSignatureVerifier.WEBHOOK_TIMESTAMP_HEADER, Long.toString(NOW.getEpochSecond()))
                .header(OpenAiWebhookSignatureVerifier.WEBHOOK_SIGNATURE_HEADER, "v1,bad")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.type").isEqualTo("invalid_request_error")
                .jsonPath("$.error.code").isEqualTo("invalid_argument")
                .jsonPath("$.error.message").isEqualTo("OpenAI webhook signature 校验失败。");

        Mockito.verifyNoInteractions(openAiWebhookEventService);
    }

    @Test
    void shouldReturnOpenAiStyleErrorWhenWebhookHeadersAreMissing() {
        String payload = "{\"id\":\"evt_missing\",\"type\":\"response.completed\"}";
        Mockito.when(openAiWebhookSignatureVerifier.verify(null, null, null, payload))
                .thenThrow(new IllegalArgumentException("webhook-id 不能为空。"));

        webTestClient.post()
                .uri("/v1/webhooks/openai")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.type").isEqualTo("invalid_request_error")
                .jsonPath("$.error.code").isEqualTo("invalid_argument")
                .jsonPath("$.error.message").isEqualTo("webhook-id 不能为空。");

        Mockito.verifyNoInteractions(openAiWebhookEventService);
    }

    @Test
    void shouldVerifyRealSignatureShapeInControllerContract() throws Exception {
        String payload = "{\"object\":\"event\",\"id\":\"evt_contract\",\"type\":\"response.completed\"}";
        String signature = signature("wh_contract", NOW.getEpochSecond(), payload, "secret");
        var verification = new OpenAiWebhookSignatureVerifier.OpenAiWebhookVerificationResult("wh_contract", NOW, false);
        Mockito.when(openAiWebhookSignatureVerifier.verify("wh_contract", Long.toString(NOW.getEpochSecond()), signature, payload))
                .thenReturn(verification);
        Mockito.when(openAiWebhookEventService.accept(verification, payload))
                .thenReturn(objectMapper.readTree("""
                        {"object":"webhook.delivery","id":"wh_contract","event_id":"evt_contract","received":true,"duplicate":false}
                        """));

        webTestClient.post()
                .uri("/v1/webhooks/openai")
                .header(OpenAiWebhookSignatureVerifier.WEBHOOK_ID_HEADER, "wh_contract")
                .header(OpenAiWebhookSignatureVerifier.WEBHOOK_TIMESTAMP_HEADER, Long.toString(NOW.getEpochSecond()))
                .header(OpenAiWebhookSignatureVerifier.WEBHOOK_SIGNATURE_HEADER, signature)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isOk();
    }

    private String signature(String webhookId, long timestamp, String rawPayload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((webhookId + "." + timestamp + "." + rawPayload).getBytes(StandardCharsets.UTF_8));
        return "v1," + Base64.getEncoder().encodeToString(digest);
    }
}
