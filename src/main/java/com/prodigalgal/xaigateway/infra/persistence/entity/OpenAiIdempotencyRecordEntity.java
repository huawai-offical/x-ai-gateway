package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "openai_idempotency_record",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_openai_idempotency_scope",
                        columnNames = {"distributed_key_id", "request_path", "idempotency_key"}
                )
        },
        indexes = {
                @Index(name = "idx_openai_idempotency_key_created", columnList = "distributed_key_id,created_at"),
                @Index(name = "idx_openai_idempotency_path_created", columnList = "request_path,created_at")
        }
)
@Comment("OpenAI-compatible Idempotency-Key 响应重放记录。")
public class OpenAiIdempotencyRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "distributed_key_id", nullable = false)
    private Long distributedKeyId;

    @Column(name = "request_path", nullable = false, length = 256)
    private String requestPath;

    @Column(name = "idempotency_key", nullable = false, length = 256)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 128)
    private String requestFingerprint;

    @Column(name = "response_status", nullable = false)
    private Integer responseStatus;

    @Column(name = "response_object_type", length = 128)
    private String responseObjectType;

    @Column(name = "response_payload_json", nullable = false, columnDefinition = "text")
    private String responsePayloadJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public Long getDistributedKeyId() {
        return distributedKeyId;
    }

    public void setDistributedKeyId(Long distributedKeyId) {
        this.distributedKeyId = distributedKeyId;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public void setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getResponseObjectType() {
        return responseObjectType;
    }

    public void setResponseObjectType(String responseObjectType) {
        this.responseObjectType = responseObjectType;
    }

    public String getResponsePayloadJson() {
        return responsePayloadJson;
    }

    public void setResponsePayloadJson(String responsePayloadJson) {
        this.responsePayloadJson = responsePayloadJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
