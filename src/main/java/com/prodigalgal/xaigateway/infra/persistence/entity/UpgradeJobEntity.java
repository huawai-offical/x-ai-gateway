package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "upgrade_job")
public class UpgradeJobEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "target_release_artifact_id", nullable = false)
    private Long targetReleaseArtifactId;
    @Column(name = "pre_backup_job_id")
    private Long preBackupJobId;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "message", length = 1024)
    private String message;
    @Column(name = "auto_rollback_triggered", nullable = false)
    private boolean autoRollbackTriggered;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;
    public Long getId() { return id; }
    public Long getTargetReleaseArtifactId() { return targetReleaseArtifactId; }
    public void setTargetReleaseArtifactId(Long targetReleaseArtifactId) { this.targetReleaseArtifactId = targetReleaseArtifactId; }
    public Long getPreBackupJobId() { return preBackupJobId; }
    public void setPreBackupJobId(Long preBackupJobId) { this.preBackupJobId = preBackupJobId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public boolean isAutoRollbackTriggered() { return autoRollbackTriggered; }
    public void setAutoRollbackTriggered(boolean autoRollbackTriggered) { this.autoRollbackTriggered = autoRollbackTriggered; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
