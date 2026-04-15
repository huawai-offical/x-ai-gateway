package com.prodigalgal.xaigateway.infra.persistence.entity;

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
import java.time.Instant;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
        name = "cache_hit_log",
        indexes = {
                @Index(name = "idx_cache_hit_log_distributed_key_created", columnList = "distributed_key_id,created_at"),
                @Index(name = "idx_cache_hit_log_provider_created", columnList = "provider_type,created_at"),
                @Index(name = "idx_cache_hit_log_model_group_created", columnList = "model_group,created_at")
        }
)
@Comment("缓存命中日志，记录一次请求中的 cache hit / cache write 收益。")
public class CacheHitLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    @Comment("主键。")
    private Long id;

    @Column(name = "request_id", nullable = false, length = 64)
    @Comment("请求标识。")
    private String requestId;

    @Column(name = "distributed_key_id", nullable = false)
    @Comment("分发 key 主键。")
    private Long distributedKeyId;

    @Column(name = "protocol", nullable = false, length = 32)
    @Comment("请求协议。")
    private String protocol;

    @Column(name = "request_path", length = 256)
    @Comment("请求路径。")
    private String requestPath;

    @Column(name = "resource_type", length = 32)
    @Comment("资源类型。")
    private String resourceType;

    @Column(name = "operation", length = 64)
    @Comment("操作类型。")
    private String operation;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 32)
    @Comment("厂商类型。")
    private ProviderType providerType;

    @Column(name = "credential_id", nullable = false)
    @Comment("上游凭证主键。")
    private Long credentialId;

    @Column(name = "model_group", nullable = false, length = 256)
    @Comment("模型分组。")
    private String modelGroup;

    @Column(name = "prefix_hash", nullable = false, length = 128)
    @Comment("前缀哈希。")
    private String prefixHash;

    @Column(name = "fingerprint", nullable = false, length = 128)
    @Comment("请求指纹。")
    private String fingerprint;

    @Column(name = "cache_kind", nullable = false, length = 64)
    @Comment("缓存种类，例如 prompt_cache、cached_content。")
    private String cacheKind;

    @Column(name = "execution_backend", length = 32)
    @Comment("执行后端。")
    private String executionBackend;

    @Column(name = "object_mode", length = 64)
    @Comment("对象模式。")
    private String objectMode;

    @Column(name = "cache_hit_tokens", nullable = false)
    @Comment("缓存命中的 token 数。")
    private int cacheHitTokens;

    @Column(name = "cache_write_tokens", nullable = false)
    @Comment("缓存写入的 token 数。")
    private int cacheWriteTokens;

    @Column(name = "saved_input_tokens", nullable = false)
    @Comment("节省的输入 token 数。")
    private int savedInputTokens;

    @Column(name = "cached_content_ref", length = 512)
    @Comment("显式缓存引用，例如 Gemini cached_content。")
    private String cachedContentRef;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    @Comment("创建时间（UTC）。")
    private Instant createdAt;

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

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
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

    public String getModelGroup() {
        return modelGroup;
    }

    public void setModelGroup(String modelGroup) {
        this.modelGroup = modelGroup;
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

    public String getCacheKind() {
        return cacheKind;
    }

    public void setCacheKind(String cacheKind) {
        this.cacheKind = cacheKind;
    }

    public String getExecutionBackend() {
        return executionBackend;
    }

    public void setExecutionBackend(String executionBackend) {
        this.executionBackend = executionBackend;
    }

    public String getObjectMode() {
        return objectMode;
    }

    public void setObjectMode(String objectMode) {
        this.objectMode = objectMode;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
