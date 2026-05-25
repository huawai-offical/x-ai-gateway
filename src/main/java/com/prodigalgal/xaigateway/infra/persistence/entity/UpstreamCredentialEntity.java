package com.prodigalgal.xaigateway.infra.persistence.entity;

import com.prodigalgal.xaigateway.gateway.core.credential.CredentialAuthKind;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.converter.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.util.List;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "upstream_credential",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_upstream_credential_scope_fingerprint",
                        columnNames = {"api_key_fingerprint", "provider_type", "base_url", "site_profile_id", "protocol_endpoint_id"}
                )
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

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_kind", nullable = false, length = 32)
    @Comment("凭证认证材料类型。")
    private CredentialAuthKind authKind = CredentialAuthKind.API_KEY;

    @Column(name = "credential_metadata_json", columnDefinition = "text")
    @Comment("凭证附加 metadata，例如 Vertex project/location。")
    private String credentialMetadataJson;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "supported_models_json", columnDefinition = "text")
    @Comment("该凭证支持的模型列表。")
    private List<String> supportedModels = List.of();

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

    @Column(name = "connectivity_status", length = 32)
    @Comment("最近一次凭证联通性探测状态，例如 AVAILABLE、UNAVAILABLE、UNSUPPORTED。")
    private String connectivityStatus;

    @Column(name = "last_connectivity_test_at", columnDefinition = "timestamp with time zone")
    @Comment("最近一次凭证联通性探测时间（UTC）。")
    private Instant lastConnectivityTestAt;

    @Column(name = "last_connectivity_latency_ms")
    @Comment("最近一次凭证联通性探测耗时（毫秒）。")
    private Long lastConnectivityLatencyMs;

    @Column(name = "last_connectivity_error_message", length = 512)
    @Comment("最近一次凭证联通性探测错误摘要。")
    private String lastConnectivityErrorMessage;

    @Column(name = "last_connectivity_response_summary", length = 512)
    @Comment("最近一次凭证联通性探测上游返回摘要。")
    private String lastConnectivityResponseSummary;

    @Column(name = "last_connectivity_upstream_request_id", length = 128)
    @Comment("最近一次凭证联通性探测上游请求 ID。")
    private String lastConnectivityUpstreamRequestId;

    @Column(name = "last_connectivity_model", length = 128)
    @Comment("最近一次凭证联通性探测使用的模型。")
    private String lastConnectivityModel;

    @Column(name = "total_request_count", nullable = false)
    @Comment("累计请求数。")
    private long totalRequestCount = 0;

    @Column(name = "successful_request_count", nullable = false)
    @Comment("累计成功请求数。")
    private long successfulRequestCount = 0;

    @Column(name = "failed_request_count", nullable = false)
    @Comment("累计失败请求数。")
    private long failedRequestCount = 0;

    @Column(name = "canceled_request_count", nullable = false)
    @Comment("累计取消请求数。")
    private long canceledRequestCount = 0;

    @Column(name = "total_token_count", nullable = false)
    @Comment("累计 token 总量。")
    private long totalTokenCount = 0;

    @Column(name = "total_cache_hit_token_count", nullable = false)
    @Comment("累计缓存命中 token 数。")
    private long totalCacheHitTokenCount = 0;

    @Column(name = "total_cache_write_token_count", nullable = false)
    @Comment("累计缓存写入 token 数。")
    private long totalCacheWriteTokenCount = 0;

    @Column(name = "total_saved_input_token_count", nullable = false)
    @Comment("累计节省输入 token 数。")
    private long totalSavedInputTokenCount = 0;

    @Column(name = "total_duration_ms", nullable = false)
    @Comment("累计请求耗时（毫秒）。")
    private long totalDurationMs = 0;

    @Column(name = "duration_sample_count", nullable = false)
    @Comment("耗时样本数。")
    private long durationSampleCount = 0;

    @Column(name = "total_first_token_ms", nullable = false)
    @Comment("累计首 token 延迟（毫秒）。")
    private long totalFirstTokenMs = 0;

    @Column(name = "first_token_sample_count", nullable = false)
    @Comment("首 token 样本数。")
    private long firstTokenSampleCount = 0;

    @Column(name = "last_first_token_ms")
    @Comment("最近一次首 token 延迟（毫秒）。")
    private Long lastFirstTokenMs;

    @Column(name = "min_first_token_ms")
    @Comment("最小首 token 延迟（毫秒）。")
    private Long minFirstTokenMs;

    @Column(name = "max_first_token_ms")
    @Comment("最大首 token 延迟（毫秒）。")
    private Long maxFirstTokenMs;

    @Column(name = "proxy_id")
    @Comment("绑定的代理 ID。")
    private Long proxyId;

    @Column(name = "tls_fingerprint_profile_id")
    @Comment("绑定的 TLS 指纹画像 ID。")
    private Long tlsFingerprintProfileId;

    @Column(name = "site_profile_id")
    @Comment("绑定的站点档案 ID。")
    private Long siteProfileId;

    @Column(name = "protocol_endpoint_id")
    @Comment("绑定的厂商协议入口 ID。")
    private Long protocolEndpointId;

    @Column(name = "group_id", nullable = true)
    @Comment("账号分组 ID。")
    private Long groupId;

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

    public CredentialAuthKind getAuthKind() {
        return authKind;
    }

    public void setAuthKind(CredentialAuthKind authKind) {
        this.authKind = authKind;
    }

    public String getCredentialMetadataJson() {
        return credentialMetadataJson;
    }

    public void setCredentialMetadataJson(String credentialMetadataJson) {
        this.credentialMetadataJson = credentialMetadataJson;
    }

    public List<String> getSupportedModels() {
        return supportedModels;
    }

    public void setSupportedModels(List<String> supportedModels) {
        this.supportedModels = supportedModels;
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

    public String getConnectivityStatus() {
        return connectivityStatus;
    }

    public void setConnectivityStatus(String connectivityStatus) {
        this.connectivityStatus = connectivityStatus;
    }

    public Instant getLastConnectivityTestAt() {
        return lastConnectivityTestAt;
    }

    public void setLastConnectivityTestAt(Instant lastConnectivityTestAt) {
        this.lastConnectivityTestAt = lastConnectivityTestAt;
    }

    public Long getLastConnectivityLatencyMs() {
        return lastConnectivityLatencyMs;
    }

    public void setLastConnectivityLatencyMs(Long lastConnectivityLatencyMs) {
        this.lastConnectivityLatencyMs = lastConnectivityLatencyMs;
    }

    public String getLastConnectivityErrorMessage() {
        return lastConnectivityErrorMessage;
    }

    public void setLastConnectivityErrorMessage(String lastConnectivityErrorMessage) {
        this.lastConnectivityErrorMessage = lastConnectivityErrorMessage;
    }

    public String getLastConnectivityResponseSummary() {
        return lastConnectivityResponseSummary;
    }

    public void setLastConnectivityResponseSummary(String lastConnectivityResponseSummary) {
        this.lastConnectivityResponseSummary = lastConnectivityResponseSummary;
    }

    public String getLastConnectivityUpstreamRequestId() {
        return lastConnectivityUpstreamRequestId;
    }

    public void setLastConnectivityUpstreamRequestId(String lastConnectivityUpstreamRequestId) {
        this.lastConnectivityUpstreamRequestId = lastConnectivityUpstreamRequestId;
    }

    public String getLastConnectivityModel() {
        return lastConnectivityModel;
    }

    public void setLastConnectivityModel(String lastConnectivityModel) {
        this.lastConnectivityModel = lastConnectivityModel;
    }

    public long getTotalRequestCount() {
        return totalRequestCount;
    }

    public void setTotalRequestCount(long totalRequestCount) {
        this.totalRequestCount = totalRequestCount;
    }

    public long getSuccessfulRequestCount() {
        return successfulRequestCount;
    }

    public void setSuccessfulRequestCount(long successfulRequestCount) {
        this.successfulRequestCount = successfulRequestCount;
    }

    public long getFailedRequestCount() {
        return failedRequestCount;
    }

    public void setFailedRequestCount(long failedRequestCount) {
        this.failedRequestCount = failedRequestCount;
    }

    public long getCanceledRequestCount() {
        return canceledRequestCount;
    }

    public void setCanceledRequestCount(long canceledRequestCount) {
        this.canceledRequestCount = canceledRequestCount;
    }

    public long getTotalTokenCount() {
        return totalTokenCount;
    }

    public void setTotalTokenCount(long totalTokenCount) {
        this.totalTokenCount = totalTokenCount;
    }

    public long getTotalCacheHitTokenCount() {
        return totalCacheHitTokenCount;
    }

    public void setTotalCacheHitTokenCount(long totalCacheHitTokenCount) {
        this.totalCacheHitTokenCount = totalCacheHitTokenCount;
    }

    public long getTotalCacheWriteTokenCount() {
        return totalCacheWriteTokenCount;
    }

    public void setTotalCacheWriteTokenCount(long totalCacheWriteTokenCount) {
        this.totalCacheWriteTokenCount = totalCacheWriteTokenCount;
    }

    public long getTotalSavedInputTokenCount() {
        return totalSavedInputTokenCount;
    }

    public void setTotalSavedInputTokenCount(long totalSavedInputTokenCount) {
        this.totalSavedInputTokenCount = totalSavedInputTokenCount;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    public void setTotalDurationMs(long totalDurationMs) {
        this.totalDurationMs = totalDurationMs;
    }

    public long getDurationSampleCount() {
        return durationSampleCount;
    }

    public void setDurationSampleCount(long durationSampleCount) {
        this.durationSampleCount = durationSampleCount;
    }

    public long getTotalFirstTokenMs() {
        return totalFirstTokenMs;
    }

    public void setTotalFirstTokenMs(long totalFirstTokenMs) {
        this.totalFirstTokenMs = totalFirstTokenMs;
    }

    public long getFirstTokenSampleCount() {
        return firstTokenSampleCount;
    }

    public void setFirstTokenSampleCount(long firstTokenSampleCount) {
        this.firstTokenSampleCount = firstTokenSampleCount;
    }

    public Long getLastFirstTokenMs() {
        return lastFirstTokenMs;
    }

    public void setLastFirstTokenMs(Long lastFirstTokenMs) {
        this.lastFirstTokenMs = lastFirstTokenMs;
    }

    public Long getMinFirstTokenMs() {
        return minFirstTokenMs;
    }

    public void setMinFirstTokenMs(Long minFirstTokenMs) {
        this.minFirstTokenMs = minFirstTokenMs;
    }

    public Long getMaxFirstTokenMs() {
        return maxFirstTokenMs;
    }

    public void setMaxFirstTokenMs(Long maxFirstTokenMs) {
        this.maxFirstTokenMs = maxFirstTokenMs;
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

    public Long getSiteProfileId() {
        return siteProfileId;
    }

    public void setSiteProfileId(Long siteProfileId) {
        this.siteProfileId = siteProfileId;
    }

    public Long getProtocolEndpointId() {
        return protocolEndpointId;
    }

    public void setProtocolEndpointId(Long protocolEndpointId) {
        this.protocolEndpointId = protocolEndpointId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
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
