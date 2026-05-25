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
        name = "invitation_relationship",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_invitation_relationship_invited_user", columnNames = "invited_user_id")
        },
        indexes = {
                @Index(name = "idx_invitation_relationship_referrer", columnList = "referrer_user_id,created_at"),
                @Index(name = "idx_invitation_relationship_code", columnList = "invitation_code_id")
        }
)
@Comment("用户邀请关系。")
public class InvitationRelationshipEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invitation_code_id", nullable = false)
    private InvitationCodeEntity invitationCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitation_usage_id")
    private InvitationCodeUsageEntity invitationUsage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referrer_user_id", nullable = false)
    private GatewayUserEntity referrerUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_user_id", nullable = false)
    private GatewayUserEntity invitedUser;

    @Column(name = "depth", nullable = false)
    private int depth = 1;

    @Column(name = "path", length = 1024)
    private String path;

    @Column(name = "source_channel", length = 32)
    private String sourceChannel;

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

    public InvitationCodeUsageEntity getInvitationUsage() {
        return invitationUsage;
    }

    public void setInvitationUsage(InvitationCodeUsageEntity invitationUsage) {
        this.invitationUsage = invitationUsage;
    }

    public GatewayUserEntity getReferrerUser() {
        return referrerUser;
    }

    public void setReferrerUser(GatewayUserEntity referrerUser) {
        this.referrerUser = referrerUser;
    }

    public GatewayUserEntity getInvitedUser() {
        return invitedUser;
    }

    public void setInvitedUser(GatewayUserEntity invitedUser) {
        this.invitedUser = invitedUser;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getSourceChannel() {
        return sourceChannel;
    }

    public void setSourceChannel(String sourceChannel) {
        this.sourceChannel = sourceChannel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
