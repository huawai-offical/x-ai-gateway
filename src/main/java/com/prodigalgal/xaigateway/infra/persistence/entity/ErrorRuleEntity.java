package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "error_rule")
public class ErrorRuleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
    @Column(name = "priority", nullable = false)
    private int priority = 100;
    @Column(name = "provider_type", length = 32)
    private String providerType;
    @Column(name = "protocol", length = 32)
    private String protocol;
    @Column(name = "model_pattern", length = 128)
    private String modelPattern;
    @Column(name = "request_path", length = 256)
    private String requestPath;
    @Column(name = "http_status")
    private Integer httpStatus;
    @Column(name = "error_code", length = 128)
    private String errorCode;
    @Column(name = "match_scope", nullable = false, length = 16)
    private String matchScope;
    @Column(name = "action", nullable = false, length = 16)
    private String action;
    @Column(name = "rewrite_status")
    private Integer rewriteStatus;
    @Column(name = "rewrite_code", length = 128)
    private String rewriteCode;
    @Column(name = "rewrite_message", length = 1024)
    private String rewriteMessage;
    @Column(name = "downgrade_policy", length = 32)
    private String downgradePolicy;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;
    public Long getId() { return id; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public String getProviderType() { return providerType; }
    public void setProviderType(String providerType) { this.providerType = providerType; }
    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }
    public String getModelPattern() { return modelPattern; }
    public void setModelPattern(String modelPattern) { this.modelPattern = modelPattern; }
    public String getRequestPath() { return requestPath; }
    public void setRequestPath(String requestPath) { this.requestPath = requestPath; }
    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getMatchScope() { return matchScope; }
    public void setMatchScope(String matchScope) { this.matchScope = matchScope; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Integer getRewriteStatus() { return rewriteStatus; }
    public void setRewriteStatus(Integer rewriteStatus) { this.rewriteStatus = rewriteStatus; }
    public String getRewriteCode() { return rewriteCode; }
    public void setRewriteCode(String rewriteCode) { this.rewriteCode = rewriteCode; }
    public String getRewriteMessage() { return rewriteMessage; }
    public void setRewriteMessage(String rewriteMessage) { this.rewriteMessage = rewriteMessage; }
    public String getDowngradePolicy() { return downgradePolicy; }
    public void setDowngradePolicy(String downgradePolicy) { this.downgradePolicy = downgradePolicy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
