package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "plan_access_group",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_plan_access_group", columnNames = {"plan_id", "access_group_id"})
        },
        indexes = {
                @Index(name = "idx_plan_access_group_plan", columnList = "plan_id"),
                @Index(name = "idx_plan_access_group_group", columnList = "access_group_id"),
                @Index(name = "idx_plan_access_group_active", columnList = "is_active")
        }
)
@Comment("套餐与访问组的绑定关系。")
public class PlanAccessGroupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlanEntity plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "access_group_id", nullable = false)
    private AccessGroupEntity accessGroup;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "priority", nullable = false)
    private int priority = 100;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public SubscriptionPlanEntity getPlan() {
        return plan;
    }

    public void setPlan(SubscriptionPlanEntity plan) {
        this.plan = plan;
    }

    public AccessGroupEntity getAccessGroup() {
        return accessGroup;
    }

    public void setAccessGroup(AccessGroupEntity accessGroup) {
        this.accessGroup = accessGroup;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
