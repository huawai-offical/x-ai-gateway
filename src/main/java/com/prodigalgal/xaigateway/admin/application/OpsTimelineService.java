package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.OpsProbeRunRequest;
import com.prodigalgal.xaigateway.admin.api.OpsProbeRunResponse;
import com.prodigalgal.xaigateway.admin.api.OpsSystemEventResponse;
import com.prodigalgal.xaigateway.infra.persistence.entity.OpsProbeRunEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.OpsSystemEventEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.OpsProbeRunRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.OpsSystemEventRepository;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.Socket;
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
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

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
        ProbeResult probeResult = runProbe(defaultString(request.targetUrl(), "http://localhost:8080/actuator/health"), request.forceFailure());
        Instant completedAt = Instant.now();

        OpsProbeRunEntity entity = new OpsProbeRunEntity();
        entity.setProbeName(defaultString(request.probeName(), "manual-probe"));
        entity.setTargetUrl(probeResult.targetUrl());
        entity.setSource(defaultString(request.source(), "console"));
        entity.setStatus(probeResult.status());
        entity.setSeverity(probeResult.severity());
        entity.setLatencyMs(Duration.between(startedAt, completedAt).toMillis());
        entity.setStatusCode(probeResult.statusCode());
        entity.setErrorMessage(probeResult.errorMessage());
        entity.setDetailJson(defaultString(request.detailJson(), writeJson(Map.of(
                "mode", "manual",
                "target", entity.getTargetUrl(),
                "probeType", probeResult.probeType(),
                "status", probeResult.status(),
                "statusCode", probeResult.statusCode(),
                "error", probeResult.errorMessage() == null ? "" : probeResult.errorMessage()
        ))));
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

    private ProbeResult runProbe(String rawTargetUrl, Boolean forceFailure) {
        String targetUrl = defaultString(rawTargetUrl, "http://localhost:8080/actuator/health");
        if (Boolean.TRUE.equals(forceFailure)) {
            return new ProbeResult(targetUrl, "forced", "FAILED", "ERROR", 599, "调用方强制失败，用于验证告警链路。");
        }
        try {
            URI uri = URI.create(targetUrl);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ("http".equals(scheme) || "https".equals(scheme)) {
                return runHttpProbe(uri);
            }
            if ("tcp".equals(scheme)) {
                return runTcpProbe(uri);
            }
            return new ProbeResult(targetUrl, "unknown", "FAILED", "ERROR", 400, "不支持的拨测协议：" + scheme);
        } catch (IllegalArgumentException exception) {
            return new ProbeResult(targetUrl, "parse", "FAILED", "ERROR", 400, "拨测目标 URL 不合法：" + exception.getMessage());
        }
    }

    private ProbeResult runHttpProbe(URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(5))
                    .method("GET", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int statusCode = response.statusCode();
            boolean ok = statusCode >= 200 && statusCode < 500;
            return new ProbeResult(
                    uri.toString(),
                    "http",
                    ok ? "SUCCEEDED" : "FAILED",
                    ok ? "INFO" : "ERROR",
                    statusCode,
                    ok ? null : "HTTP 拨测返回不可用状态码：" + statusCode
            );
        } catch (IOException exception) {
            return new ProbeResult(uri.toString(), "http", "FAILED", "ERROR", 599, exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ProbeResult(uri.toString(), "http", "FAILED", "ERROR", 599, "HTTP 拨测被中断。");
        }
    }

    private ProbeResult runTcpProbe(URI uri) {
        String host = uri.getHost();
        int port = uri.getPort();
        if (host == null || host.isBlank() || port <= 0) {
            return new ProbeResult(uri.toString(), "tcp", "FAILED", "ERROR", 400, "TCP 拨测必须使用 tcp://host:port。");
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            return new ProbeResult(uri.toString(), "tcp", "SUCCEEDED", "INFO", 200, null);
        } catch (IOException exception) {
            return new ProbeResult(uri.toString(), "tcp", "FAILED", "ERROR", 599, exception.getMessage());
        }
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

    private record ProbeResult(
            String targetUrl,
            String probeType,
            String status,
            String severity,
            Integer statusCode,
            String errorMessage) {
    }
}
