package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "installation_state")
public class InstallationStateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "active_release_artifact_id")
    private Long activeReleaseArtifactId;
    @Column(name = "bootstrap_completed", nullable = false)
    private boolean bootstrapCompleted;
    @Column(name = "last_health_check_at", columnDefinition = "timestamp with time zone")
    private Instant lastHealthCheckAt;
    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;
    public Long getId() { return id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getActiveReleaseArtifactId() { return activeReleaseArtifactId; }
    public void setActiveReleaseArtifactId(Long activeReleaseArtifactId) { this.activeReleaseArtifactId = activeReleaseArtifactId; }
    public boolean isBootstrapCompleted() { return bootstrapCompleted; }
    public void setBootstrapCompleted(boolean bootstrapCompleted) { this.bootstrapCompleted = bootstrapCompleted; }
    public Instant getLastHealthCheckAt() { return lastHealthCheckAt; }
    public void setLastHealthCheckAt(Instant lastHealthCheckAt) { this.lastHealthCheckAt = lastHealthCheckAt; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
