package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "maintenance_run")
public class MaintenanceRunEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "run_type", nullable = false, length = 64)
    private String runType;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;
    @Column(name = "confirm_required", nullable = false)
    private boolean confirmRequired;
    @Column(name = "confirmed", nullable = false)
    private boolean confirmed;
    @Column(name = "artifact_path", length = 1024)
    private String artifactPath;
    @Column(name = "artifact_checksum", length = 128)
    private String artifactChecksum;
    @Column(name = "actor", length = 128)
    private String actor;
    @Column(name = "source_ref", length = 256)
    private String sourceRef;
    @Column(name = "duration_ms", nullable = false)
    private long durationMs;
    @Column(name = "detail_json", columnDefinition = "text")
    private String detailJson;
    @Column(name = "error_message", length = 1024)
    private String errorMessage;
    @Column(name = "started_at", columnDefinition = "timestamp with time zone")
    private Instant startedAt;
    @Column(name = "completed_at", columnDefinition = "timestamp with time zone")
    private Instant completedAt;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() { return id; }
    public String getRunType() { return runType; }
    public void setRunType(String runType) { this.runType = runType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
    public boolean isConfirmRequired() { return confirmRequired; }
    public void setConfirmRequired(boolean confirmRequired) { this.confirmRequired = confirmRequired; }
    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
    public String getArtifactPath() { return artifactPath; }
    public void setArtifactPath(String artifactPath) { this.artifactPath = artifactPath; }
    public String getArtifactChecksum() { return artifactChecksum; }
    public void setArtifactChecksum(String artifactChecksum) { this.artifactChecksum = artifactChecksum; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
