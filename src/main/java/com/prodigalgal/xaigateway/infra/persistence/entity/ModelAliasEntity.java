package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "model_alias",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_model_alias_alias_key", columnNames = "alias_key")
        },
        indexes = {
                @Index(name = "idx_model_alias_enabled", columnList = "enabled"),
                @Index(name = "idx_model_alias_updated_at", columnList = "updated_at")
        }
)
@Comment("统一模型别名表。")
public class ModelAliasEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    @Comment("主键。")
    private Long id;

    @Column(name = "alias_name", nullable = false, length = 128)
    @Comment("显示用别名。")
    private String aliasName;

    @Column(name = "alias_key", nullable = false, length = 128)
    @Comment("规范化后的别名键，用于唯一匹配。")
    private String aliasKey;

    @Column(name = "enabled", nullable = false)
    @Comment("是否启用。")
    private boolean enabled = true;

    @Column(name = "description", length = 512)
    @Comment("别名说明。")
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

    public String getAliasName() {
        return aliasName;
    }

    public void setAliasName(String aliasName) {
        this.aliasName = aliasName;
    }

    public String getAliasKey() {
        return aliasKey;
    }

    public void setAliasKey(String aliasKey) {
        this.aliasKey = aliasKey;
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
