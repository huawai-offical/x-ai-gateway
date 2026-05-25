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
        name = "user_access_group_grant",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_access_group_grant_source", columnNames = {"source_type", "source_id"})
        },
        indexes = {
                @Index(name = "idx_user_access_group_grant_user", columnList = "user_id,status"),
                @Index(name = "idx_user_access_group_grant_group", columnList = "access_group_id")
        }
)
@Comment("用户级权益组赠品授权。")
public class UserAccessGroupGrantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private GatewayUserEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "access_group_id", nullable = false)
    private AccessGroupEntity accessGroup;

    @Column(name = "status", nullable = false, length = 24)
    private String status = "ACTIVE";

    @Column(name = "starts_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant startsAt;

    @Column(name = "expires_at", columnDefinition = "timestamp with time zone")
    private Instant expiresAt;

    @Column(name = "source_type", length = 64)
    private String sourceType;

    @Column(name = "source_id", length = 128)
    private String sourceId;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public GatewayUserEntity getUser() {
        return user;
    }

    public void setUser(GatewayUserEntity user) {
        this.user = user;
    }

    public AccessGroupEntity getAccessGroup() {
        return accessGroup;
    }

    public void setAccessGroup(AccessGroupEntity accessGroup) {
        this.accessGroup = accessGroup;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
