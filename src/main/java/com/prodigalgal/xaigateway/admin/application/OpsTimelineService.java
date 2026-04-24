package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.OpsProbeRunRequest;
import com.prodigalgal.xaigateway.admin.api.OpsProbeRunResponse;
import com.prodigalgal.xaigateway.admin.api.OpsSystemEventResponse;
import com.prodigalgal.xaigateway.infra.persistence.entity.OpsProbeRunEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.OpsSystemEventEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.OpsProbeRunRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.OpsSystemEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class OpsTimelineService {

    private final OpsProbeRunRepository probeRunRepository;
    private final OpsSystemEventRepository systemEventRepository;
    private final ObjectMapper objectMapper;

    public OpsTimelineService(
            OpsProbeRunRepository probeRunRepository,
            OpsSystemEventRepository systemEventRepository,
            ObjectMapper objectMapper) {
        this.probeRunRepository = probeRunRepository;
        this.systemEventRepository = systemEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<OpsProbeRunResponse> listProbeRuns() {
        return probeRunRepository.findTop100ByOrderByCreatedAtDesc().stream().map(this::toProbeRunResponse).toList();
    }

    public OpsProbeRunResponse createProbeRun(OpsProbeRunRequest request) {
        Instant startedAt = Instant.now();
        boolean failed = Boolean.TRUE.equals(request.forceFailure());
        Instant completedAt = Instant.now().plusMillis(failed ? 23 : 7);

        OpsProbeRunEntity entity = new OpsProbeRunEntity();
        entity.setProbeName(defaultString(request.probeName(), "manual-probe"));
        entity.setTargetUrl(defaultString(request.targetUrl(), "https://gateway.local/health"));
        entity.setSource(defaultString(request.source(), "console"));
        entity.setStatus(failed ? "FAILED" : "SUCCEEDED");
        entity.setSeverity(failed ? "ERROR" : "INFO");
        entity.setLatencyMs(Duration.between(startedAt, completedAt).toMillis());
        entity.setStatusCode(failed ? 503 : 200);
        entity.setErrorMessage(failed ? "模拟拨测失败" : null);
        entity.setDetailJson(defaultString(request.detailJson(), writeJson(Map.of("mode", "manual", "target", entity.getTargetUrl()))));
        entity.setStartedAt(startedAt);
        entity.setCompletedAt(completedAt);
        OpsProbeRunEntity saved = probeRunRepository.save(entity);

        recordEvent(
                "OPS_PROBE_RUN",
                saved.getSeverity(),
                saved.getSource(),
                "ops_probe_run",
                String.valueOf(saved.getId()),
                "拨测运行：" + saved.getProbeName(),
                saved.getDetailJson(),
                saved.getCompletedAt()
        );
        return toProbeRunResponse(saved);
    }

    public OpsSystemEventResponse recordEvent(
            String eventType,
            String severity,
            String source,
            String entityType,
            String entityRef,
            String title,
            String detailJson,
            Instant occurredAt) {
        OpsSystemEventEntity event = new OpsSystemEventEntity();
        event.setEventType(defaultString(eventType, "SYSTEM_EVENT"));
        event.setSeverity(normalizeSeverity(severity));
        event.setSource(defaultString(source, "system"));
        event.setEntityType(entityType);
        event.setEntityRef(entityRef);
        event.setTitle(defaultString(title, event.getEventType()));
        event.setDetailJson(defaultString(detailJson, "{}"));
        event.setOccurredAt(occurredAt == null ? Instant.now() : occurredAt);
        return toSystemEventResponse(systemEventRepository.save(event));
    }

    @Transactional(readOnly = true)
    public List<OpsSystemEventResponse> listEvents(String severity, String source, Instant from, Instant to) {
        String normalizedSeverity = severity == null || severity.isBlank() ? null : normalizeSeverity(severity);
        String normalizedSource = source == null || source.isBlank() ? null : source.trim();
        return systemEventRepository.findTop500ByOrderByOccurredAtDesc().stream()
                .filter(event -> normalizedSeverity == null || normalizedSeverity.equals(event.getSeverity()))
                .filter(event -> normalizedSource == null || normalizedSource.equals(event.getSource()))
                .filter(event -> from == null || !event.getOccurredAt().isBefore(from))
                .filter(event -> to == null || !event.getOccurredAt().isAfter(to))
                .map(this::toSystemEventResponse)
                .toList();
    }

    private String normalizeSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return "INFO";
        }
        String value = severity.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "INFO", "WARNING", "ERROR", "CRITICAL" -> value;
            default -> "INFO";
        };
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("系统事件详情序列化失败。", exception);
        }
    }

    private OpsProbeRunResponse toProbeRunResponse(OpsProbeRunEntity entity) {
        return new OpsProbeRunResponse(
                entity.getId(),
                entity.getProbeName(),
                entity.getTargetUrl(),
                entity.getStatus(),
                entity.getSeverity(),
                entity.getSource(),
                entity.getLatencyMs(),
                entity.getStatusCode(),
                entity.getErrorMessage(),
                entity.getDetailJson(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getCreatedAt()
        );
    }

    private OpsSystemEventResponse toSystemEventResponse(OpsSystemEventEntity entity) {
        return new OpsSystemEventResponse(
                entity.getId(),
                entity.getEventType(),
                entity.getSeverity(),
                entity.getSource(),
                entity.getEntityType(),
                entity.getEntityRef(),
                entity.getTitle(),
                entity.getDetailJson(),
                entity.getOccurredAt(),
                entity.getCreatedAt()
        );
    }
}
