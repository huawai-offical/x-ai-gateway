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
        name = "client_instance_grant",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_client_instance_grant_token_hash", columnNames = "token_hash")
        },
        indexes = {
                @Index(name = "idx_client_instance_grant_instance", columnList = "client_instance_id"),
                @Index(name = "idx_client_instance_grant_expires", columnList = "expires_at"),
                @Index(name = "idx_client_instance_grant_consumed", columnList = "consumed_at"),
                @Index(name = "idx_client_instance_grant_revoked", columnList = "revoked_at")
        }
)
@Comment("插件 / Deep Link 领取客户端配置的一次性授权。")
public class ClientInstanceGrantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_instance_id", nullable = false)
    private ClientInstanceEntity clientInstance;

    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    @Column(name = "full_key_ciphertext", nullable = false, columnDefinition = "text")
    private String fullKeyCiphertext;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "config_format", nullable = false, length = 32)
    private String configFormat;

    @Column(name = "base_url", nullable = false, length = 512)
    private String baseUrl;

    @Column(name = "expires_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant expiresAt;

    @Column(name = "consumed_at", columnDefinition = "timestamp with time zone")
    private Instant consumedAt;

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

    public ClientInstanceEntity getClientInstance() {
        return clientInstance;
    }

    public void setClientInstance(ClientInstanceEntity clientInstance) {
        this.clientInstance = clientInstance;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getFullKeyCiphertext() {
        return fullKeyCiphertext;
    }

    public void setFullKeyCiphertext(String fullKeyCiphertext) {
        this.fullKeyCiphertext = fullKeyCiphertext;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getConfigFormat() {
        return configFormat;
    }

    public void setConfigFormat(String configFormat) {
        this.configFormat = configFormat;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
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

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }
}
