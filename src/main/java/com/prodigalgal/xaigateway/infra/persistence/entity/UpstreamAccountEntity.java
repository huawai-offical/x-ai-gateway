package com.prodigalgal.xaigateway.infra.persistence.entity;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.infra.persistence.converter.StringListJsonConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "upstream_account")
@Comment("上游账号。")
public class UpstreamAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "pool_id", nullable = true)
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

    @Column(name = "token_expires_at", columnDefinition = "timestamp with time zone")
    private Instant tokenExpiresAt;

    @Column(name = "refresh_status", nullable = false, length = 32)
    private String refreshStatus = "UNKNOWN";

    @Column(name = "refresh_failure_count", nullable = false)
    private int refreshFailureCount = 0;

    @Column(name = "next_refresh_after", columnDefinition = "timestamp with time zone")
    private Instant nextRefreshAfter;

    @Column(name = "cooldown_until", columnDefinition = "timestamp with time zone")
    private Instant cooldownUntil;

    @Column(name = "quota_window_started_at", columnDefinition = "timestamp with time zone")
    private Instant quotaWindowStartedAt;

    @Column(name = "quota_window_seconds")
    private Integer quotaWindowSeconds;

    @Column(name = "quota_remaining_tokens")
    private Long quotaRemainingTokens;

    @Column(name = "quota_remaining_requests")
    private Long quotaRemainingRequests;

    @Column(name = "header_snapshot_json", columnDefinition = "text")
    private String headerSnapshotJson;

    @Column(name = "last_refresh_result_json", columnDefinition = "text")
    private String lastRefreshResultJson;

    @Column(name = "last_error_message", length = 512)
    private String lastErrorMessage;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "supported_models_json", columnDefinition = "text")
    private List<String> supportedModels = List.of();

    @Column(name = "proxy_id")
    private Long proxyId;

    @Column(name = "tls_fingerprint_profile_id")
    private Long tlsFingerprintProfileId;

    @Column(name = "site_profile_id")
    private Long siteProfileId;

    @Column(name = "total_request_count", nullable = false)
    private long totalRequestCount = 0;

    @Column(name = "successful_request_count", nullable = false)
    private long successfulRequestCount = 0;

    @Column(name = "failed_request_count", nullable = false)
    private long failedRequestCount = 0;

    @Column(name = "canceled_request_count", nullable = false)
    private long canceledRequestCount = 0;

    @Column(name = "total_token_count", nullable = false)
    private long totalTokenCount = 0;

    @Column(name = "total_cache_hit_token_count", nullable = false)
    private long totalCacheHitTokenCount = 0;

    @Column(name = "total_cache_write_token_count", nullable = false)
    private long totalCacheWriteTokenCount = 0;

    @Column(name = "total_saved_input_token_count", nullable = false)
    private long totalSavedInputTokenCount = 0;

    @Column(name = "total_duration_ms", nullable = false)
    private long totalDurationMs = 0;

    @Column(name = "duration_sample_count", nullable = false)
    private long durationSampleCount = 0;

    @Column(name = "total_first_token_ms", nullable = false)
    private long totalFirstTokenMs = 0;

    @Column(name = "first_token_sample_count", nullable = false)
    private long firstTokenSampleCount = 0;

    @Column(name = "last_first_token_ms")
    private Long lastFirstTokenMs;

    @Column(name = "min_first_token_ms")
    private Long minFirstTokenMs;

    @Column(name = "max_first_token_ms")
    private Long maxFirstTokenMs;

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
    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(Instant tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }
    public String getRefreshStatus() { return refreshStatus; }
    public void setRefreshStatus(String refreshStatus) { this.refreshStatus = refreshStatus; }
    public int getRefreshFailureCount() { return refreshFailureCount; }
    public void setRefreshFailureCount(int refreshFailureCount) { this.refreshFailureCount = refreshFailureCount; }
    public Instant getNextRefreshAfter() { return nextRefreshAfter; }
    public void setNextRefreshAfter(Instant nextRefreshAfter) { this.nextRefreshAfter = nextRefreshAfter; }
    public Instant getCooldownUntil() { return cooldownUntil; }
    public void setCooldownUntil(Instant cooldownUntil) { this.cooldownUntil = cooldownUntil; }
    public Instant getQuotaWindowStartedAt() { return quotaWindowStartedAt; }
    public void setQuotaWindowStartedAt(Instant quotaWindowStartedAt) { this.quotaWindowStartedAt = quotaWindowStartedAt; }
    public Integer getQuotaWindowSeconds() { return quotaWindowSeconds; }
    public void setQuotaWindowSeconds(Integer quotaWindowSeconds) { this.quotaWindowSeconds = quotaWindowSeconds; }
    public Long getQuotaRemainingTokens() { return quotaRemainingTokens; }
    public void setQuotaRemainingTokens(Long quotaRemainingTokens) { this.quotaRemainingTokens = quotaRemainingTokens; }
    public Long getQuotaRemainingRequests() { return quotaRemainingRequests; }
    public void setQuotaRemainingRequests(Long quotaRemainingRequests) { this.quotaRemainingRequests = quotaRemainingRequests; }
    public String getHeaderSnapshotJson() { return headerSnapshotJson; }
    public void setHeaderSnapshotJson(String headerSnapshotJson) { this.headerSnapshotJson = headerSnapshotJson; }
    public String getLastRefreshResultJson() { return lastRefreshResultJson; }
    public void setLastRefreshResultJson(String lastRefreshResultJson) { this.lastRefreshResultJson = lastRefreshResultJson; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public List<String> getSupportedModels() { return supportedModels; }
    public void setSupportedModels(List<String> supportedModels) { this.supportedModels = supportedModels; }
    public Long getProxyId() { return proxyId; }
    public void setProxyId(Long proxyId) { this.proxyId = proxyId; }
    public Long getTlsFingerprintProfileId() { return tlsFingerprintProfileId; }
    public void setTlsFingerprintProfileId(Long tlsFingerprintProfileId) { this.tlsFingerprintProfileId = tlsFingerprintProfileId; }
    public Long getSiteProfileId() { return siteProfileId; }
    public void setSiteProfileId(Long siteProfileId) { this.siteProfileId = siteProfileId; }
    public long getTotalRequestCount() { return totalRequestCount; }
    public void setTotalRequestCount(long totalRequestCount) { this.totalRequestCount = totalRequestCount; }
    public long getSuccessfulRequestCount() { return successfulRequestCount; }
    public void setSuccessfulRequestCount(long successfulRequestCount) { this.successfulRequestCount = successfulRequestCount; }
    public long getFailedRequestCount() { return failedRequestCount; }
    public void setFailedRequestCount(long failedRequestCount) { this.failedRequestCount = failedRequestCount; }
    public long getCanceledRequestCount() { return canceledRequestCount; }
    public void setCanceledRequestCount(long canceledRequestCount) { this.canceledRequestCount = canceledRequestCount; }
    public long getTotalTokenCount() { return totalTokenCount; }
    public void setTotalTokenCount(long totalTokenCount) { this.totalTokenCount = totalTokenCount; }
    public long getTotalCacheHitTokenCount() { return totalCacheHitTokenCount; }
    public void setTotalCacheHitTokenCount(long totalCacheHitTokenCount) { this.totalCacheHitTokenCount = totalCacheHitTokenCount; }
    public long getTotalCacheWriteTokenCount() { return totalCacheWriteTokenCount; }
    public void setTotalCacheWriteTokenCount(long totalCacheWriteTokenCount) { this.totalCacheWriteTokenCount = totalCacheWriteTokenCount; }
    public long getTotalSavedInputTokenCount() { return totalSavedInputTokenCount; }
    public void setTotalSavedInputTokenCount(long totalSavedInputTokenCount) { this.totalSavedInputTokenCount = totalSavedInputTokenCount; }
    public long getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }
    public long getDurationSampleCount() { return durationSampleCount; }
    public void setDurationSampleCount(long durationSampleCount) { this.durationSampleCount = durationSampleCount; }
    public long getTotalFirstTokenMs() { return totalFirstTokenMs; }
    public void setTotalFirstTokenMs(long totalFirstTokenMs) { this.totalFirstTokenMs = totalFirstTokenMs; }
    public long getFirstTokenSampleCount() { return firstTokenSampleCount; }
    public void setFirstTokenSampleCount(long firstTokenSampleCount) { this.firstTokenSampleCount = firstTokenSampleCount; }
    public Long getLastFirstTokenMs() { return lastFirstTokenMs; }
    public void setLastFirstTokenMs(Long lastFirstTokenMs) { this.lastFirstTokenMs = lastFirstTokenMs; }
    public Long getMinFirstTokenMs() { return minFirstTokenMs; }
    public void setMinFirstTokenMs(Long minFirstTokenMs) { this.minFirstTokenMs = minFirstTokenMs; }
    public Long getMaxFirstTokenMs() { return maxFirstTokenMs; }
    public void setMaxFirstTokenMs(Long maxFirstTokenMs) { this.maxFirstTokenMs = maxFirstTokenMs; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
