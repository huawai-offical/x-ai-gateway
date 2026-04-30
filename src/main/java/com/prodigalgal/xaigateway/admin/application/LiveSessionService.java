package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.LiveSessionCreateRequest;
import com.prodigalgal.xaigateway.admin.api.LiveSessionEventRequest;
import com.prodigalgal.xaigateway.admin.api.LiveSessionEventResponse;
import com.prodigalgal.xaigateway.admin.api.LiveSessionResponse;
import com.prodigalgal.xaigateway.admin.api.LiveSessionRuntimeEventRequest;
import com.prodigalgal.xaigateway.infra.persistence.entity.LiveSessionEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.LiveSessionEventEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.LiveSessionEventRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.LiveSessionRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class LiveSessionService {

    private static final long DEFAULT_TTL_SECONDS = 1_800;

    private final LiveSessionRepository liveSessionRepository;
    private final LiveSessionEventRepository liveSessionEventRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, LiveSessionRuntimeAdapter> runtimeAdapters;

    public LiveSessionService(
            LiveSessionRepository liveSessionRepository,
            LiveSessionEventRepository liveSessionEventRepository,
            ObjectMapper objectMapper,
            List<LiveSessionRuntimeAdapter> runtimeAdapters) {
        this.liveSessionRepository = liveSessionRepository;
        this.liveSessionEventRepository = liveSessionEventRepository;
        this.objectMapper = objectMapper;
        this.runtimeAdapters = runtimeAdapters.stream()
                .collect(Collectors.toMap(adapter -> adapter.protocol().toLowerCase(Locale.ROOT), Function.identity(), (left, right) -> left));
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
        entity.setStatus("CREATED");
        entity.setMetadataJson(mergeMetadata(defaultString(request.metadataJson(), "{}"), Map.of(
                "runtimeState", "CREATED",
                "eventFlow", "session-created"
        )));
        entity.setExpiresAt(Instant.now().plusSeconds(request.ttlSeconds() == null ? DEFAULT_TTL_SECONDS : Math.max(60, request.ttlSeconds())));
        return toResponse(liveSessionRepository.save(entity));
    }

    public LiveSessionResponse connect(String sessionKey) {
        LiveSessionEntity session = getRequired(sessionKey);
        ensureNotClosed(session);
        LiveSessionRuntimeAdapter adapter = adapterFor(session);
        Instant now = Instant.now();
        session.setStatus("CONNECTING");
        session.setMetadataJson(mergeMetadata(session.getMetadataJson(), Map.of(
                "runtimeState", "CONNECTING",
                "connectingAt", now.toString()
        )));
        LiveSessionRuntimeConnectResult result = adapter.connect(toRuntimeRequest(session, now));
        session.setStatus("CONNECTED");
        session.setMetadataJson(mergeMetadata(session.getMetadataJson(), mergeRuntimeMetadata(result.runtimeMetadata(), Map.of(
                "runtimeState", "CONNECTED",
                "connectedAt", now.toString(),
                "adapter", result.adapterName(),
                "upstreamResumeHandle", result.upstreamResumeHandle()
        ))));
        liveSessionRepository.save(session);
        appendProviderEvents(session, result.providerEvents());
        return toResponse(session);
    }

    public LiveSessionResponse heartbeat(String sessionKey) {
        LiveSessionEntity session = getRequired(sessionKey);
        ensureNotClosed(session);
        Instant now = Instant.now();
        LiveSessionRuntimeExchangeResult result = adapterFor(session).heartbeat(toRuntimeRequest(session, now));
        session.setStatus("CONNECTED");
        session.setMetadataJson(mergeMetadata(session.getMetadataJson(), mergeRuntimeMetadata(result.runtimeMetadata(), Map.of(
                "runtimeState", "CONNECTED",
                "lastHeartbeatAt", now.toString(),
                "adapter", result.adapterName()
        ))));
        liveSessionRepository.save(session);
        appendProviderEvents(session, result.providerEvents());
        return toResponse(session);
    }

    public LiveSessionResponse sendRuntimeEvent(String sessionKey, LiveSessionRuntimeEventRequest request) {
        LiveSessionEntity session = getRequired(sessionKey);
        ensureNotClosed(session);
        Instant now = Instant.now();
        long audioBytes = request.audioBytes() == null ? 0L : Math.max(0L, request.audioBytes());
        String eventType = defaultString(request.eventType(), "message");
        String payloadJson = defaultString(request.payloadJson(), "{}");
        appendEventEntity(session, eventType, "INPUT", payloadJson, audioBytes);

        LiveSessionRuntimeExchangeResult result = adapterFor(session).send(
                toRuntimeRequest(session, now),
                new LiveSessionRuntimeMessage(eventType, payloadJson, audioBytes)
        );
        session.setStatus("STREAMING");
        session.setMetadataJson(mergeMetadata(session.getMetadataJson(), mergeRuntimeMetadata(result.runtimeMetadata(), Map.of(
                "runtimeState", "STREAMING",
                "lastRuntimeEventAt", now.toString(),
                "adapter", result.adapterName()
        ))));
        liveSessionRepository.save(session);
        appendProviderEvents(session, result.providerEvents());
        return toResponse(session);
    }

    public LiveSessionResponse resume(String resumeToken) {
        LiveSessionEntity session = liveSessionRepository.findByResumeToken(resumeToken)
                .orElseThrow(() -> new IllegalArgumentException("未找到可恢复的 Live Session。"));
        ensureNotClosed(session);
        Instant now = Instant.now();
        session.setStatus("CONNECTED");
        session.setMetadataJson(mergeMetadata(session.getMetadataJson(), Map.of(
                "runtimeState", "RESUMED",
                "resumedAt", now.toString()
        )));
        liveSessionRepository.save(session);
        appendProviderEvents(session, List.of(new LiveSessionRuntimeProviderEvent(
                "runtime.resumed",
                "{\"resumeToken\":\"" + resumeToken + "\"}",
                0L
        )));
        return toResponse(session);
    }

    public LiveSessionResponse close(String sessionKey) {
        LiveSessionEntity session = getRequired(sessionKey);
        if (session.getClosedAt() != null) {
            return toResponse(session);
        }
        Instant now = Instant.now();
        LiveSessionRuntimeExchangeResult result = adapterFor(session).close(toRuntimeRequest(session, now));
        session.setStatus("CLOSED");
        session.setClosedAt(now);
        session.setMetadataJson(mergeMetadata(session.getMetadataJson(), mergeRuntimeMetadata(result.runtimeMetadata(), Map.of(
                "runtimeState", "CLOSED",
                "closedAt", now.toString(),
                "adapter", result.adapterName()
        ))));
        liveSessionRepository.save(session);
        appendProviderEvents(session, result.providerEvents());
        return toResponse(session);
    }

    public LiveSessionEventResponse appendEvent(String sessionKey, LiveSessionEventRequest request) {
        LiveSessionEntity session = getRequired(sessionKey);
        long audioBytes = request.audioBytes() == null ? 0L : Math.max(0L, request.audioBytes());
        String direction = normalizeDirection(request.direction());
        return toEventResponse(appendEventEntity(session, defaultString(request.eventType(), "message"), direction, defaultString(request.payloadJson(), "{}"), audioBytes));
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

    private LiveSessionRuntimeAdapter adapterFor(LiveSessionEntity session) {
        LiveSessionRuntimeAdapter adapter = runtimeAdapters.get(session.getProtocol().toLowerCase(Locale.ROOT));
        if (adapter == null) {
            throw new IllegalStateException("未配置 Live Session runtime adapter：" + session.getProtocol());
        }
        return adapter;
    }

    private LiveSessionRuntimeRequest toRuntimeRequest(LiveSessionEntity session, Instant now) {
        return new LiveSessionRuntimeRequest(
                session.getSessionKey(),
                session.getModelName(),
                session.getProtocol(),
                session.getResumeToken(),
                session.getMetadataJson(),
                now
        );
    }

    private void ensureNotClosed(LiveSessionEntity session) {
        if (session.getClosedAt() != null || "CLOSED".equalsIgnoreCase(session.getStatus())) {
            throw new IllegalStateException("Live Session 已关闭。");
        }
    }

    private void appendProviderEvents(LiveSessionEntity session, List<LiveSessionRuntimeProviderEvent> providerEvents) {
        if (providerEvents == null || providerEvents.isEmpty()) {
            return;
        }
        for (LiveSessionRuntimeProviderEvent event : providerEvents) {
            appendEventEntity(session, event.eventType(), "OUTPUT", defaultString(event.payloadJson(), "{}"), Math.max(0L, event.audioBytes()));
        }
    }

    private LiveSessionEventEntity appendEventEntity(
            LiveSessionEntity session,
            String eventType,
            String direction,
            String payloadJson,
            long audioBytes) {
        long eventId = session.getLastEventId() + 1;

        LiveSessionEventEntity event = new LiveSessionEventEntity();
        event.setSession(session);
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setDirection(direction);
        event.setPayloadJson(payloadJson);
        event.setAudioBytes(audioBytes);

        session.setLastEventId(eventId);
        session.setEventCount(session.getEventCount() + 1);
        if ("INPUT".equals(direction)) {
            session.setInputAudioBytes(session.getInputAudioBytes() + audioBytes);
        } else if ("OUTPUT".equals(direction)) {
            session.setOutputAudioBytes(session.getOutputAudioBytes() + audioBytes);
        }
        liveSessionRepository.save(session);
        return liveSessionEventRepository.save(event);
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

    private Map<String, String> mergeRuntimeMetadata(Map<String, String> primary, Map<String, String> fallback) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (fallback != null) {
            merged.putAll(fallback);
        }
        if (primary != null) {
            merged.putAll(primary);
        }
        return merged;
    }

    private String mergeMetadata(String metadataJson, Map<String, String> additions) {
        Map<String, Object> merged = new LinkedHashMap<>();
        try {
            JsonNode root = objectMapper.readTree(defaultString(metadataJson, "{}"));
            if (root != null && root.isObject()) {
                root.properties().forEach(entry -> merged.put(entry.getKey(), entry.getValue().isValueNode() ? entry.getValue().asText() : entry.getValue().toString()));
            }
        } catch (Exception ignored) {
            merged.put("rawMetadata", defaultString(metadataJson, "{}"));
        }
        if (additions != null) {
            merged.putAll(additions);
        }
        try {
            return objectMapper.writeValueAsString(merged);
        } catch (Exception ignored) {
            return "{}";
        }
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
