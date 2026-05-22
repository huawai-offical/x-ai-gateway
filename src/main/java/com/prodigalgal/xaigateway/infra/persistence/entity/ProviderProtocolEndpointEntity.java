package com.prodigalgal.xaigateway.infra.persistence.entity;

import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelAddressingStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
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
        name = "provider_protocol_endpoint",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_provider_protocol_endpoint_site_suite",
                        columnNames = {"site_profile_id", "protocol_suite"}
                )
        },
        indexes = {
                @Index(name = "idx_provider_protocol_endpoint_site_active", columnList = "site_profile_id,is_active"),
                @Index(name = "idx_provider_protocol_endpoint_suite_active", columnList = "protocol_suite,is_active")
        }
)
@Comment("厂商协议入口表，承载同一厂商站点下不同协议簇的独立 Base URL、路径策略和对话兼容画像。")
public class ProviderProtocolEndpointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    @Comment("主键。")
    private Long id;

    @Column(name = "site_profile_id", nullable = false)
    @Comment("所属上游站点档案 ID。")
    private Long siteProfileId;

    @Column(name = "endpoint_code", nullable = false, length = 96)
    @Comment("协议入口编码。")
    private String endpointCode;

    @Column(name = "display_name", nullable = false, length = 128)
    @Comment("协议入口显示名称。")
    private String displayName;

    @Column(name = "protocol_suite", nullable = false, length = 96)
    @Comment("厂商协议簇，例如 deepseek.openai_compatible、xiaomi_mimo.anthropic_compatible。")
    private String protocolSuite;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 32)
    @Comment("入口对应的运行时 provider type。")
    private ProviderType providerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "site_kind", nullable = false, length = 64)
    @Comment("入口对应的站点类型。")
    private UpstreamSiteKind siteKind;

    @Column(name = "base_url", nullable = false, length = 512)
    @Comment("该协议入口的上游 Base URL。")
    private String baseUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_strategy", nullable = false, length = 32)
    @Comment("鉴权策略。")
    private AuthStrategy authStrategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "path_strategy", nullable = false, length = 32)
    @Comment("路径策略。")
    private PathStrategy pathStrategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "model_addressing_strategy", nullable = false, length = 32)
    @Comment("模型寻址策略。")
    private ModelAddressingStrategy modelAddressingStrategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_schema_strategy", nullable = false, length = 32)
    @Comment("错误结构策略。")
    private ErrorSchemaStrategy errorSchemaStrategy;

    @Column(name = "stream_transport", length = 32)
    @Comment("流式传输策略。")
    private String streamTransport;

    @Column(name = "conversation_profile_json", columnDefinition = "text")
    @Comment("对话兼容画像 JSON。")
    private String conversationProfileJson;

    @Column(name = "is_active", nullable = false)
    @Comment("是否启用。")
    private boolean active = true;

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

    public Long getSiteProfileId() {
        return siteProfileId;
    }

    public void setSiteProfileId(Long siteProfileId) {
        this.siteProfileId = siteProfileId;
    }

    public String getEndpointCode() {
        return endpointCode;
    }

    public void setEndpointCode(String endpointCode) {
        this.endpointCode = endpointCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getProtocolSuite() {
        return protocolSuite;
    }

    public void setProtocolSuite(String protocolSuite) {
        this.protocolSuite = protocolSuite;
    }

    public ProviderType getProviderType() {
        return providerType;
    }

    public void setProviderType(ProviderType providerType) {
        this.providerType = providerType;
    }

    public UpstreamSiteKind getSiteKind() {
        return siteKind;
    }

    public void setSiteKind(UpstreamSiteKind siteKind) {
        this.siteKind = siteKind;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public AuthStrategy getAuthStrategy() {
        return authStrategy;
    }

    public void setAuthStrategy(AuthStrategy authStrategy) {
        this.authStrategy = authStrategy;
    }

    public PathStrategy getPathStrategy() {
        return pathStrategy;
    }

    public void setPathStrategy(PathStrategy pathStrategy) {
        this.pathStrategy = pathStrategy;
    }

    public ModelAddressingStrategy getModelAddressingStrategy() {
        return modelAddressingStrategy;
    }

    public void setModelAddressingStrategy(ModelAddressingStrategy modelAddressingStrategy) {
        this.modelAddressingStrategy = modelAddressingStrategy;
    }

    public ErrorSchemaStrategy getErrorSchemaStrategy() {
        return errorSchemaStrategy;
    }

    public void setErrorSchemaStrategy(ErrorSchemaStrategy errorSchemaStrategy) {
        this.errorSchemaStrategy = errorSchemaStrategy;
    }

    public String getStreamTransport() {
        return streamTransport;
    }

    public void setStreamTransport(String streamTransport) {
        this.streamTransport = streamTransport;
    }

    public String getConversationProfileJson() {
        return conversationProfileJson;
    }

    public void setConversationProfileJson(String conversationProfileJson) {
        this.conversationProfileJson = conversationProfileJson;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
