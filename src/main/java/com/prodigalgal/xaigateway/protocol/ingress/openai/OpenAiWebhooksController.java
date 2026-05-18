package com.prodigalgal.xaigateway.protocol.ingress.openai;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/v1/webhooks")
public class OpenAiWebhooksController {

    private final OpenAiWebhookSignatureVerifier openAiWebhookSignatureVerifier;
    private final OpenAiWebhookEventService openAiWebhookEventService;

    public OpenAiWebhooksController(
            OpenAiWebhookSignatureVerifier openAiWebhookSignatureVerifier,
            OpenAiWebhookEventService openAiWebhookEventService) {
        this.openAiWebhookSignatureVerifier = openAiWebhookSignatureVerifier;
        this.openAiWebhookEventService = openAiWebhookEventService;
    }

    @PostMapping(path = "/openai", consumes = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode acceptOpenAiWebhook(
            @RequestHeader(value = OpenAiWebhookSignatureVerifier.WEBHOOK_ID_HEADER, required = false) String webhookId,
            @RequestHeader(value = OpenAiWebhookSignatureVerifier.WEBHOOK_TIMESTAMP_HEADER, required = false) String webhookTimestamp,
            @RequestHeader(value = OpenAiWebhookSignatureVerifier.WEBHOOK_SIGNATURE_HEADER, required = false) String webhookSignature,
            @RequestBody String rawPayload) {
        var verification = openAiWebhookSignatureVerifier.verify(
                webhookId,
                webhookTimestamp,
                webhookSignature,
                rawPayload
        );
        return openAiWebhookEventService.accept(verification, rawPayload);
    }
}
