package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "change_plan")
public class ChangePlanEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "plan_name", nullable = false, length = 128)
    private String planName;
    @Column(name = "plan_type", nullable = false, length = 32)
    private String planType;
    @Column(name = "execution_class", nullable = false, length = 32)
    private String executionClass;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "release_artifact_id")
    private Long releaseArtifactId;
    @Column(name = "recovery_checkpoint_id")
    private Long recoveryCheckpointId;
    @Column(name = "maintenance_window_id")
    private Long maintenanceWindowId;
    @Column(name = "rollback_playbook_id")
    private Long rollbackPlaybookId;
    @Column(name = "requested_by", length = 128)
    private String requestedBy;
    @Column(name = "approved_by", length = 128)
    private String approvedBy;
    @Column(name = "manual_override", nullable = false)
    private boolean manualOverride;
    @Column(name = "override_reason", length = 512)
    private String overrideReason;
    @Column(name = "emergency_reason", length = 512)
    private String emergencyReason;
    @Column(name = "risk_level", length = 32)
    private String riskLevel;
    @Column(name = "current_stage", length = 64)
    private String currentStage;
    @Column(name = "current_message", length = 512)
    private String currentMessage;
    @Column(name = "preflight_json", columnDefinition = "text")
    private String preflightJson;
    @Column(name = "summary_json", columnDefinition = "text")
    private String summaryJson;
    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public String getPlanType() {
        return planType;
    }

    public void setPlanType(String planType) {
        this.planType = planType;
    }

    public String getExecutionClass() {
        return executionClass;
    }

    public void setExecutionClass(String executionClass) {
        this.executionClass = executionClass;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getReleaseArtifactId() {
        return releaseArtifactId;
    }

    public void setReleaseArtifactId(Long releaseArtifactId) {
        this.releaseArtifactId = releaseArtifactId;
    }

    public Long getRecoveryCheckpointId() {
        return recoveryCheckpointId;
    }

    public void setRecoveryCheckpointId(Long recoveryCheckpointId) {
        this.recoveryCheckpointId = recoveryCheckpointId;
    }

    public Long getMaintenanceWindowId() {
        return maintenanceWindowId;
    }

    public void setMaintenanceWindowId(Long maintenanceWindowId) {
        this.maintenanceWindowId = maintenanceWindowId;
    }

    public Long getRollbackPlaybookId() {
        return rollbackPlaybookId;
    }

    public void setRollbackPlaybookId(Long rollbackPlaybookId) {
        this.rollbackPlaybookId = rollbackPlaybookId;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public boolean isManualOverride() {
        return manualOverride;
    }

    public void setManualOverride(boolean manualOverride) {
        this.manualOverride = manualOverride;
    }

    public String getOverrideReason() {
        return overrideReason;
    }

    public void setOverrideReason(String overrideReason) {
        this.overrideReason = overrideReason;
    }

    public String getEmergencyReason() {
        return emergencyReason;
    }

    public void setEmergencyReason(String emergencyReason) {
        this.emergencyReason = emergencyReason;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(String currentStage) {
        this.currentStage = currentStage;
    }

    public String getCurrentMessage() {
        return currentMessage;
    }

    public void setCurrentMessage(String currentMessage) {
        this.currentMessage = currentMessage;
    }

    public String getPreflightJson() {
        return preflightJson;
    }

    public void setPreflightJson(String preflightJson) {
        this.preflightJson = preflightJson;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public void setSummaryJson(String summaryJson) {
        this.summaryJson = summaryJson;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
