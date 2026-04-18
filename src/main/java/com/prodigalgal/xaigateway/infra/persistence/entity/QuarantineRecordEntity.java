package com.prodigalgal.xaigateway.infra.persistence.entity;

import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceActionType;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceRecoveryMode;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceTargetType;
import com.prodigalgal.xaigateway.gateway.core.governance.QuarantineStatus;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "quarantine_record")
public class QuarantineRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private GovernanceTargetType targetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", length = 32)
    private ProviderType providerType;

    @Column(name = "site_profile_id")
    private Long siteProfileId;

    @Column(name = "credential_id")
    private Long credentialId;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "proxy_id")
    private Long proxyId;

    @Column(name = "source_rule_id")
    private Long sourceRuleId;

    @Column(name = "source_event_id")
    private Long sourceEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 32)
    private GovernanceActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "recovery_mode", nullable = false, length = 32)
    private GovernanceRecoveryMode recoveryMode = GovernanceRecoveryMode.AUTO_RESUME;

    @Column(name = "reason", nullable = false, length = 1024)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private QuarantineStatus status = QuarantineStatus.ACTIVE;

    @Column(name = "started_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant startedAt;

    @Column(name = "expires_at", columnDefinition = "timestamp with time zone")
    private Instant expiresAt;

    @Column(name = "released_at", columnDefinition = "timestamp with time zone")
    private Instant releasedAt;

    @Column(name = "release_reason", length = 512)
    private String releaseReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public GovernanceTargetType getTargetType() {
        return targetType;
    }

    public void setTargetType(GovernanceTargetType targetType) {
        this.targetType = targetType;
    }

    public ProviderType getProviderType() {
        return providerType;
    }

    public void setProviderType(ProviderType providerType) {
        this.providerType = providerType;
    }

    public Long getSiteProfileId() {
        return siteProfileId;
    }

    public void setSiteProfileId(Long siteProfileId) {
        this.siteProfileId = siteProfileId;
    }

    public Long getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(Long credentialId) {
        this.credentialId = credentialId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getProxyId() {
        return proxyId;
    }

    public void setProxyId(Long proxyId) {
        this.proxyId = proxyId;
    }

    public Long getSourceRuleId() {
        return sourceRuleId;
    }

    public void setSourceRuleId(Long sourceRuleId) {
        this.sourceRuleId = sourceRuleId;
    }

    public Long getSourceEventId() {
        return sourceEventId;
    }

    public void setSourceEventId(Long sourceEventId) {
        this.sourceEventId = sourceEventId;
    }

    public GovernanceActionType getActionType() {
        return actionType;
    }

    public void setActionType(GovernanceActionType actionType) {
        this.actionType = actionType;
    }

    public GovernanceRecoveryMode getRecoveryMode() {
        return recoveryMode;
    }

    public void setRecoveryMode(GovernanceRecoveryMode recoveryMode) {
        this.recoveryMode = recoveryMode;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public QuarantineStatus getStatus() {
        return status;
    }

    public void setStatus(QuarantineStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(Instant releasedAt) {
        this.releasedAt = releasedAt;
    }

    public String getReleaseReason() {
        return releaseReason;
    }

    public void setReleaseReason(String releaseReason) {
        this.releaseReason = releaseReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
