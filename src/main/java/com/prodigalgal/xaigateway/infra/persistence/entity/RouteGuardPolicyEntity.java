package com.prodigalgal.xaigateway.infra.persistence.entity;

import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceActionType;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernancePolicyMode;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceTargetType;
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
@Table(name = "route_guard_policy")
public class RouteGuardPolicyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_name", nullable = false, length = 128)
    private String policyName;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_mode", nullable = false, length = 32)
    private GovernancePolicyMode policyMode = GovernancePolicyMode.ENFORCE;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 32)
    private GovernanceActionType actionType = GovernanceActionType.NONE;

    @Column(name = "ttl_seconds")
    private Integer ttlSeconds;

    @Column(name = "priority", nullable = false)
    private int priority = 100;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "retry_policy", columnDefinition = "text")
    private String retryPolicy;

    @Column(name = "fallback_policy", columnDefinition = "text")
    private String fallbackPolicy;

    @Column(name = "circuit_breaker_policy", columnDefinition = "text")
    private String circuitBreakerPolicy;

    @Column(name = "rate_limit_policy", columnDefinition = "text")
    private String rateLimitPolicy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
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

    public GovernancePolicyMode getPolicyMode() {
        return policyMode;
    }

    public void setPolicyMode(GovernancePolicyMode policyMode) {
        this.policyMode = policyMode;
    }

    public GovernanceActionType getActionType() {
        return actionType;
    }

    public void setActionType(GovernanceActionType actionType) {
        this.actionType = actionType;
    }

    public Integer getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(Integer ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRetryPolicy() {
        return retryPolicy;
    }

    public void setRetryPolicy(String retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    public String getFallbackPolicy() {
        return fallbackPolicy;
    }

    public void setFallbackPolicy(String fallbackPolicy) {
        this.fallbackPolicy = fallbackPolicy;
    }

    public String getCircuitBreakerPolicy() {
        return circuitBreakerPolicy;
    }

    public void setCircuitBreakerPolicy(String circuitBreakerPolicy) {
        this.circuitBreakerPolicy = circuitBreakerPolicy;
    }

    public String getRateLimitPolicy() {
        return rateLimitPolicy;
    }

    public void setRateLimitPolicy(String rateLimitPolicy) {
        this.rateLimitPolicy = rateLimitPolicy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
