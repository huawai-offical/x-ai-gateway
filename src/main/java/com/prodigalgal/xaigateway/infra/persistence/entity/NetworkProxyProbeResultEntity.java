package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "network_proxy_probe_result")
public class NetworkProxyProbeResultEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "proxy_id", nullable = false)
    private Long proxyId;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "latency_ms")
    private Long latencyMs;
    @Column(name = "target_host", length = 256)
    private String targetHost;
    @Column(name = "error_message", length = 512)
    private String errorMessage;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    public Long getId() { return id; }
    public Long getProxyId() { return proxyId; }
    public void setProxyId(Long proxyId) { this.proxyId = proxyId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public String getTargetHost() { return targetHost; }
    public void setTargetHost(String targetHost) { this.targetHost = targetHost; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
}
