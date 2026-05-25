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
        name = "invitation_code_usage",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_invitation_usage_code_user", columnNames = {"invitation_code_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_invitation_usage_code_used", columnList = "invitation_code_id,used_at"),
                @Index(name = "idx_invitation_usage_user_used", columnList = "user_id,used_at")
        }
)
@Comment("注册邀请码使用记录。")
public class InvitationCodeUsageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invitation_code_id", nullable = false)
    private InvitationCodeEntity invitationCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private GatewayUserEntity user;

    @Column(name = "registration_email", nullable = false, length = 191)
    private String registrationEmail;

    @Column(name = "registration_channel", nullable = false, length = 32)
    private String registrationChannel;

    @Column(name = "request_source", length = 128)
    private String requestSource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referrer_user_id")
    private GatewayUserEntity referrerUser;

    @Column(name = "reward_token_credits", nullable = false)
    private long rewardTokenCredits = 0L;

    @Column(name = "referrer_reward_token_credits", nullable = false)
    private long referrerRewardTokenCredits = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reward_plan_id")
    private SubscriptionPlanEntity rewardPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reward_subscription_id")
    private UserSubscriptionEntity rewardSubscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reward_access_group_id")
    private AccessGroupEntity rewardAccessGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reward_access_group_grant_id")
    private UserAccessGroupGrantEntity rewardAccessGroupGrant;

    @Column(name = "used_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public InvitationCodeEntity getInvitationCode() {
        return invitationCode;
    }

    public void setInvitationCode(InvitationCodeEntity invitationCode) {
        this.invitationCode = invitationCode;
    }

    public GatewayUserEntity getUser() {
        return user;
    }

    public void setUser(GatewayUserEntity user) {
        this.user = user;
    }

    public String getRegistrationEmail() {
        return registrationEmail;
    }

    public void setRegistrationEmail(String registrationEmail) {
        this.registrationEmail = registrationEmail;
    }

    public String getRegistrationChannel() {
        return registrationChannel;
    }

    public void setRegistrationChannel(String registrationChannel) {
        this.registrationChannel = registrationChannel;
    }

    public String getRequestSource() {
        return requestSource;
    }

    public void setRequestSource(String requestSource) {
        this.requestSource = requestSource;
    }

    public GatewayUserEntity getReferrerUser() {
        return referrerUser;
    }

    public void setReferrerUser(GatewayUserEntity referrerUser) {
        this.referrerUser = referrerUser;
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

    public UserSubscriptionEntity getRewardSubscription() {
        return rewardSubscription;
    }

    public void setRewardSubscription(UserSubscriptionEntity rewardSubscription) {
        this.rewardSubscription = rewardSubscription;
    }

    public AccessGroupEntity getRewardAccessGroup() {
        return rewardAccessGroup;
    }

    public void setRewardAccessGroup(AccessGroupEntity rewardAccessGroup) {
        this.rewardAccessGroup = rewardAccessGroup;
    }

    public UserAccessGroupGrantEntity getRewardAccessGroupGrant() {
        return rewardAccessGroupGrant;
    }

    public void setRewardAccessGroupGrant(UserAccessGroupGrantEntity rewardAccessGroupGrant) {
        this.rewardAccessGroupGrant = rewardAccessGroupGrant;
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
