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
        name = "notification_channel",
        indexes = {
                @Index(name = "idx_notification_channel_type", columnList = "channel_type,enabled")
        }
)
public class NotificationChannelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_name", nullable = false, length = 128)
    private String channelName;

    @Column(name = "channel_type", nullable = false, length = 32)
    private String channelType;

    @Column(name = "webhook_endpoint_id")
    private Long webhookEndpointId;

    @Column(name = "email_to", length = 1024)
    private String emailTo;

    @Column(name = "template_mode", nullable = false, length = 32)
    private String templateMode = "DEFAULT";

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

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getChannelType() {
        return channelType;
    }

    public void setChannelType(String channelType) {
        this.channelType = channelType;
    }

    public Long getWebhookEndpointId() {
        return webhookEndpointId;
    }

    public void setWebhookEndpointId(Long webhookEndpointId) {
        this.webhookEndpointId = webhookEndpointId;
    }

    public String getEmailTo() {
        return emailTo;
    }

    public void setEmailTo(String emailTo) {
        this.emailTo = emailTo;
    }

    public String getTemplateMode() {
        return templateMode;
    }

    public void setTemplateMode(String templateMode) {
        this.templateMode = templateMode;
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
