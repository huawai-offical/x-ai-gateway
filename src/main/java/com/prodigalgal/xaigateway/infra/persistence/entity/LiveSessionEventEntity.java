package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
        name = "live_session_event",
        uniqueConstraints = @UniqueConstraint(name = "uk_live_session_event_session_event", columnNames = {"session_id", "event_id"}))
public class LiveSessionEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private LiveSessionEntity session;
    @Column(name = "event_id", nullable = false)
    private long eventId;
    @Column(name = "event_type", nullable = false, length = 96)
    private String eventType;
    @Column(name = "direction", nullable = false, length = 32)
    private String direction;
    @Column(name = "payload_json", columnDefinition = "text")
    private String payloadJson;
    @Column(name = "audio_bytes", nullable = false)
    private long audioBytes;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    public Long getId() { return id; }
    public LiveSessionEntity getSession() { return session; }
    public void setSession(LiveSessionEntity session) { this.session = session; }
    public long getEventId() { return eventId; }
    public void setEventId(long eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public long getAudioBytes() { return audioBytes; }
    public void setAudioBytes(long audioBytes) { this.audioBytes = audioBytes; }
    public Instant getCreatedAt() { return createdAt; }
}
