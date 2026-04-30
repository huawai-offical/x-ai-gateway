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
        name = "distributed_key_secret_export_grant",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_key_secret_export_token_hash", columnNames = "token_hash")
        },
        indexes = {
                @Index(name = "idx_key_secret_export_key", columnList = "distributed_key_id"),
                @Index(name = "idx_key_secret_export_expires_at", columnList = "expires_at"),
                @Index(name = "idx_key_secret_export_consumed_at", columnList = "consumed_at"),
                @Index(name = "idx_key_secret_export_revoked_at", columnList = "revoked_at")
        }
)
@Comment("分发 Key 完整 secret 的一次性导出授权。")
public class DistributedKeySecretExportGrantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "distributed_key_id", nullable = false)
    @Comment("所属分发 Key。")
    private DistributedKeyEntity distributedKey;

    @Column(name = "token_hash", nullable = false, length = 128)
    @Comment("一次性下载 token 的哈希值，不保存明文 token。")
    private String tokenHash;

    @Column(name = "full_key_ciphertext", nullable = false, columnDefinition = "text")
    @Comment("完整分发 Key 的加密密文。")
    private String fullKeyCiphertext;

    @Column(name = "source_action", nullable = false, length = 32)
    @Comment("授权来源动作，如 CREATE 或 ROTATE。")
    private String sourceAction;

    @Column(name = "expires_at", nullable = false, columnDefinition = "timestamp with time zone")
    @Comment("过期时间（UTC）。")
    private Instant expiresAt;

    @Column(name = "consumed_at", columnDefinition = "timestamp with time zone")
    @Comment("消费时间（UTC）。")
    private Instant consumedAt;

    @Column(name = "revoked_at", columnDefinition = "timestamp with time zone")
    @Comment("撤销时间（UTC）。")
    private Instant revokedAt;

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

    public DistributedKeyEntity getDistributedKey() {
        return distributedKey;
    }

    public void setDistributedKey(DistributedKeyEntity distributedKey) {
        this.distributedKey = distributedKey;
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

    public String getSourceAction() {
        return sourceAction;
    }

    public void setSourceAction(String sourceAction) {
        this.sourceAction = sourceAction;
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
