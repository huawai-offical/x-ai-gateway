package com.prodigalgal.xaigateway.infra.persistence.entity;

import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.infra.persistence.converter.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.util.List;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "site_capability_snapshot",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_site_capability_snapshot_site_profile", columnNames = "site_profile_id")
        },
        indexes = {
                @Index(name = "idx_site_capability_snapshot_health_state", columnList = "health_state"),
                @Index(name = "idx_site_capability_snapshot_refreshed_at", columnList = "refreshed_at")
        }
)
@Comment("站点级能力快照，承载协议、鉴权、错误语义、流式与健康状态。")
public class SiteCapabilitySnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    @Comment("主键。")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_profile_id", nullable = false)
    @Comment("关联的站点档案。")
    private UpstreamSiteProfileEntity siteProfile;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "supported_protocols_json", nullable = false, columnDefinition = "text")
    @Comment("站点级支持的前台协议列表。")
    private List<String> supportedProtocols = List.of();

    @Column(name = "supports_responses", nullable = false)
    @Comment("是否支持 responses 对象。")
    private boolean supportsResponses;

    @Column(name = "supports_embeddings", nullable = false)
    @Comment("是否支持 embeddings。")
    private boolean supportsEmbeddings;

    @Column(name = "supports_audio", nullable = false)
    @Comment("是否支持 audio 资源。")
    private boolean supportsAudio;

    @Column(name = "supports_images", nullable = false)
    @Comment("是否支持 images 资源。")
    private boolean supportsImages;

    @Column(name = "supports_moderation", nullable = false)
    @Comment("是否支持 moderation。")
    private boolean supportsModeration;

    @Column(name = "supports_files", nullable = false)
    @Comment("是否支持 files。")
    private boolean supportsFiles;

    @Column(name = "supports_uploads", nullable = false)
    @Comment("是否支持 uploads。")
    private boolean supportsUploads;

    @Column(name = "supports_batches", nullable = false)
    @Comment("是否支持 batches。")
    private boolean supportsBatches;

    @Column(name = "supports_tuning", nullable = false)
    @Comment("是否支持 tuning。")
    private boolean supportsTuning;

    @Column(name = "supports_realtime", nullable = false)
    @Comment("是否支持 realtime。")
    private boolean supportsRealtime;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_strategy", nullable = false, length = 32)
    @Comment("鉴权策略快照。")
    private AuthStrategy authStrategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "path_strategy", nullable = false, length = 32)
    @Comment("路径策略快照。")
    private PathStrategy pathStrategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_schema_strategy", nullable = false, length = 32)
    @Comment("错误结构策略快照。")
    private ErrorSchemaStrategy errorSchemaStrategy;

    @Column(name = "stream_transport", length = 32)
    @Comment("流式传输语义，例如 SSE、NDJSON。")
    private String streamTransport;

    @Column(name = "fallback_strategy", length = 128)
    @Comment("回退策略摘要。")
    private String fallbackStrategy;

    @Column(name = "health_state", nullable = false, length = 32)
    @Comment("健康状态。")
    private String healthState = "UNKNOWN";

    @Column(name = "blocked_reason", length = 512)
    @Comment("若当前站点被阻断，记录阻断原因。")
    private String blockedReason;

    @Column(name = "refreshed_at", columnDefinition = "timestamp with time zone")
    @Comment("最近一次刷新时间。")
    private Instant refreshedAt;

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

    public UpstreamSiteProfileEntity getSiteProfile() {
        return siteProfile;
    }

    public void setSiteProfile(UpstreamSiteProfileEntity siteProfile) {
        this.siteProfile = siteProfile;
    }

    public List<String> getSupportedProtocols() {
        return supportedProtocols;
    }

    public void setSupportedProtocols(List<String> supportedProtocols) {
        this.supportedProtocols = supportedProtocols;
    }

    public boolean isSupportsResponses() {
        return supportsResponses;
    }

    public void setSupportsResponses(boolean supportsResponses) {
        this.supportsResponses = supportsResponses;
    }

    public boolean isSupportsEmbeddings() {
        return supportsEmbeddings;
    }

    public void setSupportsEmbeddings(boolean supportsEmbeddings) {
        this.supportsEmbeddings = supportsEmbeddings;
    }

    public boolean isSupportsAudio() {
        return supportsAudio;
    }

    public void setSupportsAudio(boolean supportsAudio) {
        this.supportsAudio = supportsAudio;
    }

    public boolean isSupportsImages() {
        return supportsImages;
    }

    public void setSupportsImages(boolean supportsImages) {
        this.supportsImages = supportsImages;
    }

    public boolean isSupportsModeration() {
        return supportsModeration;
    }

    public void setSupportsModeration(boolean supportsModeration) {
        this.supportsModeration = supportsModeration;
    }

    public boolean isSupportsFiles() {
        return supportsFiles;
    }

    public void setSupportsFiles(boolean supportsFiles) {
        this.supportsFiles = supportsFiles;
    }

    public boolean isSupportsUploads() {
        return supportsUploads;
    }

    public void setSupportsUploads(boolean supportsUploads) {
        this.supportsUploads = supportsUploads;
    }

    public boolean isSupportsBatches() {
        return supportsBatches;
    }

    public void setSupportsBatches(boolean supportsBatches) {
        this.supportsBatches = supportsBatches;
    }

    public boolean isSupportsTuning() {
        return supportsTuning;
    }

    public void setSupportsTuning(boolean supportsTuning) {
        this.supportsTuning = supportsTuning;
    }

    public boolean isSupportsRealtime() {
        return supportsRealtime;
    }

    public void setSupportsRealtime(boolean supportsRealtime) {
        this.supportsRealtime = supportsRealtime;
    }

    public AuthStrategy getAuthStrategy() {
        return authStrategy;
    }

    public void setAuthStrategy(AuthStrategy authStrategy) {
        this.authStrategy = authStrategy;
    }

    public PathStrategy getPathStrategy() {
        return pathStrategy;
    }

    public void setPathStrategy(PathStrategy pathStrategy) {
        this.pathStrategy = pathStrategy;
    }

    public ErrorSchemaStrategy getErrorSchemaStrategy() {
        return errorSchemaStrategy;
    }

    public void setErrorSchemaStrategy(ErrorSchemaStrategy errorSchemaStrategy) {
        this.errorSchemaStrategy = errorSchemaStrategy;
    }

    public String getStreamTransport() {
        return streamTransport;
    }

    public void setStreamTransport(String streamTransport) {
        this.streamTransport = streamTransport;
    }

    public String getFallbackStrategy() {
        return fallbackStrategy;
    }

    public void setFallbackStrategy(String fallbackStrategy) {
        this.fallbackStrategy = fallbackStrategy;
    }

    public String getHealthState() {
        return healthState;
    }

    public void setHealthState(String healthState) {
        this.healthState = healthState;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public void setBlockedReason(String blockedReason) {
        this.blockedReason = blockedReason;
    }

    public Instant getRefreshedAt() {
        return refreshedAt;
    }

    public void setRefreshedAt(Instant refreshedAt) {
        this.refreshedAt = refreshedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
