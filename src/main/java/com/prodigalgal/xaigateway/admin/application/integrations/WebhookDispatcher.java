package com.prodigalgal.xaigateway.admin.application.integrations;

import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.infra.persistence.entity.NotificationChannelEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.WebhookEndpointEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.WebhookEndpointRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class WebhookDispatcher {

    private final WebhookEndpointRepository webhookEndpointRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public WebhookDispatcher(
            WebhookEndpointRepository webhookEndpointRepository,
            CredentialCryptoService credentialCryptoService,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper) {
        this.webhookEndpointRepository = webhookEndpointRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    public OutboundDispatchResult dispatch(NotificationChannelEntity channel, OutboundEventEnvelope envelope) {
        if (channel.getWebhookEndpointId() == null) {
            return new OutboundDispatchResult(false, null, null, "当前 channel 未绑定 webhook endpoint。");
        }
        WebhookEndpointEntity endpoint = webhookEndpointRepository.findById(channel.getWebhookEndpointId())
                .orElseThrow(() -> new IllegalArgumentException("未找到 webhook endpoint。"));
        if (!endpoint.isEnabled()) {
            return new OutboundDispatchResult(false, null, null, "webhook endpoint 已停用。");
        }
        try {
            Object payload = buildPayload(channel, envelope);
            String payloadJson = objectMapper.writeValueAsString(payload);
            String signature = sign(endpoint, payloadJson);
            return webClientBuilder.build()
                    .post()
                    .uri(endpoint.getEndpointUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> applyHeaders(headers, envelope, signature))
                    .bodyValue(payload)
                    .exchangeToMono(response -> response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> new OutboundDispatchResult(
                                    response.statusCode().is2xxSuccessful(),
                                    response.statusCode().value(),
                                    summarize(body),
                                    response.statusCode().is2xxSuccessful() ? null : summarize(body)
                            )))
                    .timeout(Duration.ofMillis(endpoint.getTimeoutMs() == null ? 5000 : endpoint.getTimeoutMs()))
                    .onErrorResume(error -> reactor.core.publisher.Mono.just(new OutboundDispatchResult(false, null, null, error.getMessage())))
                    .block();
        } catch (JacksonException exception) {
            return new OutboundDispatchResult(false, null, null, "序列化 webhook payload 失败。");
        }
    }

    private Object buildPayload(NotificationChannelEntity channel, OutboundEventEnvelope envelope) {
        NotificationChannelType channelType = NotificationChannelType.valueOf(channel.getChannelType());
        if (channelType == NotificationChannelType.IM_WEBHOOK) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventType", envelope.eventType());
            payload.put("severity", envelope.severity());
            payload.put("summary", envelope.summary());
            payload.put("entity", envelope.entityType() + "/" + envelope.entityRef());
            payload.put("traceUrl", envelope.traceUrl());
            payload.put("runbookUrl", envelope.runbookUrl());
            payload.put("details", envelope.details());
            return payload;
        }
        return envelope;
    }

    private void applyHeaders(HttpHeaders headers, OutboundEventEnvelope envelope, String signature) {
        headers.add("X-XAIG-Event-Type", envelope.eventType());
        headers.add("X-XAIG-Event-Id", envelope.eventId());
        headers.add("X-XAIG-Occurred-At", String.valueOf(envelope.occurredAt()));
        if (signature != null) {
            headers.add("X-XAIG-Signature", signature);
        }
    }

    private String sign(WebhookEndpointEntity endpoint, String payloadJson) {
        if (!WebhookSigningMode.HMAC_SHA256.name().equals(endpoint.getSigningMode())) {
            return null;
        }
        if (endpoint.getSecretCiphertext() == null || endpoint.getSecretCiphertext().isBlank()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    credentialCryptoService.decrypt(endpoint.getSecretCiphertext()).getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            byte[] signature = mac.doFinal(payloadJson.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(signature);
        } catch (Exception exception) {
            throw new IllegalStateException("计算 webhook 签名失败。", exception);
        }
    }

    private String summarize(String body) {
        return Optional.ofNullable(body)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.length() > 256 ? value.substring(0, 256) : value)
                .orElse(null);
    }
}
