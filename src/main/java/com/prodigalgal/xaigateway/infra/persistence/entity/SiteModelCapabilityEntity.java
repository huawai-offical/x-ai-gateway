package com.prodigalgal.xaigateway.infra.persistence.entity;

import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
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

@Entity
@Table(
        name = "site_model_capability",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_site_model_capability_profile_model_key", columnNames = "site_profile_id,model_key")
        },
        indexes = {
                @Index(name = "idx_site_model_capability_model_key_active", columnList = "model_key,is_active"),
                @Index(name = "idx_site_model_capability_site_profile", columnList = "site_profile_id")
        }
)
@Comment("站点级模型能力真相源。")
public class SiteModelCapabilityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    @Comment("主键。")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_profile_id", nullable = false)
    @Comment("关联的站点档案。")
    private UpstreamSiteProfileEntity siteProfile;

    @Column(name = "model_name", nullable = false, length = 256)
    @Comment("模型原始名称。")
    private String modelName;

    @Column(name = "model_key", nullable = false, length = 256)
    @Comment("归一化后的模型 key。")
    private String modelKey;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "supported_protocols_json", nullable = false, columnDefinition = "text")
    @Comment("该模型支持的前台协议列表。")
    private List<String> supportedProtocols = List.of();

    @Column(name = "supports_chat", nullable = false)
    @Comment("是否支持 chat。")
    private boolean supportsChat;

    @Column(name = "supports_tools", nullable = false)
    @Comment("是否支持 tool calling。")
    private boolean supportsTools;

    @Column(name = "supports_image_input", nullable = false)
    @Comment("是否支持图片输入。")
    private boolean supportsImageInput;

    @Column(name = "supports_embeddings", nullable = false)
    @Comment("是否支持 embeddings。")
    private boolean supportsEmbeddings;

    @Column(name = "supports_cache", nullable = false)
    @Comment("是否支持 cache。")
    private boolean supportsCache;

    @Column(name = "supports_thinking", nullable = false)
    @Comment("是否支持 reasoning/thinking。")
    private boolean supportsThinking;

    @Column(name = "supports_visible_reasoning", nullable = false)
    @Comment("是否支持可见 reasoning。")
    private boolean supportsVisibleReasoning;

    @Column(name = "supports_reasoning_reuse", nullable = false)
    @Comment("是否支持 reasoning reuse。")
    private boolean supportsReasoningReuse;

    @Enumerated(EnumType.STRING)
    @Column(name = "reasoning_transport", nullable = false, length = 32)
    @Comment("reasoning 传输类型。")
    private ReasoningTransport reasoningTransport = ReasoningTransport.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "capability_level", nullable = false, length = 32)
    @Comment("模型总体损耗等级。")
    private InteropCapabilityLevel capabilityLevel = InteropCapabilityLevel.NATIVE;

    @Column(name = "is_active", nullable = false)
    @Comment("是否激活。")
    private boolean active = true;

    @Column(name = "source_refreshed_at", nullable = false, columnDefinition = "timestamp with time zone")
    @Comment("源刷新时间。")
    private Instant sourceRefreshedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public UpstreamSiteProfileEntity getSiteProfile() {
        return siteProfile;
    }

    public void setSiteProfile(UpstreamSiteProfileEntity siteProfile) {
        this.siteProfile = siteProfile;
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

    public boolean isSupportsTools() {
        return supportsTools;
    }

    public void setSupportsTools(boolean supportsTools) {
        this.supportsTools = supportsTools;
    }

    public boolean isSupportsImageInput() {
        return supportsImageInput;
    }

    public void setSupportsImageInput(boolean supportsImageInput) {
        this.supportsImageInput = supportsImageInput;
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

    public InteropCapabilityLevel getCapabilityLevel() {
        return capabilityLevel;
    }

    public void setCapabilityLevel(InteropCapabilityLevel capabilityLevel) {
        this.capabilityLevel = capabilityLevel;
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
}
