package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "cost_model")
public class CostModelEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "provider_type", nullable = false, length = 96)
    private String providerType;
    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName;
    @Column(name = "currency", nullable = false, length = 16)
    private String currency;
    @Column(name = "input_token_micros", nullable = false)
    private long inputTokenMicros;
    @Column(name = "output_token_micros", nullable = false)
    private long outputTokenMicros;
    @Column(name = "cache_hit_token_micros", nullable = false)
    private long cacheHitTokenMicros;
    @Column(name = "active", nullable = false)
    private boolean active;
    @Column(name = "notes", length = 512)
    private String notes;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() { return id; }
    public String getProviderType() { return providerType; }
    public void setProviderType(String providerType) { this.providerType = providerType; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public long getInputTokenMicros() { return inputTokenMicros; }
    public void setInputTokenMicros(long inputTokenMicros) { this.inputTokenMicros = inputTokenMicros; }
    public long getOutputTokenMicros() { return outputTokenMicros; }
    public void setOutputTokenMicros(long outputTokenMicros) { this.outputTokenMicros = outputTokenMicros; }
    public long getCacheHitTokenMicros() { return cacheHitTokenMicros; }
    public void setCacheHitTokenMicros(long cacheHitTokenMicros) { this.cacheHitTokenMicros = cacheHitTokenMicros; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
