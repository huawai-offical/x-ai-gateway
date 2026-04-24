package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "ops_probe_run")
public class OpsProbeRunEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "probe_name", nullable = false, length = 128)
    private String probeName;
    @Column(name = "target_url", nullable = false, length = 1024)
    private String targetUrl;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "severity", nullable = false, length = 32)
    private String severity;
    @Column(name = "source", nullable = false, length = 96)
    private String source;
    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;
    @Column(name = "status_code")
    private Integer statusCode;
    @Column(name = "error_message", length = 1024)
    private String errorMessage;
    @Column(name = "detail_json", columnDefinition = "text")
    private String detailJson;
    @Column(name = "started_at", columnDefinition = "timestamp with time zone")
    private Instant startedAt;
    @Column(name = "completed_at", columnDefinition = "timestamp with time zone")
    private Instant completedAt;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    public Long getId() { return id; }
    public String getProbeName() { return probeName; }
    public void setProbeName(String probeName) { this.probeName = probeName; }
    public String getTargetUrl() { return targetUrl; }
    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    public Integer getStatusCode() { return statusCode; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
