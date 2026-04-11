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
        name = "route_decision_log",
        indexes = {
                @Index(name = "idx_route_decision_log_distributed_key_created", columnList = "distributed_key_id,created_at"),
                @Index(name = "idx_route_decision_log_protocol_created", columnList = "protocol,created_at"),
                @Index(name = "idx_route_decision_log_provider_created", columnList = "selected_provider_type,created_at")
        }
)
@Comment("路由决策日志，记录一次请求为何选中某个上游候选。")
public class RouteDecisionLogEntity {

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

    @Column(name = "distributed_key_prefix", nullable = false, length = 64)
    @Comment("分发 key 前缀。")
    private String distributedKeyPrefix;

    @Column(name = "requested_model", nullable = false, length = 256)
    @Comment("请求模型名。")
    private String requestedModel;

    @Column(name = "public_model", nullable = false, length = 256)
    @Comment("对外公开模型名。")
    private String publicModel;

    @Column(name = "resolved_model_key", nullable = false, length = 256)
    @Comment("实际解析后的模型键。")
    private String resolvedModelKey;

    @Column(name = "protocol", nullable = false, length = 32)
    @Comment("请求协议。")
    private String protocol;

    @Column(name = "model_group", nullable = false, length = 256)
    @Comment("模型分组。")
    private String modelGroup;

    @Column(name = "selection_source", nullable = false, length = 32)
    @Comment("选路来源，例如 PREFIX_AFFINITY、WEIGHTED_HASH。")
    private String selectionSource;

    @Column(name = "selected_credential_id", nullable = false)
    @Comment("选中的上游凭证主键。")
    private Long selectedCredentialId;

    @Enumerated(EnumType.STRING)
    @Column(name = "selected_provider_type", nullable = false, length = 32)
    @Comment("选中的上游厂商类型。")
    private ProviderType selectedProviderType;

    @Column(name = "selected_base_url", nullable = false, length = 512)
    @Comment("选中的上游基础地址。")
    private String selectedBaseUrl;

    @Column(name = "prefix_hash", nullable = false, length = 128)
    @Comment("前缀哈希。")
    private String prefixHash;

    @Column(name = "fingerprint", nullable = false, length = 128)
    @Comment("完整请求指纹。")
    private String fingerprint;

    @Column(name = "candidate_count", nullable = false)
    @Comment("候选数量。")
    private int candidateCount;

    @Column(name = "candidate_summary_json", nullable = false, columnDefinition = "text")
    @Comment("候选摘要 JSON。")
    private String candidateSummaryJson;

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

    public String getDistributedKeyPrefix() {
        return distributedKeyPrefix;
    }

    public void setDistributedKeyPrefix(String distributedKeyPrefix) {
        this.distributedKeyPrefix = distributedKeyPrefix;
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

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getModelGroup() {
        return modelGroup;
    }

    public void setModelGroup(String modelGroup) {
        this.modelGroup = modelGroup;
    }

    public String getSelectionSource() {
        return selectionSource;
    }

    public void setSelectionSource(String selectionSource) {
        this.selectionSource = selectionSource;
    }

    public Long getSelectedCredentialId() {
        return selectedCredentialId;
    }

    public void setSelectedCredentialId(Long selectedCredentialId) {
        this.selectedCredentialId = selectedCredentialId;
    }

    public ProviderType getSelectedProviderType() {
        return selectedProviderType;
    }

    public void setSelectedProviderType(ProviderType selectedProviderType) {
        this.selectedProviderType = selectedProviderType;
    }

    public String getSelectedBaseUrl() {
        return selectedBaseUrl;
    }

    public void setSelectedBaseUrl(String selectedBaseUrl) {
        this.selectedBaseUrl = selectedBaseUrl;
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

    public int getCandidateCount() {
        return candidateCount;
    }

    public void setCandidateCount(int candidateCount) {
        this.candidateCount = candidateCount;
    }

    public String getCandidateSummaryJson() {
        return candidateSummaryJson;
    }

    public void setCandidateSummaryJson(String candidateSummaryJson) {
        this.candidateSummaryJson = candidateSummaryJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
