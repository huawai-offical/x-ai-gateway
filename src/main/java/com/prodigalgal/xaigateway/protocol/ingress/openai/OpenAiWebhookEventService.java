package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceType;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayAsyncResourceEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayAsyncResourceRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@Transactional
public class OpenAiWebhookEventService {

    static final long SYSTEM_WEBHOOK_DISTRIBUTED_KEY_ID = 0L;

    private final GatewayAsyncResourceRepository gatewayAsyncResourceRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public OpenAiWebhookEventService(
            GatewayAsyncResourceRepository gatewayAsyncResourceRepository,
            ObjectMapper objectMapper) {
        this(gatewayAsyncResourceRepository, objectMapper, Clock.systemUTC());
    }

    OpenAiWebhookEventService(
            GatewayAsyncResourceRepository gatewayAsyncResourceRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.gatewayAsyncResourceRepository = gatewayAsyncResourceRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public JsonNode accept(
            OpenAiWebhookSignatureVerifier.OpenAiWebhookVerificationResult verification,
            String rawPayload) {
        JsonNode payload = parsePayload(rawPayload);
        String eventId = eventId(payload, rawPayload);
        String eventType = text(payload, "type", "unknown");
        boolean duplicate = verification.duplicateDelivery() || gatewayAsyncResourceRepository.existsByResourceKey(eventId);
        if (!duplicate) {
            persistEvent(verification, rawPayload, payload, eventId, eventType);
        }
        ObjectNode response = objectMapper.createObjectNode();
        response.put("object", "webhook.delivery");
        response.put("id", verification.webhookId());
        response.put("event_id", eventId);
        response.put("type", eventType);
        response.put("received", true);
        response.put("duplicate", duplicate);
        response.put("created_at", verification.timestamp().getEpochSecond());
        return response;
    }

    private void persistEvent(
            OpenAiWebhookSignatureVerifier.OpenAiWebhookVerificationResult verification,
            String rawPayload,
            JsonNode payload,
            String eventId,
            String eventType) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("object_mode", "openai_webhook_event");
        metadata.put("webhook_id", verification.webhookId());
        metadata.put("webhook_timestamp", verification.timestamp().getEpochSecond());
        metadata.put("event_type", eventType);
        metadata.put("source", "openai");
        metadata.put("received_at", java.time.Instant.now(clock).getEpochSecond());

        GatewayAsyncResourceEntity entity = new GatewayAsyncResourceEntity();
        entity.setResourceKey(eventId);
        entity.setDistributedKeyId(SYSTEM_WEBHOOK_DISTRIBUTED_KEY_ID);
        entity.setResourceType(GatewayAsyncResourceType.WEBHOOK_EVENT);
        entity.setRequestModel("openai.webhook");
        entity.setStatus("received");
        entity.setRequestPayloadJson(rawPayload == null ? "" : rawPayload);
        entity.setResponsePayloadJson(writeJson(payload));
        entity.setMetadataJson(writeJson(metadata));
        gatewayAsyncResourceRepository.save(entity);
    }

    private JsonNode parsePayload(String rawPayload) {
        try {
            JsonNode payload = objectMapper.readTree(rawPayload == null ? "" : rawPayload);
            if (payload == null || !payload.isObject()) {
                throw new IllegalArgumentException("OpenAI webhook payload 必须是 JSON object。");
            }
            return payload;
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("OpenAI webhook payload 不是合法 JSON。", exception);
        }
    }

    private String eventId(JsonNode payload, String rawPayload) {
        String id = text(payload, "id", null);
        if (id != null) {
            return id;
        }
        return "evt_gateway_" + digest(rawPayload == null ? "" : rawPayload);
    }

    private String text(JsonNode node, String fieldName, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        String value = node.path(fieldName).asText(null);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成 OpenAI webhook event id。", exception);
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化 OpenAI webhook event 失败。", exception);
        }
    }
}
