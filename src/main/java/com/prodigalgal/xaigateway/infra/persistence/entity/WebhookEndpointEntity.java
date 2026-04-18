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
        name = "webhook_endpoint",
        indexes = {
                @Index(name = "idx_webhook_endpoint_enabled", columnList = "enabled")
        }
)
public class WebhookEndpointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "endpoint_name", nullable = false, length = 128)
    private String endpointName;

    @Column(name = "endpoint_url", nullable = false, length = 1024)
    private String endpointUrl;

    @Column(name = "secret_ciphertext", columnDefinition = "TEXT")
    private String secretCiphertext;

    @Column(name = "secret_fingerprint", length = 128)
    private String secretFingerprint;

    @Column(name = "signing_mode", nullable = false, length = 32)
    private String signingMode = "HMAC_SHA256";

    @Column(name = "timeout_ms", nullable = false)
    private Integer timeoutMs = 5000;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getEndpointName() {
        return endpointName;
    }

    public void setEndpointName(String endpointName) {
        this.endpointName = endpointName;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public String getSecretCiphertext() {
        return secretCiphertext;
    }

    public void setSecretCiphertext(String secretCiphertext) {
        this.secretCiphertext = secretCiphertext;
    }

    public String getSecretFingerprint() {
        return secretFingerprint;
    }

    public void setSecretFingerprint(String secretFingerprint) {
        this.secretFingerprint = secretFingerprint;
    }

    public String getSigningMode() {
        return signingMode;
    }

    public void setSigningMode(String signingMode) {
        this.signingMode = signingMode;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
