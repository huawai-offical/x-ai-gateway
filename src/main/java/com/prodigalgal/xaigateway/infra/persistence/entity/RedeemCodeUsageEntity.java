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

@Entity
@Table(
        name = "redeem_code_usage",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_redeem_usage_code_user", columnNames = {"redeem_code_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_redeem_usage_user_used", columnList = "user_id,used_at"),
                @Index(name = "idx_redeem_usage_campaign_user", columnList = "campaign_id,user_id")
        }
)
@Comment("兑换码使用记录。")
public class RedeemCodeUsageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "redeem_code_id", nullable = false)
    private RedeemCodeEntity redeemCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private PromoCampaignEntity campaign;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private GatewayUserEntity user;

    @Column(name = "used_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public RedeemCodeEntity getRedeemCode() {
        return redeemCode;
    }

    public void setRedeemCode(RedeemCodeEntity redeemCode) {
        this.redeemCode = redeemCode;
    }

    public PromoCampaignEntity getCampaign() {
        return campaign;
    }

    public void setCampaign(PromoCampaignEntity campaign) {
        this.campaign = campaign;
    }

    public GatewayUserEntity getUser() {
        return user;
    }

    public void setUser(GatewayUserEntity user) {
        this.user = user;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
