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

@Entity
@Table(
        name = "announcement_read_state",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_announcement_read_user", columnNames = {"announcement_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_announcement_read_user_read", columnList = "user_id,read_at")
        }
)
@Comment("门户公告已读状态。")
public class AnnouncementReadStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "announcement_id", nullable = false)
    private AnnouncementEntity announcement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private GatewayUserEntity user;

    @Column(name = "read_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public AnnouncementEntity getAnnouncement() {
        return announcement;
    }

    public void setAnnouncement(AnnouncementEntity announcement) {
        this.announcement = announcement;
    }

    public GatewayUserEntity getUser() {
        return user;
    }

    public void setUser(GatewayUserEntity user) {
        this.user = user;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
