package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "slo_policy",
        indexes = {
                @Index(name = "idx_slo_policy_enabled", columnList = "enabled"),
                @Index(name = "idx_slo_policy_scope", columnList = "scope_type,scope_ref")
        }
)
public class SloPolicyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_name", nullable = false, length = 128)
    private String policyName;

    @Column(name = "scope_type", nullable = false, length = 32)
    private String scopeType;

    @Column(name = "scope_ref", length = 128)
    private String scopeRef;

    @Column(name = "window_minutes", nullable = false)
    private Integer windowMinutes = 60;

    @Column(name = "error_budget_ratio", nullable = false, precision = 10, scale = 4)
    private BigDecimal errorBudgetRatio;

    @Column(name = "warning_burn_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal warningBurnRate;

    @Column(name = "critical_burn_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal criticalBurnRate;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "description", length = 512)
    private String description;

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

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public String getScopeRef() {
        return scopeRef;
    }

    public void setScopeRef(String scopeRef) {
        this.scopeRef = scopeRef;
    }

    public Integer getWindowMinutes() {
        return windowMinutes;
    }

    public void setWindowMinutes(Integer windowMinutes) {
        this.windowMinutes = windowMinutes;
    }

    public BigDecimal getErrorBudgetRatio() {
        return errorBudgetRatio;
    }

    public void setErrorBudgetRatio(BigDecimal errorBudgetRatio) {
        this.errorBudgetRatio = errorBudgetRatio;
    }

    public BigDecimal getWarningBurnRate() {
        return warningBurnRate;
    }

    public void setWarningBurnRate(BigDecimal warningBurnRate) {
        this.warningBurnRate = warningBurnRate;
    }

    public BigDecimal getCriticalBurnRate() {
        return criticalBurnRate;
    }

    public void setCriticalBurnRate(BigDecimal criticalBurnRate) {
        this.criticalBurnRate = criticalBurnRate;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
