package com.prodigalgal.xaigateway.infra.persistence.entity;

import com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestStatus;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "request_log",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_request_log_request_id", columnNames = "request_id")
        },
        indexes = {
                @Index(name = "idx_request_log_distributed_key_started", columnList = "distributed_key_id,started_at"),
                @Index(name = "idx_request_log_provider_started", columnList = "provider_type,started_at"),
                @Index(name = "idx_request_log_status_started", columnList = "status,started_at")
        }
)
@Comment("请求生命周期日志。")
public class RequestLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "request_id", nullable = false, updatable = false, length = 64)
    private String requestId;

    @Column(name = "distributed_key_id", nullable = false)
    private Long distributedKeyId;

    @Column(name = "distributed_key_prefix", nullable = false, length = 64)
    private String distributedKeyPrefix;

    @Column(name = "protocol", nullable = false, length = 32)
    private String protocol;

    @Column(name = "request_path", nullable = false, length = 256)
    private String requestPath;

    @Column(name = "requested_model", nullable = false, length = 256)
    private String requestedModel;

    @Column(name = "public_model", nullable = false, length = 256)
    private String publicModel;

    @Column(name = "resolved_model_key", nullable = false, length = 256)
    private String resolvedModelKey;

    @Column(name = "model_group", nullable = false, length = 256)
    private String modelGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 32)
    private ProviderType providerType;

    @Column(name = "credential_id", nullable = false)
    private Long credentialId;

    @Column(name = "selection_source", nullable = false, length = 32)
    private String selectionSource;

    @Column(name = "prefix_hash", nullable = false, length = 128)
    private String prefixHash;

    @Column(name = "fingerprint", nullable = false, length = 128)
    private String fingerprint;

    @Column(name = "is_stream", nullable = false)
    private boolean stream;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private GatewayRequestStatus status;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "started_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant startedAt;

    @Column(name = "completed_at", columnDefinition = "timestamp with time zone")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Long getDistributedKeyId() {
        return distributedKeyId;
    }

    public void setDistributedKeyId(Long distributedKeyId) {
        this.distributedKeyId = distributedKeyId;
    }

    public String getDistributedKeyPrefix() {
        return distributedKeyPrefix;
    }

    public void setDistributedKeyPrefix(String distributedKeyPrefix) {
        this.distributedKeyPrefix = distributedKeyPrefix;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    public String getRequestedModel() {
        return requestedModel;
    }

    public void setRequestedModel(String requestedModel) {
        this.requestedModel = requestedModel;
    }

    public String getPublicModel() {
        return publicModel;
    }

    public void setPublicModel(String publicModel) {
        this.publicModel = publicModel;
    }

    public String getResolvedModelKey() {
        return resolvedModelKey;
    }

    public void setResolvedModelKey(String resolvedModelKey) {
        this.resolvedModelKey = resolvedModelKey;
    }

    public String getModelGroup() {
        return modelGroup;
    }

    public void setModelGroup(String modelGroup) {
        this.modelGroup = modelGroup;
    }

    public ProviderType getProviderType() {
        return providerType;
    }

    public void setProviderType(ProviderType providerType) {
        this.providerType = providerType;
    }

    public Long getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(Long credentialId) {
        this.credentialId = credentialId;
    }

    public String getSelectionSource() {
        return selectionSource;
    }

    public void setSelectionSource(String selectionSource) {
        this.selectionSource = selectionSource;
    }

    public String getPrefixHash() {
        return prefixHash;
    }

    public void setPrefixHash(String prefixHash) {
        this.prefixHash = prefixHash;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public boolean isStream() {
        return stream;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }

    public GatewayRequestStatus getStatus() {
        return status;
    }

    public void setStatus(GatewayRequestStatus status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
