package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "live_session")
public class LiveSessionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "session_key", nullable = false, unique = true, length = 96)
    private String sessionKey;
    @Column(name = "distributed_key_id")
    private Long distributedKeyId;
    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName;
    @Column(name = "protocol", nullable = false, length = 64)
    private String protocol;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "resume_token", nullable = false, unique = true, length = 128)
    private String resumeToken;
    @Column(name = "last_event_id", nullable = false)
    private long lastEventId;
    @Column(name = "input_audio_bytes", nullable = false)
    private long inputAudioBytes;
    @Column(name = "output_audio_bytes", nullable = false)
    private long outputAudioBytes;
    @Column(name = "event_count", nullable = false)
    private long eventCount;
    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;
    @Column(name = "expires_at", columnDefinition = "timestamp with time zone")
    private Instant expiresAt;
    @Column(name = "closed_at", columnDefinition = "timestamp with time zone")
    private Instant closedAt;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() { return id; }
    public String getSessionKey() { return sessionKey; }
    public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }
    public Long getDistributedKeyId() { return distributedKeyId; }
    public void setDistributedKeyId(Long distributedKeyId) { this.distributedKeyId = distributedKeyId; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResumeToken() { return resumeToken; }
    public void setResumeToken(String resumeToken) { this.resumeToken = resumeToken; }
    public long getLastEventId() { return lastEventId; }
    public void setLastEventId(long lastEventId) { this.lastEventId = lastEventId; }
    public long getInputAudioBytes() { return inputAudioBytes; }
    public void setInputAudioBytes(long inputAudioBytes) { this.inputAudioBytes = inputAudioBytes; }
    public long getOutputAudioBytes() { return outputAudioBytes; }
    public void setOutputAudioBytes(long outputAudioBytes) { this.outputAudioBytes = outputAudioBytes; }
    public long getEventCount() { return eventCount; }
    public void setEventCount(long eventCount) { this.eventCount = eventCount; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
