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
        name = "gateway_file",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_gateway_file_file_key", columnNames = "file_key")
        },
        indexes = {
                @Index(name = "idx_gateway_file_distributed_key_created", columnList = "distributed_key_id,created_at"),
                @Index(name = "idx_gateway_file_status_created", columnList = "status,created_at")
        }
)
@Comment("网关文件对象元数据表。")
public class GatewayFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    @Comment("主键。")
    private Long id;

    @Column(name = "file_key", nullable = false, length = 64)
    @Comment("对外暴露的文件键，例如 file-xxx。")
    private String fileKey;

    @Column(name = "distributed_key_id", nullable = false)
    @Comment("所属分发 key 主键。")
    private Long distributedKeyId;

    @Column(name = "filename", nullable = false, length = 256)
    @Comment("原始文件名。")
    private String filename;

    @Column(name = "mime_type", nullable = false, length = 128)
    @Comment("MIME 类型。")
    private String mimeType;

    @Column(name = "purpose", length = 64)
    @Comment("上传用途。")
    private String purpose;

    @Column(name = "size_bytes", nullable = false)
    @Comment("文件字节大小。")
    private long sizeBytes;

    @Column(name = "sha256", nullable = false, length = 64)
    @Comment("文件内容 SHA-256。")
    private String sha256;

    @Column(name = "storage_path", nullable = false, length = 512)
    @Comment("本地存储路径。")
    private String storagePath;

    @Column(name = "status", nullable = false, length = 32)
    @Comment("文件状态。")
    private String status;

    @Column(name = "deleted", nullable = false)
    @Comment("逻辑删除标记。")
    private boolean deleted = false;

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

    public String getFileKey() {
        return fileKey;
    }

    public void setFileKey(String fileKey) {
        this.fileKey = fileKey;
    }

    public Long getDistributedKeyId() {
        return distributedKeyId;
    }

    public void setDistributedKeyId(Long distributedKeyId) {
        this.distributedKeyId = distributedKeyId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
