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
        name = "distributed_key_access_group_grant",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_distributed_key_access_group_grant", columnNames = {"distributed_key_id", "access_group_id"})
        },
        indexes = {
                @Index(name = "idx_key_access_group_key", columnList = "distributed_key_id"),
                @Index(name = "idx_key_access_group_group", columnList = "access_group_id"),
                @Index(name = "idx_key_access_group_mode", columnList = "grant_mode")
        }
)
@Comment("分发 Key 的访问组授权。")
public class DistributedKeyAccessGroupGrantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "distributed_key_id", nullable = false)
    private DistributedKeyEntity distributedKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "access_group_id", nullable = false)
    private AccessGroupEntity accessGroup;

    @Column(name = "grant_mode", nullable = false, length = 24)
    private String grantMode = "INHERIT";

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "priority", nullable = false)
    private int priority = 100;

    @Column(name = "reason", length = 512)
    private String reason;

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

    public AccessGroupEntity getAccessGroup() {
        return accessGroup;
    }

    public void setAccessGroup(AccessGroupEntity accessGroup) {
        this.accessGroup = accessGroup;
    }

    public String getGrantMode() {
        return grantMode;
    }

    public void setGrantMode(String grantMode) {
        this.grantMode = grantMode;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
