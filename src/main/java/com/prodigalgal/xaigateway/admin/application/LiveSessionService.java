package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.LiveSessionCreateRequest;
import com.prodigalgal.xaigateway.admin.api.LiveSessionEventRequest;
import com.prodigalgal.xaigateway.admin.api.LiveSessionEventResponse;
import com.prodigalgal.xaigateway.admin.api.LiveSessionResponse;
import com.prodigalgal.xaigateway.infra.persistence.entity.LiveSessionEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.LiveSessionEventEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.LiveSessionEventRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.LiveSessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LiveSessionService {

    private static final long DEFAULT_TTL_SECONDS = 1_800;

    private final LiveSessionRepository liveSessionRepository;
    private final LiveSessionEventRepository liveSessionEventRepository;

    public LiveSessionService(
            LiveSessionRepository liveSessionRepository,
            LiveSessionEventRepository liveSessionEventRepository) {
        this.liveSessionRepository = liveSessionRepository;
        this.liveSessionEventRepository = liveSessionEventRepository;
    }

    @Transactional(readOnly = true)
    public List<LiveSessionResponse> list() {
        return liveSessionRepository.findTop100ByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public LiveSessionResponse get(String sessionKey) {
        return toResponse(getRequired(sessionKey));
    }

    public LiveSessionResponse create(LiveSessionCreateRequest request) {
        LiveSessionEntity entity = new LiveSessionEntity();
        entity.setSessionKey("live_" + UUID.randomUUID().toString().replace("-", ""));
        entity.setResumeToken("resume_" + UUID.randomUUID().toString().replace("-", ""));
        entity.setDistributedKeyId(request.distributedKeyId());
        entity.setModelName(defaultString(request.model(), "gpt-4o-realtime-preview"));
        entity.setProtocol(defaultString(request.protocol(), "openai_realtime"));
        entity.setStatus("OPEN");
        entity.setMetadataJson(defaultString(request.metadataJson(), "{}"));
        entity.setExpiresAt(Instant.now().plusSeconds(request.ttlSeconds() == null ? DEFAULT_TTL_SECONDS : Math.max(60, request.ttlSeconds())));
        return toResponse(liveSessionRepository.save(entity));
    }

    public LiveSessionEventResponse appendEvent(String sessionKey, LiveSessionEventRequest request) {
        LiveSessionEntity session = getRequired(sessionKey);
        long eventId = session.getLastEventId() + 1;
        long audioBytes = request.audioBytes() == null ? 0L : Math.max(0L, request.audioBytes());
        String direction = normalizeDirection(request.direction());

        LiveSessionEventEntity event = new LiveSessionEventEntity();
        event.setSession(session);
        event.setEventId(eventId);
        event.setEventType(defaultString(request.eventType(), "message"));
        event.setDirection(direction);
        event.setPayloadJson(defaultString(request.payloadJson(), "{}"));
        event.setAudioBytes(audioBytes);

        session.setLastEventId(eventId);
        session.setEventCount(session.getEventCount() + 1);
        if ("INPUT".equals(direction)) {
            session.setInputAudioBytes(session.getInputAudioBytes() + audioBytes);
        } else if ("OUTPUT".equals(direction)) {
            session.setOutputAudioBytes(session.getOutputAudioBytes() + audioBytes);
        }
        liveSessionRepository.save(session);
        return toEventResponse(liveSessionEventRepository.save(event));
    }

    @Transactional(readOnly = true)
    public List<LiveSessionEventResponse> listEvents(String sessionKey, Long afterEventId) {
        LiveSessionEntity session = getRequired(sessionKey);
        long cursor = afterEventId == null ? 0L : Math.max(0L, afterEventId);
        return liveSessionEventRepository
                .findAllBySession_IdAndEventIdGreaterThanOrderByEventIdAsc(session.getId(), cursor)
                .stream()
                .map(this::toEventResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public String replaySse(String sessionKey, Long afterEventId) {
        StringBuilder builder = new StringBuilder();
        for (LiveSessionEventResponse event : listEvents(sessionKey, afterEventId)) {
            builder.append("id: ").append(event.eventId()).append('\n');
            builder.append("event: ").append(event.eventType()).append('\n');
            builder.append("data: ").append(event.payloadJson() == null || event.payloadJson().isBlank() ? "{}" : event.payloadJson()).append("\n\n");
        }
        return builder.toString();
    }

    private LiveSessionEntity getRequired(String sessionKey) {
        return liveSessionRepository.findBySessionKey(sessionKey)
                .orElseThrow(() -> new IllegalArgumentException("未找到 Live Session。"));
    }

    private String normalizeDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return "INPUT";
        }
        String value = direction.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "INPUT", "OUTPUT", "SYSTEM" -> value;
            default -> "SYSTEM";
        };
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private LiveSessionResponse toResponse(LiveSessionEntity entity) {
        return new LiveSessionResponse(
                entity.getId(),
                entity.getSessionKey(),
                entity.getDistributedKeyId(),
                entity.getModelName(),
                entity.getProtocol(),
                entity.getStatus(),
                entity.getResumeToken(),
                entity.getLastEventId(),
                entity.getInputAudioBytes(),
                entity.getOutputAudioBytes(),
                entity.getEventCount(),
                entity.getMetadataJson(),
                entity.getExpiresAt(),
                entity.getClosedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private LiveSessionEventResponse toEventResponse(LiveSessionEventEntity entity) {
        return new LiveSessionEventResponse(
                entity.getId(),
                entity.getSession().getSessionKey(),
                entity.getEventId(),
                entity.getEventType(),
                entity.getDirection(),
                entity.getPayloadJson(),
                entity.getAudioBytes(),
                entity.getCreatedAt()
        );
    }
}
