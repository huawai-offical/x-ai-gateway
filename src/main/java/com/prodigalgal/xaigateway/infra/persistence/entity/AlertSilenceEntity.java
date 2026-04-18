package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "alert_silence",
        indexes = {
                @Index(name = "idx_alert_silence_enabled", columnList = "enabled"),
                @Index(name = "idx_alert_silence_event", columnList = "event_type,severity"),
                @Index(name = "idx_alert_silence_entity", columnList = "entity_type,entity_ref")
        }
)
public class AlertSilenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "silence_name", nullable = false, length = 128)
    private String silenceName;

    @Column(name = "event_type", length = 64)
    private String eventType;

    @Column(name = "severity", length = 16)
    private String severity;

    @Column(name = "entity_type", length = 64)
    private String entityType;

    @Column(name = "entity_ref", length = 128)
    private String entityRef;

    @Column(name = "starts_at", columnDefinition = "timestamp with time zone")
    private Instant startsAt;

    @Column(name = "ends_at", columnDefinition = "timestamp with time zone")
    private Instant endsAt;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "reason", length = 512)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getSilenceName() {
        return silenceName;
    }

    public void setSilenceName(String silenceName) {
        this.silenceName = silenceName;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
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

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(Instant endsAt) {
        this.endsAt = endsAt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
