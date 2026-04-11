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
        name = "upstream_credential",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_upstream_credential_api_key_fingerprint", columnNames = "api_key_fingerprint")
        },
        indexes = {
                @Index(name = "idx_upstream_credential_provider_active", columnList = "provider_type,is_active"),
                @Index(name = "idx_upstream_credential_cooldown_until", columnList = "cooldown_until"),
                @Index(name = "idx_upstream_credential_deleted_updated_at", columnList = "deleted,updated_at")
        }
)
@Comment("上游厂商凭证表，保存真实厂商 API key 及其健康状态。")
public class UpstreamCredentialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    @Comment("主键。")
    private Long id;

    @Column(name = "credential_name", nullable = false, length = 128)
    @Comment("凭证名称，用于后台识别。")
    private String credentialName;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 32)
    @Comment("厂商类型，例如 OPENAI_DIRECT、ANTHROPIC_DIRECT。")
    private ProviderType providerType;

    @Column(name = "base_url", nullable = false, length = 512)
    @Comment("上游基础 URL。")
    private String baseUrl;

    @Column(name = "api_key_ciphertext", nullable = false, columnDefinition = "text")
    @Comment("加密后的 API key 密文。")
    private String apiKeyCiphertext;

    @Column(name = "api_key_fingerprint", nullable = false, length = 128)
    @Comment("API key 指纹，用于查重和审计。")
    private String apiKeyFingerprint;

    @Column(name = "is_active", nullable = false)
    @Comment("是否启用。")
    private boolean active = true;

    @Column(name = "cooldown_until", columnDefinition = "timestamp with time zone")
    @Comment("冷却截止时间，冷却期间不参与路由。")
    private Instant cooldownUntil;

    @Column(name = "last_error_code", length = 64)
    @Comment("最后一次错误码。")
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 512)
    @Comment("最后一次错误摘要。")
    private String lastErrorMessage;

    @Column(name = "last_error_at", columnDefinition = "timestamp with time zone")
    @Comment("最后一次错误时间（UTC）。")
    private Instant lastErrorAt;

    @Column(name = "last_used_at", columnDefinition = "timestamp with time zone")
    @Comment("最后一次被选中调用的时间（UTC）。")
    private Instant lastUsedAt;

    @Column(name = "proxy_id")
    @Comment("绑定的代理 ID。")
    private Long proxyId;

    @Column(name = "tls_fingerprint_profile_id")
    @Comment("绑定的 TLS 指纹画像 ID。")
    private Long tlsFingerprintProfileId;

    @Column(name = "deleted", nullable = false)
    @Comment("逻辑删除标记。")
    private boolean deleted = false;

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

    public String getCredentialName() {
        return credentialName;
    }

    public void setCredentialName(String credentialName) {
        this.credentialName = credentialName;
    }

    public ProviderType getProviderType() {
        return providerType;
    }

    public void setProviderType(ProviderType providerType) {
        this.providerType = providerType;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKeyCiphertext() {
        return apiKeyCiphertext;
    }

    public void setApiKeyCiphertext(String apiKeyCiphertext) {
        this.apiKeyCiphertext = apiKeyCiphertext;
    }

    public String getApiKeyFingerprint() {
        return apiKeyFingerprint;
    }

    public void setApiKeyFingerprint(String apiKeyFingerprint) {
        this.apiKeyFingerprint = apiKeyFingerprint;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCooldownUntil() {
        return cooldownUntil;
    }

    public void setCooldownUntil(Instant cooldownUntil) {
        this.cooldownUntil = cooldownUntil;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public Instant getLastErrorAt() {
        return lastErrorAt;
    }

    public void setLastErrorAt(Instant lastErrorAt) {
        this.lastErrorAt = lastErrorAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public Long getProxyId() {
        return proxyId;
    }

    public void setProxyId(Long proxyId) {
        this.proxyId = proxyId;
    }

    public Long getTlsFingerprintProfileId() {
        return tlsFingerprintProfileId;
    }

    public void setTlsFingerprintProfileId(Long tlsFingerprintProfileId) {
        this.tlsFingerprintProfileId = tlsFingerprintProfileId;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
