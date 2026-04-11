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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "upstream_cache_reference",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_upstream_cache_reference_scope", columnNames = {"distributed_key_id", "provider_type", "model_group", "prefix_hash"})
        },
        indexes = {
                @Index(name = "idx_upstream_cache_reference_provider_created", columnList = "provider_type,created_at"),
                @Index(name = "idx_upstream_cache_reference_status_expire", columnList = "status,expire_at")
        }
)
@Comment("上游显式缓存引用表，例如 Gemini cached_content 的映射。")
public class UpstreamCacheReferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    @Comment("主键。")
    private Long id;

    @Column(name = "distributed_key_id", nullable = false)
    @Comment("分发 key 主键。")
    private Long distributedKeyId;

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

    @Column(name = "external_cache_ref", nullable = false, length = 512)
    @Comment("上游缓存引用名。")
    private String externalCacheRef;

    @Column(name = "status", nullable = false, length = 32)
    @Comment("缓存引用状态，例如 ACTIVE、INVALIDATED。")
    private String status;

    @Column(name = "expire_at", columnDefinition = "timestamp with time zone")
    @Comment("引用过期时间（UTC）。")
    private Instant expireAt;

    @Column(name = "last_used_at", columnDefinition = "timestamp with time zone")
    @Comment("最后使用时间（UTC）。")
    private Instant lastUsedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    @Comment("创建时间（UTC）。")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    @Comment("最后更新时间（UTC）。")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public Long getDistributedKeyId() {
        return distributedKeyId;
    }

    public void setDistributedKeyId(Long distributedKeyId) {
        this.distributedKeyId = distributedKeyId;
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

    public String getExternalCacheRef() {
        return externalCacheRef;
    }

    public void setExternalCacheRef(String externalCacheRef) {
        this.externalCacheRef = externalCacheRef;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Instant expireAt) {
        this.expireAt = expireAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
