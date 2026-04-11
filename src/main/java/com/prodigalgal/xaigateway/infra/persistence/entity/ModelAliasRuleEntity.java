package com.prodigalgal.xaigateway.infra.persistence.entity;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import jakarta.persistence.Column;
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
import java.time.Instant;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "model_alias_rule",
        indexes = {
                @Index(name = "idx_model_alias_rule_alias_enabled", columnList = "alias_id,enabled"),
                @Index(name = "idx_model_alias_rule_protocol_priority", columnList = "protocol,priority"),
                @Index(name = "idx_model_alias_rule_target_model_key", columnList = "target_model_key")
        }
)
@Comment("统一模型别名规则表。")
public class ModelAliasRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    @Comment("主键。")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alias_id", nullable = false)
    @Comment("所属别名。")
    private ModelAliasEntity alias;

    @Column(name = "protocol", nullable = false, length = 32)
    @Comment("适用协议。")
    private String protocol;

    @Column(name = "target_model_name", nullable = false, length = 256)
    @Comment("目标模型名称。")
    private String targetModelName;

    @Column(name = "target_model_key", nullable = false, length = 256)
    @Comment("目标模型规范化键。")
    private String targetModelKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", length = 32)
    @Comment("可选的厂商过滤条件。")
    private ProviderType providerType;

    @Column(name = "base_url_pattern", length = 256)
    @Comment("可选的 baseUrl 正则匹配。")
    private String baseUrlPattern;

    @Column(name = "priority", nullable = false)
    @Comment("规则优先级，数值越小越优先。")
    private int priority = 100;

    @Column(name = "enabled", nullable = false)
    @Comment("规则是否启用。")
    private boolean enabled = true;

    @Column(name = "description", length = 512)
    @Comment("规则说明。")
    private String description;

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

    public ModelAliasEntity getAlias() {
        return alias;
    }

    public void setAlias(ModelAliasEntity alias) {
        this.alias = alias;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getTargetModelName() {
        return targetModelName;
    }

    public void setTargetModelName(String targetModelName) {
        this.targetModelName = targetModelName;
    }

    public String getTargetModelKey() {
        return targetModelKey;
    }

    public void setTargetModelKey(String targetModelKey) {
        this.targetModelKey = targetModelKey;
    }

    public ProviderType getProviderType() {
        return providerType;
    }

    public void setProviderType(ProviderType providerType) {
        this.providerType = providerType;
    }

    public String getBaseUrlPattern() {
        return baseUrlPattern;
    }

    public void setBaseUrlPattern(String baseUrlPattern) {
        this.baseUrlPattern = baseUrlPattern;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
