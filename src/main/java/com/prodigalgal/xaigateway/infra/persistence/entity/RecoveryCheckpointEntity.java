package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "recovery_checkpoint")
public class RecoveryCheckpointEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "checkpoint_name", nullable = false, length = 128)
    private String checkpointName;
    @Column(name = "change_plan_id")
    private Long changePlanId;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "metadata_snapshot_path", length = 1024)
    private String metadataSnapshotPath;
    @Column(name = "runtime_snapshot_path", length = 1024)
    private String runtimeSnapshotPath;
    @Column(name = "data_snapshot_path", length = 1024)
    private String dataSnapshotPath;
    @Column(name = "manifest_json", columnDefinition = "text")
    private String manifestJson;
    @Column(name = "verification_status", length = 32)
    private String verificationStatus;
    @Column(name = "verification_message", length = 512)
    private String verificationMessage;
    @Column(name = "verified_at", columnDefinition = "timestamp with time zone")
    private Instant verifiedAt;
    @Column(name = "verified_by", length = 128)
    private String verifiedBy;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getCheckpointName() {
        return checkpointName;
    }

    public void setCheckpointName(String checkpointName) {
        this.checkpointName = checkpointName;
    }

    public Long getChangePlanId() {
        return changePlanId;
    }

    public void setChangePlanId(Long changePlanId) {
        this.changePlanId = changePlanId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMetadataSnapshotPath() {
        return metadataSnapshotPath;
    }

    public void setMetadataSnapshotPath(String metadataSnapshotPath) {
        this.metadataSnapshotPath = metadataSnapshotPath;
    }

    public String getRuntimeSnapshotPath() {
        return runtimeSnapshotPath;
    }

    public void setRuntimeSnapshotPath(String runtimeSnapshotPath) {
        this.runtimeSnapshotPath = runtimeSnapshotPath;
    }

    public String getDataSnapshotPath() {
        return dataSnapshotPath;
    }

    public void setDataSnapshotPath(String dataSnapshotPath) {
        this.dataSnapshotPath = dataSnapshotPath;
    }

    public String getManifestJson() {
        return manifestJson;
    }

    public void setManifestJson(String manifestJson) {
        this.manifestJson = manifestJson;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public String getVerificationMessage() {
        return verificationMessage;
    }

    public void setVerificationMessage(String verificationMessage) {
        this.verificationMessage = verificationMessage;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(String verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
