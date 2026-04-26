package com.prodigalgal.xaigateway.infra.persistence.entity;

import com.prodigalgal.xaigateway.infra.persistence.converter.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
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
        name = "access_group",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_access_group_name", columnNames = "group_name")
        },
        indexes = {
                @Index(name = "idx_access_group_active", columnList = "is_active"),
                @Index(name = "idx_access_group_priority", columnList = "priority")
        }
)
@Comment("访问组，用于套餐、公告和分发 Key 的权益编排。")
public class AccessGroupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_name", nullable = false, length = 128)
    private String groupName;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "priority", nullable = false)
    private int priority = 100;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "allowed_protocols_json", nullable = false, columnDefinition = "text")
    private List<String> allowedProtocols = List.of();

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "allowed_models_json", nullable = false, columnDefinition = "text")
    private List<String> allowedModels = List.of();

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "allowed_provider_types_json", nullable = false, columnDefinition = "text")
    private List<String> allowedProviderTypes = List.of();

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "allowed_client_families_json", nullable = false, columnDefinition = "text")
    private List<String> allowedClientFamilies = List.of();

    @Column(name = "rpm_limit")
    private Integer rpmLimit;

    @Column(name = "tpm_limit")
    private Integer tpmLimit;

    @Column(name = "concurrency_limit")
    private Integer concurrencyLimit;

    @Column(name = "daily_token_limit")
    private Long dailyTokenLimit;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
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

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public List<String> getAllowedProtocols() {
        return allowedProtocols;
    }

    public void setAllowedProtocols(List<String> allowedProtocols) {
        this.allowedProtocols = allowedProtocols;
    }

    public List<String> getAllowedModels() {
        return allowedModels;
    }

    public void setAllowedModels(List<String> allowedModels) {
        this.allowedModels = allowedModels;
    }

    public List<String> getAllowedProviderTypes() {
        return allowedProviderTypes;
    }

    public void setAllowedProviderTypes(List<String> allowedProviderTypes) {
        this.allowedProviderTypes = allowedProviderTypes;
    }

    public List<String> getAllowedClientFamilies() {
        return allowedClientFamilies;
    }

    public void setAllowedClientFamilies(List<String> allowedClientFamilies) {
        this.allowedClientFamilies = allowedClientFamilies;
    }

    public Integer getRpmLimit() {
        return rpmLimit;
    }

    public void setRpmLimit(Integer rpmLimit) {
        this.rpmLimit = rpmLimit;
    }

    public Integer getTpmLimit() {
        return tpmLimit;
    }

    public void setTpmLimit(Integer tpmLimit) {
        this.tpmLimit = tpmLimit;
    }

    public Integer getConcurrencyLimit() {
        return concurrencyLimit;
    }

    public void setConcurrencyLimit(Integer concurrencyLimit) {
        this.concurrencyLimit = concurrencyLimit;
    }

    public Long getDailyTokenLimit() {
        return dailyTokenLimit;
    }

    public void setDailyTokenLimit(Long dailyTokenLimit) {
        this.dailyTokenLimit = dailyTokenLimit;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
