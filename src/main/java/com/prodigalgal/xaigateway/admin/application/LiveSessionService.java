package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.LiveSessionCreateRequest;
import com.prodigalgal.xaigateway.admin.api.LiveSessionConformanceResponse;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final LiveSessionConnectionPool connectionPool;

    @Autowired
    public LiveSessionService(
            LiveSessionRepository liveSessionRepository,
            LiveSessionEventRepository liveSessionEventRepository,
            ObjectMapper objectMapper,
            List<LiveSessionRuntimeAdapter> runtimeAdapters) {
        this(liveSessionRepository, liveSessionEventRepository, objectMapper, runtimeAdapters, new LiveSessionConnectionPool());
    }

    public LiveSessionService(
            LiveSessionRepository liveSessionRepository,
            LiveSessionEventRepository liveSessionEventRepository,
            ObjectMapper objectMapper,
            List<LiveSessionRuntimeAdapter> runtimeAdapters,
            LiveSessionConnectionPool connectionPool) {
        this.liveSessionRepository = liveSessionRepository;
        this.liveSessionEventRepository = liveSessionEventRepository;
        this.objectMapper = objectMapper;
        this.runtimeAdapters = runtimeAdapters.stream()
                .collect(Collectors.toMap(adapter -> adapter.protocol().toLowerCase(Locale.ROOT), Function.identity(), (left, right) -> left));
        this.connectionPool = connectionPool == null ? new LiveSessionConnectionPool() : connectionPool;
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
        LiveSessionConnectionPool.Lease lease = connectionPool.acquire(tenantKey(session), session.getSessionKey(), session.getProtocol());
        LiveSessionRuntimeConnectResult result;
        try {
            result = adapter.connect(toRuntimeRequest(session, now));
        } catch (RuntimeException exception) {
            connectionPool.release(session.getSessionKey());
            throw exception;
        }
        session.setStatus("CONNECTED");
        session.setMetadataJson(mergeMetadata(session.getMetadataJson(), mergeRuntimeMetadata(result.runtimeMetadata(), Map.of(
                "runtimeState", "CONNECTED",
                "connectedAt", now.toString(),
                "adapter", result.adapterName(),
                "transport", adapter.transport(),
                "upstreamResumeHandle", result.upstreamResumeHandle()
        ))));
        session.setMetadataJson(mergeMetadata(session.getMetadataJson(), connectionPoolMetadata(lease)));
        liveSessionRepository.save(session);
        appendProviderEvents(session, result.providerEvents());
        return toResponse(session);
    }

    public LiveSessionResponse heartbeat(String sessionKey) {
        LiveSessionEntity session = getRequired(sessionKey);
        ensureNotClosed(session);
        Instant now = Instant.now();
        LiveSessionRuntimeAdapter adapter = adapterFor(session);
        LiveSessionRuntimeExchangeResult result = adapter.heartbeat(toRuntimeRequest(session, now));
        LiveSessionConnectionPool.Lease lease = connectionPool.touch(session.getSessionKey());
        session.setStatus("CONNECTED");
        session.setMetadataJson(mergeMetadata(session.getMetadataJson(), mergeRuntimeMetadata(result.runtimeMetadata(), Map.of(
                "runtimeState", "CONNECTED",
                "lastHeartbeatAt", now.toString(),
                "adapter", result.adapterName(),
                "transport", adapter.transport()
        ))));
        session.setMetadataJson(mergeMetadata(session.getMetadataJson(), connectionPoolMetadata(lease)));
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

        LiveSessionRuntimeAdapter adapter = adapterFor(session);
        LiveSessionRuntimeExchangeResult result = adapter.send(
                toRuntimeRequest(session, now),
                new LiveSessionRuntimeMessage(eventType, payloadJson, audioBytes)
        );
        LiveSessionConnectionPool.Lease lease = connectionPool.touch(session.getSessionKey());
        session.setStatus("STREAMING");
        session.setMetadataJson(mergeMetadata(session.getMetadataJson(), mergeRuntimeMetadata(result.runtimeMetadata(), Map.of(
                "runtimeState", "STREAMING",
                "lastRuntimeEventAt", now.toString(),
                "lastGatewayEventCategory", classifyRuntimeEvent(eventType),
                "lastInputAudioBytes", String.valueOf(audioBytes),
                "usageInputAudioBytes", String.valueOf(session.getInputAudioBytes()),
                "binaryFrameObserved", String.valueOf(audioBytes > 0),
                "adapter", result.adapterName(),
                "transport", adapter.transport()
        ))));
        session.setMetadataJson(mergeMetadata(session.getMetadataJson(), connectionPoolMetadata(lease)));
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
        LiveSessionRuntimeAdapter adapter = adapterFor(session);
        LiveSessionRuntimeExchangeResult result;
        try {
            result = adapter.close(toRuntimeRequest(session, now));
        } finally {
            connectionPool.release(session.getSessionKey());
        }
        session.setStatus("CLOSED");
        session.setClosedAt(now);
        session.setMetadataJson(mergeMetadata(session.getMetadataJson(), mergeRuntimeMetadata(result.runtimeMetadata(), Map.of(
                "runtimeState", "CLOSED",
                "closedAt", now.toString(),
                "closeReason", "client_closed",
                "cancelSemantic", "gateway_close_as_client_cancel",
                "adapter", result.adapterName(),
                "transport", adapter.transport()
        ))));
        session.setMetadataJson(mergeMetadata(session.getMetadataJson(), Map.of(
                "connectionPoolState", "RELEASED",
                "connectionPoolActive", String.valueOf(connectionPool.activeCount())
        )));
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

    @Transactional(readOnly = true)
    public LiveSessionConformanceResponse conformance(String sessionKey) {
        LiveSessionEntity session = getRequired(sessionKey);
        List<LiveSessionEventEntity> events = liveSessionEventRepository.findAllBySession_IdOrderByEventIdAsc(session.getId());
        long inputEvents = events.stream().filter(event -> "INPUT".equalsIgnoreCase(event.getDirection())).count();
        long outputEvents = events.stream().filter(event -> "OUTPUT".equalsIgnoreCase(event.getDirection())).count();
        boolean connected = contains(session.getStatus(), "CONNECTED")
                || contains(session.getStatus(), "STREAMING")
                || contains(session.getStatus(), "CLOSED")
                || contains(session.getMetadataJson(), "CONNECTED")
                || events.stream().anyMatch(event -> "runtime.connected".equalsIgnoreCase(event.getEventType()));
        boolean streaming = "STREAMING".equalsIgnoreCase(session.getStatus())
                || inputEvents > 0 && outputEvents > 0
                || events.stream().anyMatch(event -> event.getEventType() != null && event.getEventType().startsWith("provider."));
        boolean closed = session.getClosedAt() != null || "CLOSED".equalsIgnoreCase(session.getStatus());
        boolean sseReplayAvailable = !events.isEmpty() && replaySse(sessionKey, 0L).contains("data:");
        String transport = readMetadataValue(session.getMetadataJson(), "transport");
        boolean websocketTransport = "websocket".equalsIgnoreCase(transport);
        boolean websocketFrames = events.stream().anyMatch(event -> event.getEventType() != null && event.getEventType().startsWith("websocket."));
        boolean binaryAudioFrames = events.stream().anyMatch(event -> event.getEventType() != null
                && event.getEventType().startsWith("websocket.frame.")
                && event.getAudioBytes() > 0);
        boolean normalizedProviderErrors = events.stream().anyMatch(event -> "websocket.error".equalsIgnoreCase(event.getEventType())
                || contains(event.getPayloadJson(), "normalizedProviderErrorCode"))
                || contains(session.getMetadataJson(), "normalizedProviderErrorCode");
        boolean retryObserved = events.stream().anyMatch(event -> "websocket.retry".equalsIgnoreCase(event.getEventType()))
                || contains(session.getMetadataJson(), "retryAfterMs");

        List<String> checks = new ArrayList<>();
        addCheck(checks, connected, "runtime connected");
        addCheck(checks, streaming, "bidirectional event flow");
        addCheck(checks, sseReplayAvailable, "sse replay available");
        addCheck(checks, session.getInputAudioBytes() >= 0 && session.getOutputAudioBytes() >= 0, "audio byte counters available");
        if (websocketTransport) {
            addCheck(checks, websocketFrames, "websocket frames available");
            addCheck(checks, binaryAudioFrames, "binary audio frames accounted");
            addCheck(checks, normalizedProviderErrors, "provider errors normalized");
            addCheck(checks, retryObserved, "retry semantics available");
        }

        List<String> warnings = new ArrayList<>();
        if (!closed) {
            warnings.add("session not closed");
        }
        if (outputEvents == 0) {
            warnings.add("no provider output events");
        }
        String conformanceStatus = checks.size() >= 4 && warnings.isEmpty()
                ? "PASS"
                : checks.size() >= 3 ? "WARN" : "FAIL";

        return new LiveSessionConformanceResponse(
                session.getSessionKey(),
                session.getProtocol(),
                session.getStatus(),
                connected,
                streaming,
                closed,
                sseReplayAvailable,
                inputEvents,
                outputEvents,
                events.size(),
                session.getInputAudioBytes(),
                session.getOutputAudioBytes(),
                transport,
                conformanceStatus,
                List.copyOf(checks),
                List.copyOf(warnings)
        );
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

    private String tenantKey(LiveSessionEntity session) {
        Long distributedKeyId = session.getDistributedKeyId();
        return distributedKeyId == null ? "tenant:anonymous" : "distributed_key:" + distributedKeyId;
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

    private String classifyRuntimeEvent(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return "message";
        }
        String normalized = eventType.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("audio.")) {
            return "audio";
        }
        if (normalized.contains("timeout")) {
            return "timeout";
        }
        if (normalized.contains("retry")) {
            return "retry";
        }
        if (normalized.startsWith("error")) {
            return "error";
        }
        return "message";
    }

    private boolean contains(String value, String expected) {
        return value != null && expected != null && value.toUpperCase(Locale.ROOT).contains(expected.toUpperCase(Locale.ROOT));
    }

    private void addCheck(List<String> checks, boolean passed, String name) {
        if (passed) {
            checks.add(name);
        }
    }

    private String readMetadataValue(String metadataJson, String fieldName) {
        try {
            JsonNode root = objectMapper.readTree(defaultString(metadataJson, "{}"));
            JsonNode value = root == null ? null : root.path(fieldName);
            return value == null || value.isMissingNode() || value.isNull() ? null : value.asText(null);
        } catch (Exception ignored) {
            return null;
        }
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

    private Map<String, String> connectionPoolMetadata(LiveSessionConnectionPool.Lease lease) {
        Map<String, String> metadata = new LinkedHashMap<>();
        if (lease != null) {
            metadata.put("connectionPoolLeaseId", lease.leaseId());
            metadata.put("connectionPoolTenant", lease.tenantKey());
            metadata.put("connectionPoolState", lease.state());
            metadata.put("connectionPoolExpiresAt", lease.expiresAt().toString());
        }
        metadata.put("connectionPoolActive", String.valueOf(connectionPool.activeCount()));
        metadata.put("connectionPoolMaxPerTenant", String.valueOf(connectionPool.maxConnectionsPerTenant()));
        return metadata;
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
