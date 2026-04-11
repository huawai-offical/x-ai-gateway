package com.prodigalgal.xaigateway.infra.persistence.entity;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.infra.persistence.converter.StringListJsonConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "upstream_account_pool")
@Comment("上游账号池。")
public class UpstreamAccountPoolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pool_name", nullable = false, length = 128)
    private String poolName;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 32)
    private UpstreamAccountProviderType providerType;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "supported_models_json", columnDefinition = "text")
    private List<String> supportedModels = List.of();

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "supported_protocols_json", columnDefinition = "text")
    private List<String> supportedProtocols = List.of();

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "allowed_client_families_json", columnDefinition = "text")
    private List<String> allowedClientFamilies = List.of();

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "description", length = 512)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() { return id; }
    public String getPoolName() { return poolName; }
    public void setPoolName(String poolName) { this.poolName = poolName; }
    public UpstreamAccountProviderType getProviderType() { return providerType; }
    public void setProviderType(UpstreamAccountProviderType providerType) { this.providerType = providerType; }
    public List<String> getSupportedModels() { return supportedModels; }
    public void setSupportedModels(List<String> supportedModels) { this.supportedModels = supportedModels; }
    public List<String> getSupportedProtocols() { return supportedProtocols; }
    public void setSupportedProtocols(List<String> supportedProtocols) { this.supportedProtocols = supportedProtocols; }
    public List<String> getAllowedClientFamilies() { return allowedClientFamilies; }
    public void setAllowedClientFamilies(List<String> allowedClientFamilies) { this.allowedClientFamilies = allowedClientFamilies; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
