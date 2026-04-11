package com.prodigalgal.xaigateway.infra.persistence.entity;

import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "gateway_async_resource",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_gateway_async_resource_resource_key", columnNames = "resource_key")
        },
        indexes = {
                @Index(name = "idx_gateway_async_resource_scope", columnList = "distributed_key_id,resource_type,created_at"),
                @Index(name = "idx_gateway_async_resource_status_created", columnList = "status,created_at")
        }
)
@Comment("网关异步资源与会话对象表。")
public class GatewayAsyncResourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "resource_key", nullable = false, length = 96)
    private String resourceKey;

    @Column(name = "distributed_key_id", nullable = false)
    private Long distributedKeyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 32)
    private GatewayAsyncResourceType resourceType;

    @Column(name = "request_model", length = 256)
    private String requestModel;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "request_payload_json", columnDefinition = "text")
    private String requestPayloadJson;

    @Column(name = "response_payload_json", columnDefinition = "text")
    private String responsePayloadJson;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getResourceKey() {
        return resourceKey;
    }

    public void setResourceKey(String resourceKey) {
        this.resourceKey = resourceKey;
    }

    public Long getDistributedKeyId() {
        return distributedKeyId;
    }

    public void setDistributedKeyId(Long distributedKeyId) {
        this.distributedKeyId = distributedKeyId;
    }

    public GatewayAsyncResourceType getResourceType() {
        return resourceType;
    }

    public void setResourceType(GatewayAsyncResourceType resourceType) {
        this.resourceType = resourceType;
    }

    public String getRequestModel() {
        return requestModel;
    }

    public void setRequestModel(String requestModel) {
        this.requestModel = requestModel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRequestPayloadJson() {
        return requestPayloadJson;
    }

    public void setRequestPayloadJson(String requestPayloadJson) {
        this.requestPayloadJson = requestPayloadJson;
    }

    public String getResponsePayloadJson() {
        return responsePayloadJson;
    }

    public void setResponsePayloadJson(String responsePayloadJson) {
        this.responsePayloadJson = responsePayloadJson;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
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
