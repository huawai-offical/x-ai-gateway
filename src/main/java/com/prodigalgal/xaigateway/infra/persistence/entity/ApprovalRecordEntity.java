package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "approval_record")
public class ApprovalRecordEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "change_plan_id", nullable = false)
    private Long changePlanId;
    @Column(name = "decision", nullable = false, length = 32)
    private String decision;
    @Column(name = "actor", nullable = false, length = 128)
    private String actor;
    @Column(name = "reason", length = 512)
    private String reason;
    @Column(name = "decision_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant decisionAt;
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

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getDecisionAt() {
        return decisionAt;
    }

    public void setDecisionAt(Instant decisionAt) {
        this.decisionAt = decisionAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
