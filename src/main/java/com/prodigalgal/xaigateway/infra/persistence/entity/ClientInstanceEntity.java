package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "client_instance",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_client_instance_key_instance", columnNames = {"distributed_key_id", "instance_id"})
        },
        indexes = {
                @Index(name = "idx_client_instance_key_status", columnList = "distributed_key_id,status"),
                @Index(name = "idx_client_instance_family", columnList = "client_family"),
                @Index(name = "idx_client_instance_last_seen", columnList = "last_seen_at")
        }
)
@Comment("云端管理的 CLI / IDE 客户端实例。")
public class ClientInstanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "distributed_key_id", nullable = false)
    private DistributedKeyEntity distributedKey;

    @Column(name = "instance_id", nullable = false, length = 128)
    private String instanceId;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "client_family", nullable = false, length = 64)
    private String clientFamily;

    @Column(name = "workspace_hint", length = 256)
    private String workspaceHint;

    @Column(name = "plugin_name", length = 128)
    private String pluginName;

    @Column(name = "plugin_version", length = 64)
    private String pluginVersion;

    @Column(name = "deep_link_scheme", length = 64)
    private String deepLinkScheme;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "last_authorized_at", columnDefinition = "timestamp with time zone")
    private Instant lastAuthorizedAt;

    @Column(name = "last_seen_at", columnDefinition = "timestamp with time zone")
    private Instant lastSeenAt;

    @Column(name = "last_request_at", columnDefinition = "timestamp with time zone")
    private Instant lastRequestAt;

    @Column(name = "last_request_id", length = 64)
    private String lastRequestId;

    @Column(name = "disabled_at", columnDefinition = "timestamp with time zone")
    private Instant disabledAt;

    @Column(name = "revoked_at", columnDefinition = "timestamp with time zone")
    private Instant revokedAt;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public DistributedKeyEntity getDistributedKey() {
        return distributedKey;
    }

    public void setDistributedKey(DistributedKeyEntity distributedKey) {
        this.distributedKey = distributedKey;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getClientFamily() {
        return clientFamily;
    }

    public void setClientFamily(String clientFamily) {
        this.clientFamily = clientFamily;
    }

    public String getWorkspaceHint() {
        return workspaceHint;
    }

    public void setWorkspaceHint(String workspaceHint) {
        this.workspaceHint = workspaceHint;
    }

    public String getPluginName() {
        return pluginName;
    }

    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
    }

    public String getPluginVersion() {
        return pluginVersion;
    }

    public void setPluginVersion(String pluginVersion) {
        this.pluginVersion = pluginVersion;
    }

    public String getDeepLinkScheme() {
        return deepLinkScheme;
    }

    public void setDeepLinkScheme(String deepLinkScheme) {
        this.deepLinkScheme = deepLinkScheme;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getLastAuthorizedAt() {
        return lastAuthorizedAt;
    }

    public void setLastAuthorizedAt(Instant lastAuthorizedAt) {
        this.lastAuthorizedAt = lastAuthorizedAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Instant getLastRequestAt() {
        return lastRequestAt;
    }

    public void setLastRequestAt(Instant lastRequestAt) {
        this.lastRequestAt = lastRequestAt;
    }

    public String getLastRequestId() {
        return lastRequestId;
    }

    public void setLastRequestId(String lastRequestId) {
        this.lastRequestId = lastRequestId;
    }

    public Instant getDisabledAt() {
        return disabledAt;
    }

    public void setDisabledAt(Instant disabledAt) {
        this.disabledAt = disabledAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status) && revokedAt == null;
    }

    public boolean isRevoked() {
        return revokedAt != null || "REVOKED".equalsIgnoreCase(status);
    }
}
