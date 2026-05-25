package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
        name = "request_trace_detail_archive",
        indexes = {
                @Index(name = "idx_request_trace_detail_archive_cutoff", columnList = "cutoff_at,created_at")
        }
)
@Comment("请求详情追踪 TTL 清理归档摘要，记录每次清理覆盖窗口、阶段计数和状态。")
public class RequestTraceDetailArchiveEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "archive_batch_id", nullable = false, length = 64)
    private String archiveBatchId;

    @Column(name = "cutoff_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant cutoffAt;

    @Column(name = "archived_count", nullable = false)
    private int archivedCount;

    @Column(name = "earliest_created_at", columnDefinition = "timestamp with time zone")
    private Instant earliestCreatedAt;

    @Column(name = "latest_created_at", columnDefinition = "timestamp with time zone")
    private Instant latestCreatedAt;

    @Column(name = "stage_counts_json", columnDefinition = "text")
    private String stageCountsJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public String getArchiveBatchId() {
        return archiveBatchId;
    }

    public void setArchiveBatchId(String archiveBatchId) {
        this.archiveBatchId = archiveBatchId;
    }

    public Instant getCutoffAt() {
        return cutoffAt;
    }

    public void setCutoffAt(Instant cutoffAt) {
        this.cutoffAt = cutoffAt;
    }

    public int getArchivedCount() {
        return archivedCount;
    }

    public void setArchivedCount(int archivedCount) {
        this.archivedCount = archivedCount;
    }

    public Instant getEarliestCreatedAt() {
        return earliestCreatedAt;
    }

    public void setEarliestCreatedAt(Instant earliestCreatedAt) {
        this.earliestCreatedAt = earliestCreatedAt;
    }

    public Instant getLatestCreatedAt() {
        return latestCreatedAt;
    }

    public void setLatestCreatedAt(Instant latestCreatedAt) {
        this.latestCreatedAt = latestCreatedAt;
    }

    public String getStageCountsJson() {
        return stageCountsJson;
    }

    public void setStageCountsJson(String stageCountsJson) {
        this.stageCountsJson = stageCountsJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
