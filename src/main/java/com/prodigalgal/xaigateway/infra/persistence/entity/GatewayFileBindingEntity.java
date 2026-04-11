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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "gateway_file_binding",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_gateway_file_binding_scope", columnNames = {"gateway_file_id", "provider_type", "external_file_id"})
        },
        indexes = {
                @Index(name = "idx_gateway_file_binding_file_created", columnList = "gateway_file_id,created_at"),
                @Index(name = "idx_gateway_file_binding_credential_status", columnList = "credential_id,status")
        }
)
@Comment("网关文件对象与上游文件对象的绑定关系。")
public class GatewayFileBindingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    @Comment("主键。")
    private Long id;

    @Column(name = "gateway_file_id", nullable = false)
    @Comment("网关文件对象主键。")
    private Long gatewayFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 32)
    @Comment("上游厂商类型。")
    private ProviderType providerType;

    @Column(name = "credential_id", nullable = false)
    @Comment("上游凭证主键。")
    private Long credentialId;

    @Column(name = "external_file_id", nullable = false, length = 256)
    @Comment("上游文件对象 ID。")
    private String externalFileId;

    @Column(name = "external_filename", length = 256)
    @Comment("上游文件名。")
    private String externalFilename;

    @Column(name = "status", nullable = false, length = 32)
    @Comment("绑定状态，例如 ACTIVE、DELETED。")
    private String status;

    @Column(name = "last_synced_at", columnDefinition = "timestamp with time zone")
    @Comment("最近同步时间（UTC）。")
    private Instant lastSyncedAt;

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

    public Long getGatewayFileId() {
        return gatewayFileId;
    }

    public void setGatewayFileId(Long gatewayFileId) {
        this.gatewayFileId = gatewayFileId;
    }

    public ProviderType getProviderType() {
        return providerType;
    }

    public void setProviderType(ProviderType providerType) {
        this.providerType = providerType;
    }

    public Long getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(Long credentialId) {
        this.credentialId = credentialId;
    }

    public String getExternalFileId() {
        return externalFileId;
    }

    public void setExternalFileId(String externalFileId) {
        this.externalFileId = externalFileId;
    }

    public String getExternalFilename() {
        return externalFilename;
    }

    public void setExternalFilename(String externalFilename) {
        this.externalFilename = externalFilename;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
