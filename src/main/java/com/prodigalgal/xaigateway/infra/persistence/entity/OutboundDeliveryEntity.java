package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "outbound_delivery",
        indexes = {
                @Index(name = "idx_outbound_delivery_status", columnList = "delivery_status,next_retry_at"),
                @Index(name = "idx_outbound_delivery_event", columnList = "event_type,occurred_at"),
                @Index(name = "idx_outbound_delivery_request", columnList = "request_id"),
                @Index(name = "idx_outbound_delivery_entity", columnList = "entity_type,entity_ref")
        }
)
public class OutboundDeliveryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Column(name = "entity_type", length = 64)
    private String entityType;

    @Column(name = "entity_ref", length = 128)
    private String entityRef;

    @Column(name = "request_id", length = 128)
    private String requestId;

    @Column(name = "gateway_resource_key", length = 128)
    private String gatewayResourceKey;

    @Column(name = "upstream_object_id", length = 128)
    private String upstreamObjectId;

    @Column(name = "delivery_status", nullable = false, length = 32)
    private String deliveryStatus;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_retry_at", columnDefinition = "timestamp with time zone")
    private Instant nextRetryAt;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "response_summary", length = 1024)
    private String responseSummary;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "occurred_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant occurredAt;

    @Column(name = "delivered_at", columnDefinition = "timestamp with time zone")
    private Instant deliveredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Long getChannelId() {
        return channelId;
    }

    public void setChannelId(Long channelId) {
        this.channelId = channelId;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityRef() {
        return entityRef;
    }

    public void setEntityRef(String entityRef) {
        this.entityRef = entityRef;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getGatewayResourceKey() {
        return gatewayResourceKey;
    }

    public void setGatewayResourceKey(String gatewayResourceKey) {
        this.gatewayResourceKey = gatewayResourceKey;
    }

    public String getUpstreamObjectId() {
        return upstreamObjectId;
    }

    public void setUpstreamObjectId(String upstreamObjectId) {
        this.upstreamObjectId = upstreamObjectId;
    }

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(String deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Integer getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(Integer responseCode) {
        this.responseCode = responseCode;
    }

    public String getResponseSummary() {
        return responseSummary;
    }

    public void setResponseSummary(String responseSummary) {
        this.responseSummary = responseSummary;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
