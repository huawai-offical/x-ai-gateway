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
        name = "audit_log",
        indexes = {
                @Index(name = "idx_audit_log_type_created", columnList = "audit_type,created_at"),
                @Index(name = "idx_audit_log_request_created", columnList = "request_id,created_at"),
                @Index(name = "idx_audit_log_target_created", columnList = "target_type,target_id,created_at")
        }
)
@Comment("后台操作与网关事件审计日志。")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "audit_type", nullable = false, length = 32)
    private String auditType;

    @Column(name = "action", nullable = false, length = 128)
    private String action;

    @Column(name = "target_type", nullable = false, length = 64)
    private String targetType;

    @Column(name = "target_id", length = 256)
    private String targetId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "actor", nullable = false, length = 128)
    private String actor;

    @Column(name = "path", length = 512)
    private String path;

    @Column(name = "detail_json", columnDefinition = "text")
    private String detailJson;

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

    public String getAuditType() {
        return auditType;
    }

    public void setAuditType(String auditType) {
        this.auditType = auditType;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public void setDetailJson(String detailJson) {
        this.detailJson = detailJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
