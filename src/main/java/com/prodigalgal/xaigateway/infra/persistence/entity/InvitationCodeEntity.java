package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.FetchType;
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
        name = "invitation_code",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_invitation_code_code", columnNames = "code")
        },
        indexes = {
                @Index(name = "idx_invitation_code_active", columnList = "is_active"),
                @Index(name = "idx_invitation_code_created_at", columnList = "created_at"),
                @Index(name = "idx_invitation_code_owner_user", columnList = "owner_user_id")
        }
)
@Comment("注册邀请码。")
public class InvitationCodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 128)
    private String code;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "max_uses", nullable = false)
    private int maxUses = 1;

    @Column(name = "used_count", nullable = false)
    private int usedCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private GatewayUserEntity ownerUser;

    @Column(name = "reward_token_credits", nullable = false)
    private long rewardTokenCredits = 0L;

    @Column(name = "referrer_reward_token_credits", nullable = false)
    private long referrerRewardTokenCredits = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reward_plan_id")
    private SubscriptionPlanEntity rewardPlan;

    @Column(name = "reward_plan_duration_days")
    private Integer rewardPlanDurationDays;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reward_access_group_id")
    private AccessGroupEntity rewardAccessGroup;

    @Column(name = "reward_access_group_duration_days")
    private Integer rewardAccessGroupDurationDays;

    @Column(name = "expires_at", columnDefinition = "timestamp with time zone")
    private Instant expiresAt;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getMaxUses() {
        return maxUses;
    }

    public void setMaxUses(int maxUses) {
        this.maxUses = maxUses;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public void setUsedCount(int usedCount) {
        this.usedCount = usedCount;
    }

    public GatewayUserEntity getOwnerUser() {
        return ownerUser;
    }

    public void setOwnerUser(GatewayUserEntity ownerUser) {
        this.ownerUser = ownerUser;
    }

    public long getRewardTokenCredits() {
        return rewardTokenCredits;
    }

    public void setRewardTokenCredits(long rewardTokenCredits) {
        this.rewardTokenCredits = rewardTokenCredits;
    }

    public long getReferrerRewardTokenCredits() {
        return referrerRewardTokenCredits;
    }

    public void setReferrerRewardTokenCredits(long referrerRewardTokenCredits) {
        this.referrerRewardTokenCredits = referrerRewardTokenCredits;
    }

    public SubscriptionPlanEntity getRewardPlan() {
        return rewardPlan;
    }

    public void setRewardPlan(SubscriptionPlanEntity rewardPlan) {
        this.rewardPlan = rewardPlan;
    }

    public Integer getRewardPlanDurationDays() {
        return rewardPlanDurationDays;
    }

    public void setRewardPlanDurationDays(Integer rewardPlanDurationDays) {
        this.rewardPlanDurationDays = rewardPlanDurationDays;
    }

    public AccessGroupEntity getRewardAccessGroup() {
        return rewardAccessGroup;
    }

    public void setRewardAccessGroup(AccessGroupEntity rewardAccessGroup) {
        this.rewardAccessGroup = rewardAccessGroup;
    }

    public Integer getRewardAccessGroupDurationDays() {
        return rewardAccessGroupDurationDays;
    }

    public void setRewardAccessGroupDurationDays(Integer rewardAccessGroupDurationDays) {
        this.rewardAccessGroupDurationDays = rewardAccessGroupDurationDays;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
