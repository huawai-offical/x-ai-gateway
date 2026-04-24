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
        name = "promo_campaign",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_promo_campaign_name", columnNames = "campaign_name")
        },
        indexes = {
                @Index(name = "idx_promo_campaign_active", columnList = "is_active")
        }
)
@Comment("促销兑换活动。")
public class PromoCampaignEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_name", nullable = false, length = 128)
    private String campaignName;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "reward_token_credits", nullable = false)
    private long rewardTokenCredits = 0L;

    @Column(name = "max_redemptions_per_user", nullable = false)
    private int maxRedemptionsPerUser = 1;

    @Column(name = "starts_at", columnDefinition = "timestamp with time zone")
    private Instant startsAt;

    @Column(name = "expires_at", columnDefinition = "timestamp with time zone")
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getCampaignName() {
        return campaignName;
    }

    public void setCampaignName(String campaignName) {
        this.campaignName = campaignName;
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

    public long getRewardTokenCredits() {
        return rewardTokenCredits;
    }

    public void setRewardTokenCredits(long rewardTokenCredits) {
        this.rewardTokenCredits = rewardTokenCredits;
    }

    public int getMaxRedemptionsPerUser() {
        return maxRedemptionsPerUser;
    }

    public void setMaxRedemptionsPerUser(int maxRedemptionsPerUser) {
        this.maxRedemptionsPerUser = maxRedemptionsPerUser;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
