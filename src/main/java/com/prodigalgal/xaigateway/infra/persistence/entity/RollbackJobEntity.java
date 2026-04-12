package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "rollback_job")
public class RollbackJobEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "upgrade_job_id")
    private Long upgradeJobId;
    @Column(name = "release_artifact_id")
    private Long releaseArtifactId;
    @Column(name = "backup_job_id")
    private Long backupJobId;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "message", length = 1024)
    private String message;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;
    public Long getId() { return id; }
    public Long getUpgradeJobId() { return upgradeJobId; }
    public void setUpgradeJobId(Long upgradeJobId) { this.upgradeJobId = upgradeJobId; }
    public Long getReleaseArtifactId() { return releaseArtifactId; }
    public void setReleaseArtifactId(Long releaseArtifactId) { this.releaseArtifactId = releaseArtifactId; }
    public Long getBackupJobId() { return backupJobId; }
    public void setBackupJobId(Long backupJobId) { this.backupJobId = backupJobId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
