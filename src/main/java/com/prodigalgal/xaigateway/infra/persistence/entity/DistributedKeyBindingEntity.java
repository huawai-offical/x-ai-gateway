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
        name = "distributed_key_binding",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_distributed_key_binding_pair", columnNames = {"distributed_key_id", "credential_id"})
        },
        indexes = {
                @Index(name = "idx_distributed_key_binding_key_active", columnList = "distributed_key_id,is_active"),
                @Index(name = "idx_distributed_key_binding_credential_active", columnList = "credential_id,is_active")
        }
)
@Comment("分发 key 与上游凭证的绑定关系。")
public class DistributedKeyBindingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    @Comment("主键。")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "distributed_key_id", nullable = false)
    @Comment("所属分发 key。")
    private DistributedKeyEntity distributedKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credential_id", nullable = false)
    @Comment("绑定的上游凭证。")
    private UpstreamCredentialEntity credential;

    @Column(name = "priority", nullable = false)
    @Comment("优先级，数值越小越优先。")
    private int priority = 100;

    @Column(name = "weight", nullable = false)
    @Comment("路由权重。")
    private int weight = 100;

    @Column(name = "is_active", nullable = false)
    @Comment("绑定是否启用。")
    private boolean active = true;

    @Column(name = "description", length = 512)
    @Comment("绑定说明。")
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

    public DistributedKeyEntity getDistributedKey() {
        return distributedKey;
    }

    public void setDistributedKey(DistributedKeyEntity distributedKey) {
        this.distributedKey = distributedKey;
    }

    public UpstreamCredentialEntity getCredential() {
        return credential;
    }

    public void setCredential(UpstreamCredentialEntity credential) {
        this.credential = credential;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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
