package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "dashboard_external_app")
public class DashboardExternalAppEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "app_name", nullable = false, length = 128)
    private String appName;
    @Column(name = "slug", nullable = false, length = 96, unique = true)
    private String slug;
    @Column(name = "iframe_url", nullable = false, length = 1024)
    private String iframeUrl;
    @Column(name = "allowed_origin", nullable = false, length = 512)
    private String allowedOrigin;
    @Column(name = "sandbox_permissions", length = 512)
    private String sandboxPermissions;
    @Column(name = "signing_secret_ciphertext", columnDefinition = "text")
    private String signingSecretCiphertext;
    @Column(name = "signing_secret_fingerprint", length = 128)
    private String signingSecretFingerprint;
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
    @Column(name = "nav_enabled", nullable = false)
    private boolean navEnabled = true;
    @Column(name = "description", length = 512)
    private String description;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() { return id; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getIframeUrl() { return iframeUrl; }
    public void setIframeUrl(String iframeUrl) { this.iframeUrl = iframeUrl; }
    public String getAllowedOrigin() { return allowedOrigin; }
    public void setAllowedOrigin(String allowedOrigin) { this.allowedOrigin = allowedOrigin; }
    public String getSandboxPermissions() { return sandboxPermissions; }
    public void setSandboxPermissions(String sandboxPermissions) { this.sandboxPermissions = sandboxPermissions; }
    public String getSigningSecretCiphertext() { return signingSecretCiphertext; }
    public void setSigningSecretCiphertext(String signingSecretCiphertext) { this.signingSecretCiphertext = signingSecretCiphertext; }
    public String getSigningSecretFingerprint() { return signingSecretFingerprint; }
    public void setSigningSecretFingerprint(String signingSecretFingerprint) { this.signingSecretFingerprint = signingSecretFingerprint; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isNavEnabled() { return navEnabled; }
    public void setNavEnabled(boolean navEnabled) { this.navEnabled = navEnabled; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
