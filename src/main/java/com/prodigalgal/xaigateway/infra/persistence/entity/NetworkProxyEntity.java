package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "network_proxy")
public class NetworkProxyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "proxy_name", nullable = false, length = 128)
    private String proxyName;
    @Column(name = "proxy_url", nullable = false, length = 512)
    private String proxyUrl;
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
    @Column(name = "last_status", length = 32)
    private String lastStatus;
    @Column(name = "last_latency_ms")
    private Long lastLatencyMs;
    @Column(name = "last_error_message", length = 512)
    private String lastErrorMessage;
    @Column(name = "last_probed_at", columnDefinition = "timestamp with time zone")
    private Instant lastProbedAt;
    @Column(name = "description", length = 512)
    private String description;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() { return id; }
    public String getProxyName() { return proxyName; }
    public void setProxyName(String proxyName) { this.proxyName = proxyName; }
    public String getProxyUrl() { return proxyUrl; }
    public void setProxyUrl(String proxyUrl) { this.proxyUrl = proxyUrl; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getLastStatus() { return lastStatus; }
    public void setLastStatus(String lastStatus) { this.lastStatus = lastStatus; }
    public Long getLastLatencyMs() { return lastLatencyMs; }
    public void setLastLatencyMs(Long lastLatencyMs) { this.lastLatencyMs = lastLatencyMs; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }
    public Instant getLastProbedAt() { return lastProbedAt; }
    public void setLastProbedAt(Instant lastProbedAt) { this.lastProbedAt = lastProbedAt; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
