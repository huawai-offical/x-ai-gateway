package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "rollback_playbook")
public class RollbackPlaybookEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "change_plan_id", nullable = false)
    private Long changePlanId;
    @Column(name = "recovery_checkpoint_id")
    private Long recoveryCheckpointId;
    @Column(name = "rollback_release_artifact_id")
    private Long rollbackReleaseArtifactId;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "trigger_conditions_json", columnDefinition = "text")
    private String triggerConditionsJson;
    @Column(name = "latest_rollback_plan_id")
    private Long latestRollbackPlanId;
    @Column(name = "last_triggered_at", columnDefinition = "timestamp with time zone")
    private Instant lastTriggeredAt;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public Long getChangePlanId() {
        return changePlanId;
    }

    public void setChangePlanId(Long changePlanId) {
        this.changePlanId = changePlanId;
    }

    public Long getRecoveryCheckpointId() {
        return recoveryCheckpointId;
    }

    public void setRecoveryCheckpointId(Long recoveryCheckpointId) {
        this.recoveryCheckpointId = recoveryCheckpointId;
    }

    public Long getRollbackReleaseArtifactId() {
        return rollbackReleaseArtifactId;
    }

    public void setRollbackReleaseArtifactId(Long rollbackReleaseArtifactId) {
        this.rollbackReleaseArtifactId = rollbackReleaseArtifactId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTriggerConditionsJson() {
        return triggerConditionsJson;
    }

    public void setTriggerConditionsJson(String triggerConditionsJson) {
        this.triggerConditionsJson = triggerConditionsJson;
    }

    public Long getLatestRollbackPlanId() {
        return latestRollbackPlanId;
    }

    public void setLatestRollbackPlanId(Long latestRollbackPlanId) {
        this.latestRollbackPlanId = latestRollbackPlanId;
    }

    public Instant getLastTriggeredAt() {
        return lastTriggeredAt;
    }

    public void setLastTriggeredAt(Instant lastTriggeredAt) {
        this.lastTriggeredAt = lastTriggeredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
