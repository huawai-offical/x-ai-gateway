package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "subscription_plan",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_subscription_plan_name", columnNames = "plan_name")
        },
        indexes = {
                @Index(name = "idx_subscription_plan_is_active", columnList = "is_active")
        }
)
@Comment("用户订阅套餐。")
public class SubscriptionPlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_name", nullable = false, length = 128)
    private String planName;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "default_duration_days", nullable = false)
    private int defaultDurationDays = 30;

    @Column(name = "max_active_keys", nullable = false)
    private int maxActiveKeys = 3;

    @Column(name = "rpm_limit", nullable = false)
    private int rpmLimit = 60;

    @Column(name = "tpm_limit", nullable = false)
    private int tpmLimit = 120000;

    @Column(name = "concurrency_limit", nullable = false)
    private int concurrencyLimit = 2;

    @Column(name = "daily_token_limit", nullable = false)
    private long dailyTokenLimit = 1_000_000L;

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getDefaultDurationDays() {
        return defaultDurationDays;
    }

    public void setDefaultDurationDays(int defaultDurationDays) {
        this.defaultDurationDays = defaultDurationDays;
    }

    public int getMaxActiveKeys() {
        return maxActiveKeys;
    }

    public void setMaxActiveKeys(int maxActiveKeys) {
        this.maxActiveKeys = maxActiveKeys;
    }

    public int getRpmLimit() {
        return rpmLimit;
    }

    public void setRpmLimit(int rpmLimit) {
        this.rpmLimit = rpmLimit;
    }

    public int getTpmLimit() {
        return tpmLimit;
    }

    public void setTpmLimit(int tpmLimit) {
        this.tpmLimit = tpmLimit;
    }

    public int getConcurrencyLimit() {
        return concurrencyLimit;
    }

    public void setConcurrencyLimit(int concurrencyLimit) {
        this.concurrencyLimit = concurrencyLimit;
    }

    public long getDailyTokenLimit() {
        return dailyTokenLimit;
    }

    public void setDailyTokenLimit(long dailyTokenLimit) {
        this.dailyTokenLimit = dailyTokenLimit;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
