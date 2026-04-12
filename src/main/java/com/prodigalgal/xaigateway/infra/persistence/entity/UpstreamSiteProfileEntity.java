package com.prodigalgal.xaigateway.infra.persistence.entity;

import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelAddressingStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
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
        name = "upstream_site_profile",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_upstream_site_profile_code", columnNames = "profile_code")
        },
        indexes = {
                @Index(name = "idx_upstream_site_profile_family_active", columnList = "provider_family,is_active"),
                @Index(name = "idx_upstream_site_profile_kind_active", columnList = "site_kind,is_active")
        }
)
@Comment("上游站点档案，统一承载 provider family、鉴权策略、路径策略与错误语义。")
public class UpstreamSiteProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    @Comment("主键。")
    private Long id;

    @Column(name = "profile_code", nullable = false, length = 64)
    @Comment("站点档案编码。")
    private String profileCode;

    @Column(name = "display_name", nullable = false, length = 128)
    @Comment("站点档案显示名称。")
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_family", nullable = false, length = 32)
    @Comment("Provider family。")
    private ProviderFamily providerFamily;

    @Enumerated(EnumType.STRING)
    @Column(name = "site_kind", nullable = false, length = 64)
    @Comment("站点档案类型。")
    private UpstreamSiteKind siteKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_strategy", nullable = false, length = 32)
    @Comment("鉴权策略。")
    private AuthStrategy authStrategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "path_strategy", nullable = false, length = 32)
    @Comment("路径拼装策略。")
    private PathStrategy pathStrategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "model_addressing_strategy", nullable = false, length = 32)
    @Comment("模型寻址策略。")
    private ModelAddressingStrategy modelAddressingStrategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_schema_strategy", nullable = false, length = 32)
    @Comment("错误结构解析策略。")
    private ErrorSchemaStrategy errorSchemaStrategy;

    @Column(name = "base_url_pattern", length = 512)
    @Comment("匹配 baseUrl 的正则或模式。")
    private String baseUrlPattern;

    @Column(name = "description", length = 512)
    @Comment("备注说明。")
    private String description;

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

    public String getProfileCode() {
        return profileCode;
    }

    public void setProfileCode(String profileCode) {
        this.profileCode = profileCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public ProviderFamily getProviderFamily() {
        return providerFamily;
    }

    public void setProviderFamily(ProviderFamily providerFamily) {
        this.providerFamily = providerFamily;
    }

    public UpstreamSiteKind getSiteKind() {
        return siteKind;
    }

    public void setSiteKind(UpstreamSiteKind siteKind) {
        this.siteKind = siteKind;
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

    public String getBaseUrlPattern() {
        return baseUrlPattern;
    }

    public void setBaseUrlPattern(String baseUrlPattern) {
        this.baseUrlPattern = baseUrlPattern;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
