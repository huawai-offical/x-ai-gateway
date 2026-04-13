package com.prodigalgal.xaigateway.infra.persistence.entity;

import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageCompleteness;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageSource;
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
        name = "usage_record",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_usage_record_request_id", columnNames = "request_id")
        },
        indexes = {
                @Index(name = "idx_usage_record_distributed_key_created", columnList = "distributed_key_id,created_at"),
                @Index(name = "idx_usage_record_provider_created", columnList = "provider_type,created_at"),
                @Index(name = "idx_usage_record_completeness_created", columnList = "completeness,created_at")
        }
)
@Comment("归一化 usage 记录。")
public class UsageRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "request_id", nullable = false, updatable = false, length = 64)
    private String requestId;

    @Column(name = "distributed_key_id", nullable = false)
    private Long distributedKeyId;

    @Column(name = "protocol", nullable = false, length = 32)
    private String protocol;

    @Column(name = "request_path", nullable = false, length = 256)
    private String requestPath;

    @Column(name = "model_group", nullable = false, length = 256)
    private String modelGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 32)
    private ProviderType providerType;

    @Column(name = "credential_id", nullable = false)
    private Long credentialId;

    @Column(name = "is_stream", nullable = false)
    private boolean stream;

    @Enumerated(EnumType.STRING)
    @Column(name = "completeness", nullable = false, length = 16)
    private GatewayUsageCompleteness completeness;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_source", nullable = false, length = 32)
    private GatewayUsageSource usageSource;

    @Column(name = "raw_prompt_tokens", nullable = false)
    private int rawPromptTokens;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;

    @Column(name = "reasoning_tokens", nullable = false)
    private int reasoningTokens;

    @Column(name = "cache_hit_tokens", nullable = false)
    private int cacheHitTokens;

    @Column(name = "cache_write_tokens", nullable = false)
    private int cacheWriteTokens;

    @Column(name = "upstream_cache_hit_tokens", nullable = false)
    private int upstreamCacheHitTokens;

    @Column(name = "upstream_cache_write_tokens", nullable = false)
    private int upstreamCacheWriteTokens;

    @Column(name = "saved_input_tokens", nullable = false)
    private int savedInputTokens;

    @Column(name = "cached_content_ref", length = 512)
    private String cachedContentRef;

    @Column(name = "total_tokens", nullable = false)
    private int totalTokens;

    @Column(name = "native_usage_payload_json", columnDefinition = "text")
    private String nativeUsagePayloadJson;

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

    public boolean isStream() {
        return stream;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }

    public GatewayUsageCompleteness getCompleteness() {
        return completeness;
    }

    public void setCompleteness(GatewayUsageCompleteness completeness) {
        this.completeness = completeness;
    }

    public GatewayUsageSource getUsageSource() {
        return usageSource;
    }

    public void setUsageSource(GatewayUsageSource usageSource) {
        this.usageSource = usageSource;
    }

    public int getRawPromptTokens() {
        return rawPromptTokens;
    }

    public void setRawPromptTokens(int rawPromptTokens) {
        this.rawPromptTokens = rawPromptTokens;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }

    public int getReasoningTokens() {
        return reasoningTokens;
    }

    public void setReasoningTokens(int reasoningTokens) {
        this.reasoningTokens = reasoningTokens;
    }

    public int getCacheHitTokens() {
        return cacheHitTokens;
    }

    public void setCacheHitTokens(int cacheHitTokens) {
        this.cacheHitTokens = cacheHitTokens;
    }

    public int getCacheWriteTokens() {
        return cacheWriteTokens;
    }

    public void setCacheWriteTokens(int cacheWriteTokens) {
        this.cacheWriteTokens = cacheWriteTokens;
    }

    public int getUpstreamCacheHitTokens() {
        return upstreamCacheHitTokens;
    }

    public void setUpstreamCacheHitTokens(int upstreamCacheHitTokens) {
        this.upstreamCacheHitTokens = upstreamCacheHitTokens;
    }

    public int getUpstreamCacheWriteTokens() {
        return upstreamCacheWriteTokens;
    }

    public void setUpstreamCacheWriteTokens(int upstreamCacheWriteTokens) {
        this.upstreamCacheWriteTokens = upstreamCacheWriteTokens;
    }

    public int getSavedInputTokens() {
        return savedInputTokens;
    }

    public void setSavedInputTokens(int savedInputTokens) {
        this.savedInputTokens = savedInputTokens;
    }

    public String getCachedContentRef() {
        return cachedContentRef;
    }

    public void setCachedContentRef(String cachedContentRef) {
        this.cachedContentRef = cachedContentRef;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    public String getNativeUsagePayloadJson() {
        return nativeUsagePayloadJson;
    }

    public void setNativeUsagePayloadJson(String nativeUsagePayloadJson) {
        this.nativeUsagePayloadJson = nativeUsagePayloadJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
