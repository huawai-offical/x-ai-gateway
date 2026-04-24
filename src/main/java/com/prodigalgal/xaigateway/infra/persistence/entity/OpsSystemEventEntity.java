package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "ops_system_event")
public class OpsSystemEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_type", nullable = false, length = 96)
    private String eventType;
    @Column(name = "severity", nullable = false, length = 32)
    private String severity;
    @Column(name = "source", nullable = false, length = 96)
    private String source;
    @Column(name = "entity_type", length = 96)
    private String entityType;
    @Column(name = "entity_ref", length = 256)
    private String entityRef;
    @Column(name = "title", nullable = false, length = 256)
    private String title;
    @Column(name = "detail_json", columnDefinition = "text")
    private String detailJson;
    @Column(name = "occurred_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant occurredAt;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getEntityRef() { return entityRef; }
    public void setEntityRef(String entityRef) { this.entityRef = entityRef; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public Instant getCreatedAt() { return createdAt; }
}
