package com.prodigalgal.xaigateway.infra.persistence.entity;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import jakarta.persistence.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "upstream_account")
@Comment("上游账号。")
public class UpstreamAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pool_id", nullable = false)
    private UpstreamAccountPoolEntity pool;

    @Column(name = "account_name", nullable = false, length = 128)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 32)
    private UpstreamAccountProviderType providerType;

    @Column(name = "external_account_id", length = 256)
    private String externalAccountId;

    @Column(name = "access_token_ciphertext", columnDefinition = "text")
    private String accessTokenCiphertext;

    @Column(name = "refresh_token_ciphertext", columnDefinition = "text")
    private String refreshTokenCiphertext;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_frozen", nullable = false)
    private boolean frozen = false;

    @Column(name = "is_healthy", nullable = false)
    private boolean healthy = true;

    @Column(name = "last_refresh_at", columnDefinition = "timestamp with time zone")
    private Instant lastRefreshAt;

    @Column(name = "last_used_at", columnDefinition = "timestamp with time zone")
    private Instant lastUsedAt;

    @Column(name = "last_error_message", length = 512)
    private String lastErrorMessage;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;

    @Column(name = "proxy_id")
    private Long proxyId;

    @Column(name = "tls_fingerprint_profile_id")
    private Long tlsFingerprintProfileId;

    @Column(name = "site_profile_id")
    private Long siteProfileId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() { return id; }
    public UpstreamAccountPoolEntity getPool() { return pool; }
    public void setPool(UpstreamAccountPoolEntity pool) { this.pool = pool; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public UpstreamAccountProviderType getProviderType() { return providerType; }
    public void setProviderType(UpstreamAccountProviderType providerType) { this.providerType = providerType; }
    public String getExternalAccountId() { return externalAccountId; }
    public void setExternalAccountId(String externalAccountId) { this.externalAccountId = externalAccountId; }
    public String getAccessTokenCiphertext() { return accessTokenCiphertext; }
    public void setAccessTokenCiphertext(String accessTokenCiphertext) { this.accessTokenCiphertext = accessTokenCiphertext; }
    public String getRefreshTokenCiphertext() { return refreshTokenCiphertext; }
    public void setRefreshTokenCiphertext(String refreshTokenCiphertext) { this.refreshTokenCiphertext = refreshTokenCiphertext; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isFrozen() { return frozen; }
    public void setFrozen(boolean frozen) { this.frozen = frozen; }
    public boolean isHealthy() { return healthy; }
    public void setHealthy(boolean healthy) { this.healthy = healthy; }
    public Instant getLastRefreshAt() { return lastRefreshAt; }
    public void setLastRefreshAt(Instant lastRefreshAt) { this.lastRefreshAt = lastRefreshAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public Long getProxyId() { return proxyId; }
    public void setProxyId(Long proxyId) { this.proxyId = proxyId; }
    public Long getTlsFingerprintProfileId() { return tlsFingerprintProfileId; }
    public void setTlsFingerprintProfileId(Long tlsFingerprintProfileId) { this.tlsFingerprintProfileId = tlsFingerprintProfileId; }
    public Long getSiteProfileId() { return siteProfileId; }
    public void setSiteProfileId(Long siteProfileId) { this.siteProfileId = siteProfileId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
