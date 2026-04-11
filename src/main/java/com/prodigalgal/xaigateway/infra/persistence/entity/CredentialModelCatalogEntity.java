package com.prodigalgal.xaigateway.infra.persistence.entity;

import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.infra.persistence.converter.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "credential_model_catalog",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_credential_model_catalog_pair", columnNames = {"credential_id", "model_key"})
        },
        indexes = {
                @Index(name = "idx_credential_model_catalog_model_key", columnList = "model_key"),
                @Index(name = "idx_credential_model_catalog_credential_active", columnList = "credential_id,active"),
                @Index(name = "idx_credential_model_catalog_supports_chat", columnList = "supports_chat")
        }
)
@Comment("按上游凭证发现的模型目录缓存。")
public class CredentialModelCatalogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    @Comment("主键。")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credential_id", nullable = false)
    @Comment("所属上游凭证。")
    private UpstreamCredentialEntity credential;

    @Column(name = "model_name", nullable = false, length = 256)
    @Comment("模型原始名称。")
    private String modelName;

    @Column(name = "model_key", nullable = false, length = 256)
    @Comment("模型规范化键。")
    private String modelKey;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "supported_protocols_json", nullable = false, columnDefinition = "text")
    @Comment("支持的协议列表，JSON 数组。")
    private List<String> supportedProtocols = List.of();

    @Column(name = "supports_chat", nullable = false)
    @Comment("是否支持聊天。")
    private boolean supportsChat;

    @Column(name = "supports_embeddings", nullable = false)
    @Comment("是否支持向量。")
    private boolean supportsEmbeddings;

    @Column(name = "supports_cache", nullable = false)
    @Comment("是否支持上游缓存能力。")
    private boolean supportsCache;

    @Column(name = "supports_thinking", nullable = false)
    @Comment("是否支持 thinking / reasoning。")
    private boolean supportsThinking;

    @Column(name = "supports_visible_reasoning", nullable = false)
    @Comment("是否支持可见 reasoning。")
    private boolean supportsVisibleReasoning;

    @Column(name = "supports_reasoning_reuse", nullable = false)
    @Comment("是否支持 reasoning 复用。")
    private boolean supportsReasoningReuse;

    @Enumerated(EnumType.STRING)
    @Column(name = "reasoning_transport", nullable = false, length = 32)
    @Comment("reasoning 传输方式。")
    private ReasoningTransport reasoningTransport = ReasoningTransport.NONE;

    @Column(name = "active", nullable = false)
    @Comment("该模型目录条目是否可用。")
    private boolean active = true;

    @Column(name = "source_refreshed_at", columnDefinition = "timestamp with time zone")
    @Comment("上游最近一次发现时间（UTC）。")
    private Instant sourceRefreshedAt;

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

    public UpstreamCredentialEntity getCredential() {
        return credential;
    }

    public void setCredential(UpstreamCredentialEntity credential) {
        this.credential = credential;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelKey() {
        return modelKey;
    }

    public void setModelKey(String modelKey) {
        this.modelKey = modelKey;
    }

    public List<String> getSupportedProtocols() {
        return supportedProtocols;
    }

    public void setSupportedProtocols(List<String> supportedProtocols) {
        this.supportedProtocols = supportedProtocols;
    }

    public boolean isSupportsChat() {
        return supportsChat;
    }

    public void setSupportsChat(boolean supportsChat) {
        this.supportsChat = supportsChat;
    }

    public boolean isSupportsEmbeddings() {
        return supportsEmbeddings;
    }

    public void setSupportsEmbeddings(boolean supportsEmbeddings) {
        this.supportsEmbeddings = supportsEmbeddings;
    }

    public boolean isSupportsCache() {
        return supportsCache;
    }

    public void setSupportsCache(boolean supportsCache) {
        this.supportsCache = supportsCache;
    }

    public boolean isSupportsThinking() {
        return supportsThinking;
    }

    public void setSupportsThinking(boolean supportsThinking) {
        this.supportsThinking = supportsThinking;
    }

    public boolean isSupportsVisibleReasoning() {
        return supportsVisibleReasoning;
    }

    public void setSupportsVisibleReasoning(boolean supportsVisibleReasoning) {
        this.supportsVisibleReasoning = supportsVisibleReasoning;
    }

    public boolean isSupportsReasoningReuse() {
        return supportsReasoningReuse;
    }

    public void setSupportsReasoningReuse(boolean supportsReasoningReuse) {
        this.supportsReasoningReuse = supportsReasoningReuse;
    }

    public ReasoningTransport getReasoningTransport() {
        return reasoningTransport;
    }

    public void setReasoningTransport(ReasoningTransport reasoningTransport) {
        this.reasoningTransport = reasoningTransport;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getSourceRefreshedAt() {
        return sourceRefreshedAt;
    }

    public void setSourceRefreshedAt(Instant sourceRefreshedAt) {
        this.sourceRefreshedAt = sourceRefreshedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
