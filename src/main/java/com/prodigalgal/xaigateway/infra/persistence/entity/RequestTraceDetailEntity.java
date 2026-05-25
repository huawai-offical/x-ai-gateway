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
        name = "request_trace_detail",
        indexes = {
                @Index(name = "idx_request_trace_detail_request_created", columnList = "request_id,created_at"),
                @Index(name = "idx_request_trace_detail_stage_created", columnList = "stage,created_at"),
                @Index(name = "idx_request_trace_detail_expires_at", columnList = "expires_at")
        }
)
@Comment("请求详情追踪快照，按 request_id 串联下游请求、转换计划、上游请求响应与下游响应。")
public class RequestTraceDetailEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "stage", nullable = false, length = 64)
    private String stage;

    @Column(name = "direction", nullable = false, length = 32)
    private String direction;

    @Column(name = "content_kind", nullable = false, length = 32)
    private String contentKind;

    @Column(name = "payload_json", columnDefinition = "text")
    private String payloadJson;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;

    @Column(name = "payload_hash", nullable = false, length = 128)
    private String payloadHash;

    @Column(name = "metadata_hash", length = 128)
    private String metadataHash;

    @Column(name = "original_length", nullable = false)
    private int originalLength;

    @Column(name = "stored_length", nullable = false)
    private int storedLength;

    @Column(name = "metadata_original_length", nullable = false)
    private int metadataOriginalLength;

    @Column(name = "metadata_stored_length", nullable = false)
    private int metadataStoredLength;

    @Column(name = "truncated", nullable = false)
    private boolean truncated;

    @Column(name = "metadata_truncated", nullable = false)
    private boolean metadataTruncated;

    @Column(name = "redacted", nullable = false)
    private boolean redacted;

    @Column(name = "metadata_redacted", nullable = false)
    private boolean metadataRedacted;

    @Column(name = "expires_at", columnDefinition = "timestamp with time zone")
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getContentKind() {
        return contentKind;
    }

    public void setContentKind(String contentKind) {
        this.contentKind = contentKind;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }

    public String getMetadataHash() {
        return metadataHash;
    }

    public void setMetadataHash(String metadataHash) {
        this.metadataHash = metadataHash;
    }

    public int getOriginalLength() {
        return originalLength;
    }

    public void setOriginalLength(int originalLength) {
        this.originalLength = originalLength;
    }

    public int getStoredLength() {
        return storedLength;
    }

    public void setStoredLength(int storedLength) {
        this.storedLength = storedLength;
    }

    public int getMetadataOriginalLength() {
        return metadataOriginalLength;
    }

    public void setMetadataOriginalLength(int metadataOriginalLength) {
        this.metadataOriginalLength = metadataOriginalLength;
    }

    public int getMetadataStoredLength() {
        return metadataStoredLength;
    }

    public void setMetadataStoredLength(int metadataStoredLength) {
        this.metadataStoredLength = metadataStoredLength;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public boolean isMetadataTruncated() {
        return metadataTruncated;
    }

    public void setMetadataTruncated(boolean metadataTruncated) {
        this.metadataTruncated = metadataTruncated;
    }

    public boolean isRedacted() {
        return redacted;
    }

    public void setRedacted(boolean redacted) {
        this.redacted = redacted;
    }

    public boolean isMetadataRedacted() {
        return metadataRedacted;
    }

    public void setMetadataRedacted(boolean metadataRedacted) {
        this.metadataRedacted = metadataRedacted;
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
}
