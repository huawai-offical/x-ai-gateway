package com.prodigalgal.xaigateway.admin.application.integrations;

import com.prodigalgal.xaigateway.admin.api.OutboundDeliveryResponse;
import com.prodigalgal.xaigateway.infra.persistence.entity.NotificationChannelEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.OutboundDeliveryEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.NotificationChannelRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.OutboundDeliveryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class OutboundDeliveryService {

    private final OutboundDeliveryRepository outboundDeliveryRepository;
    private final NotificationChannelRepository notificationChannelRepository;
    private final OutboundChannelDispatcher outboundChannelDispatcher;
    private final ObjectMapper objectMapper;

    public OutboundDeliveryService(
            OutboundDeliveryRepository outboundDeliveryRepository,
            NotificationChannelRepository notificationChannelRepository,
            OutboundChannelDispatcher outboundChannelDispatcher,
            ObjectMapper objectMapper) {
        this.outboundDeliveryRepository = outboundDeliveryRepository;
        this.notificationChannelRepository = notificationChannelRepository;
        this.outboundChannelDispatcher = outboundChannelDispatcher;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<OutboundDeliveryResponse> list(
            String eventType,
            String deliveryStatus,
            String channelType,
            String entityType,
            String entityRef,
            String requestId) {
        return outboundDeliveryRepository.findTop200ByOrderByOccurredAtDesc().stream()
                .filter(item -> matches(item.getEventType(), eventType))
                .filter(item -> matches(item.getDeliveryStatus(), deliveryStatus))
                .filter(item -> matches(item.getEntityType(), entityType))
                .filter(item -> matches(item.getEntityRef(), entityRef))
                .filter(item -> matches(item.getRequestId(), requestId))
                .filter(item -> filterByChannelType(item.getChannelId(), channelType))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OutboundDeliveryResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    public OutboundDeliveryResponse createPendingDelivery(Long channelId, OutboundEventEnvelope envelope) {
        OutboundDeliveryEntity entity = new OutboundDeliveryEntity();
        entity.setEventId(envelope.eventId());
        entity.setEventType(envelope.eventType());
        entity.setChannelId(channelId);
        entity.setEntityType(envelope.entityType());
        entity.setEntityRef(envelope.entityRef());
        entity.setRequestId(envelope.requestId());
        entity.setGatewayResourceKey(envelope.gatewayResourceKey());
        entity.setUpstreamObjectId(envelope.upstreamObjectId());
        entity.setDeliveryStatus(OutboundDeliveryStatus.PENDING.name());
        entity.setAttemptCount(0);
        entity.setPayloadJson(writeJson(envelope));
        entity.setOccurredAt(envelope.occurredAt());
        entity.setNextRetryAt(Instant.now());
        return toResponse(outboundDeliveryRepository.save(entity));
    }

    public OutboundDeliveryResponse deliver(Long id) {
        OutboundDeliveryEntity entity = getEntity(id);
        NotificationChannelEntity channel = notificationChannelRepository.findById(entity.getChannelId())
                .orElseThrow(() -> new IllegalArgumentException("未找到 notification channel。"));
        OutboundEventEnvelope envelope = readEnvelope(entity.getPayloadJson());
        entity.setDeliveryStatus(OutboundDeliveryStatus.DELIVERING.name());
        outboundDeliveryRepository.save(entity);

        OutboundDispatchResult result = outboundChannelDispatcher.dispatch(channel, envelope);
        entity.setAttemptCount(entity.getAttemptCount() + 1);
        entity.setResponseCode(result.responseCode());
        entity.setResponseSummary(result.responseSummary());
        entity.setLastError(result.errorMessage());
        if (result.succeeded()) {
            entity.setDeliveryStatus(OutboundDeliveryStatus.SUCCEEDED.name());
            entity.setDeliveredAt(Instant.now());
            entity.setNextRetryAt(null);
        } else {
            entity.setDeliveredAt(null);
            if (entity.getAttemptCount() >= 4) {
                entity.setDeliveryStatus(OutboundDeliveryStatus.DEAD_LETTER.name());
                entity.setNextRetryAt(null);
            } else {
                entity.setDeliveryStatus(OutboundDeliveryStatus.FAILED.name());
                entity.setNextRetryAt(nextRetryAt(entity.getAttemptCount()));
            }
        }
        return toResponse(outboundDeliveryRepository.save(entity));
    }

    public OutboundDeliveryResponse replay(Long id) {
        OutboundDeliveryEntity entity = getEntity(id);
        entity.setDeliveryStatus(OutboundDeliveryStatus.PENDING.name());
        entity.setNextRetryAt(Instant.now());
        entity.setLastError(null);
        entity.setResponseCode(null);
        entity.setResponseSummary(null);
        outboundDeliveryRepository.save(entity);
        return deliver(id);
    }

    public void processDueRetries() {
        List<String> statuses = List.of(OutboundDeliveryStatus.PENDING.name(), OutboundDeliveryStatus.FAILED.name());
        outboundDeliveryRepository.findAllByDeliveryStatusInAndNextRetryAtLessThanEqualOrderByOccurredAtAsc(statuses, Instant.now())
                .forEach(item -> {
                    try {
                        deliver(item.getId());
                    } catch (IllegalArgumentException ignored) {
                        // channel 被删除时仅保留 failed/dead-letter 记录
                    }
                });
    }

    private Instant nextRetryAt(int attemptCount) {
        return switch (attemptCount) {
            case 1 -> Instant.now().plusSeconds(60);
            case 2 -> Instant.now().plusSeconds(5 * 60L);
            case 3 -> Instant.now().plusSeconds(15 * 60L);
            default -> Instant.now().plusSeconds(60 * 60L);
        };
    }

    private boolean filterByChannelType(Long channelId, String channelType) {
        if (channelType == null || channelType.isBlank()) {
            return true;
        }
        return notificationChannelRepository.findById(channelId)
                .map(channel -> matches(channel.getChannelType(), channelType))
                .orElse(false);
    }

    private boolean matches(String actual, String expected) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return Optional.ofNullable(actual)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .orElse("")
                .equals(expected.trim().toUpperCase(Locale.ROOT));
    }

    private OutboundDeliveryEntity getEntity(Long id) {
        return outboundDeliveryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到 outbound delivery。"));
    }

    private OutboundEventEnvelope readEnvelope(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, OutboundEventEnvelope.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("解析 outbound envelope 失败。", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化 outbound envelope 失败。", exception);
        }
    }

    private OutboundDeliveryResponse toResponse(OutboundDeliveryEntity entity) {
        return new OutboundDeliveryResponse(
                entity.getId(),
                entity.getEventId(),
                entity.getEventType(),
                entity.getChannelId(),
                entity.getEntityType(),
                entity.getEntityRef(),
                entity.getRequestId(),
                entity.getGatewayResourceKey(),
                entity.getUpstreamObjectId(),
                entity.getDeliveryStatus(),
                entity.getAttemptCount(),
                entity.getNextRetryAt(),
                entity.getLastError(),
                entity.getResponseCode(),
                entity.getResponseSummary(),
                entity.getPayloadJson(),
                entity.getOccurredAt(),
                entity.getDeliveredAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
