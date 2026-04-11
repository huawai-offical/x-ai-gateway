package com.prodigalgal.xaigateway.infra.persistence.entity;

import com.prodigalgal.xaigateway.infra.persistence.converter.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "distributed_key",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_distributed_key_key_prefix", columnNames = "key_prefix")
        },
        indexes = {
                @Index(name = "idx_distributed_key_is_active", columnList = "is_active"),
                @Index(name = "idx_distributed_key_last_used_at", columnList = "last_used_at"),
                @Index(name = "idx_distributed_key_updated_at", columnList = "updated_at")
        }
)
@Comment("对前台分发的网关 key。")
public class DistributedKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    @Comment("主键。")
    private Long id;

    @Column(name = "key_name", nullable = false, length = 128)
    @Comment("分发 key 名称。")
    private String keyName;

    @Column(name = "key_prefix", nullable = false, length = 64)
    @Comment("分发 key 前缀，用于快速查找。")
    private String keyPrefix;

    @Column(name = "secret_hash", nullable = false, length = 256)
    @Comment("分发 key secret 的哈希值。")
    private String secretHash;

    @Column(name = "masked_key", nullable = false, length = 128)
    @Comment("掩码后的分发 key，仅用于后台展示。")
    private String maskedKey;

    @Column(name = "description", length = 512)
    @Comment("分发 key 说明。")
    private String description;

    @Column(name = "is_active", nullable = false)
    @Comment("是否启用。")
    private boolean active = true;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "allowed_protocols_json", nullable = false, columnDefinition = "text")
    @Comment("允许的协议白名单，JSON 数组。")
    private List<String> allowedProtocols = List.of();

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "allowed_models_json", nullable = false, columnDefinition = "text")
    @Comment("允许的模型或别名白名单，JSON 数组。")
    private List<String> allowedModels = List.of();

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "allowed_provider_types_json", columnDefinition = "text")
    @Comment("允许的 provider 类型白名单，JSON 数组。")
    private List<String> allowedProviderTypes = List.of();

    @Column(name = "expires_at", columnDefinition = "timestamp with time zone")
    @Comment("分发 key 过期时间（UTC）。")
    private Instant expiresAt;

    @Column(name = "budget_limit_micros")
    @Comment("预算上限，单位 micros。")
    private Long budgetLimitMicros;

    @Column(name = "budget_window_seconds")
    @Comment("预算窗口秒数。")
    private Integer budgetWindowSeconds;

    @Column(name = "rpm_limit")
    @Comment("每分钟请求数限制。")
    private Integer rpmLimit;

    @Column(name = "tpm_limit")
    @Comment("每分钟 token 估算限制。")
    private Integer tpmLimit;

    @Column(name = "concurrency_limit")
    @Comment("并发请求上限。")
    private Integer concurrencyLimit;

    @Column(name = "sticky_session_ttl_seconds")
    @Comment("会话粘性 TTL 秒数。")
    private Integer stickySessionTtlSeconds;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "allowed_client_families_json", columnDefinition = "text")
    @Comment("允许的客户端家族白名单，JSON 数组。")
    private List<String> allowedClientFamilies = List.of();

    @Column(name = "require_client_family_match", nullable = false)
    @Comment("是否要求客户端家族必须显式匹配。")
    private boolean requireClientFamilyMatch = false;

    @Column(name = "last_used_at", columnDefinition = "timestamp with time zone")
    @Comment("最后一次使用时间（UTC）。")
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

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public void setSecretHash(String secretHash) {
        this.secretHash = secretHash;
    }

    public String getMaskedKey() {
        return maskedKey;
    }

    public void setMaskedKey(String maskedKey) {
        this.maskedKey = maskedKey;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<String> getAllowedProtocols() {
        return allowedProtocols;
    }

    public void setAllowedProtocols(List<String> allowedProtocols) {
        this.allowedProtocols = allowedProtocols;
    }

    public List<String> getAllowedModels() {
        return allowedModels;
    }

    public void setAllowedModels(List<String> allowedModels) {
        this.allowedModels = allowedModels;
    }

    public List<String> getAllowedProviderTypes() {
        return allowedProviderTypes;
    }

    public void setAllowedProviderTypes(List<String> allowedProviderTypes) {
        this.allowedProviderTypes = allowedProviderTypes;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Long getBudgetLimitMicros() {
        return budgetLimitMicros;
    }

    public void setBudgetLimitMicros(Long budgetLimitMicros) {
        this.budgetLimitMicros = budgetLimitMicros;
    }

    public Integer getBudgetWindowSeconds() {
        return budgetWindowSeconds;
    }

    public void setBudgetWindowSeconds(Integer budgetWindowSeconds) {
        this.budgetWindowSeconds = budgetWindowSeconds;
    }

    public Integer getRpmLimit() {
        return rpmLimit;
    }

    public void setRpmLimit(Integer rpmLimit) {
        this.rpmLimit = rpmLimit;
    }

    public Integer getTpmLimit() {
        return tpmLimit;
    }

    public void setTpmLimit(Integer tpmLimit) {
        this.tpmLimit = tpmLimit;
    }

    public Integer getConcurrencyLimit() {
        return concurrencyLimit;
    }

    public void setConcurrencyLimit(Integer concurrencyLimit) {
        this.concurrencyLimit = concurrencyLimit;
    }

    public Integer getStickySessionTtlSeconds() {
        return stickySessionTtlSeconds;
    }

    public void setStickySessionTtlSeconds(Integer stickySessionTtlSeconds) {
        this.stickySessionTtlSeconds = stickySessionTtlSeconds;
    }

    public List<String> getAllowedClientFamilies() {
        return allowedClientFamilies;
    }

    public void setAllowedClientFamilies(List<String> allowedClientFamilies) {
        this.allowedClientFamilies = allowedClientFamilies;
    }

    public boolean isRequireClientFamilyMatch() {
        return requireClientFamilyMatch;
    }

    public void setRequireClientFamilyMatch(boolean requireClientFamilyMatch) {
        this.requireClientFamilyMatch = requireClientFamilyMatch;
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
