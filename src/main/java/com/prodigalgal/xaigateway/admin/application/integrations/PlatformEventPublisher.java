package com.prodigalgal.xaigateway.admin.application.integrations;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class PlatformEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public PlatformEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publish(PlatformEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    public void publish(
            PlatformEventType eventType,
            String severity,
            String category,
            String entityType,
            String entityRef,
            ProviderType providerType,
            Long siteProfileId,
            Long credentialId,
            Long accountId,
            String requestId,
            String gatewayResourceKey,
            String upstreamObjectId,
            String summary,
            Map<String, Object> details) {
        publish(new PlatformEvent(
                UUID.randomUUID().toString(),
                eventType,
                Instant.now(),
                severity,
                category,
                entityType,
                entityRef,
                providerType,
                siteProfileId,
                credentialId,
                accountId,
                requestId,
                gatewayResourceKey,
                upstreamObjectId,
                summary,
                details == null ? Map.of() : new LinkedHashMap<>(details)
        ));
    }
}
