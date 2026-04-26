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
import java.time.Instant;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "announcement",
        indexes = {
                @Index(name = "idx_announcement_status_published", columnList = "status,published_at"),
                @Index(name = "idx_announcement_audience", columnList = "audience_type,audience_user_id,audience_plan_id,audience_access_group_id")
        }
)
@Comment("门户公告。")
public class AnnouncementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "summary", length = 512)
    private String summary;

    @Column(name = "body", columnDefinition = "text")
    private String body;

    @Column(name = "status", nullable = false, length = 24)
    private String status = "DRAFT";

    @Column(name = "audience_type", nullable = false, length = 24)
    private String audienceType = "GLOBAL";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audience_user_id")
    private GatewayUserEntity audienceUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audience_plan_id")
    private SubscriptionPlanEntity audiencePlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audience_access_group_id")
    private AccessGroupEntity audienceAccessGroup;

    @Column(name = "published_at", columnDefinition = "timestamp with time zone")
    private Instant publishedAt;

    @Column(name = "expires_at", columnDefinition = "timestamp with time zone")
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAudienceType() {
        return audienceType;
    }

    public void setAudienceType(String audienceType) {
        this.audienceType = audienceType;
    }

    public GatewayUserEntity getAudienceUser() {
        return audienceUser;
    }

    public void setAudienceUser(GatewayUserEntity audienceUser) {
        this.audienceUser = audienceUser;
    }

    public SubscriptionPlanEntity getAudiencePlan() {
        return audiencePlan;
    }

    public void setAudiencePlan(SubscriptionPlanEntity audiencePlan) {
        this.audiencePlan = audiencePlan;
    }

    public AccessGroupEntity getAudienceAccessGroup() {
        return audienceAccessGroup;
    }

    public void setAudienceAccessGroup(AccessGroupEntity audienceAccessGroup) {
        this.audienceAccessGroup = audienceAccessGroup;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
