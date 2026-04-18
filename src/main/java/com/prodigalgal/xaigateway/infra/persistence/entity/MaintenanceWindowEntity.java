package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "maintenance_window")
public class MaintenanceWindowEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "window_name", nullable = false, length = 128)
    private String windowName;
    @Column(name = "scope_type", length = 32)
    private String scopeType;
    @Column(name = "scope_ref", length = 128)
    private String scopeRef;
    @Column(name = "starts_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant startsAt;
    @Column(name = "ends_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant endsAt;
    @Column(name = "enabled", nullable = false)
    private boolean enabled;
    @Column(name = "description", length = 512)
    private String description;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getWindowName() {
        return windowName;
    }

    public void setWindowName(String windowName) {
        this.windowName = windowName;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public String getScopeRef() {
        return scopeRef;
    }

    public void setScopeRef(String scopeRef) {
        this.scopeRef = scopeRef;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
