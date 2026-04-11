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
        name = "system_setting",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_system_setting_setting_key", columnNames = "setting_key")
        },
        indexes = {
                @Index(name = "idx_system_setting_value_type", columnList = "value_type"),
                @Index(name = "idx_system_setting_updated_at", columnList = "updated_at")
        }
)
@Comment("系统设置表，保存网关运行时的持久化配置项。")
public class SystemSettingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    @Comment("主键。")
    private Long id;

    @Column(name = "setting_key", nullable = false, length = 128)
    @Comment("配置键，使用稳定的业务名称。")
    private String settingKey;

    @Column(name = "setting_value", nullable = false, columnDefinition = "text")
    @Comment("配置值，序列化后保存。")
    private String settingValue;

    @Column(name = "value_type", nullable = false, length = 32)
    @Comment("配置值类型，例如 string、json、boolean。")
    private String valueType;

    @Column(name = "description", length = 512)
    @Comment("配置项说明。")
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

    public String getSettingKey() {
        return settingKey;
    }

    public void setSettingKey(String settingKey) {
        this.settingKey = settingKey;
    }

    public String getSettingValue() {
        return settingValue;
    }

    public void setSettingValue(String settingValue) {
        this.settingValue = settingValue;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
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
