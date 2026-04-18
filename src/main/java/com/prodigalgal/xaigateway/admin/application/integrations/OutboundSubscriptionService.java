package com.prodigalgal.xaigateway.admin.application.integrations;

import com.prodigalgal.xaigateway.infra.persistence.entity.OutboundEventSubscriptionEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.OutboundEventSubscriptionRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OutboundSubscriptionService {

    private final OutboundEventSubscriptionRepository outboundEventSubscriptionRepository;

    public OutboundSubscriptionService(OutboundEventSubscriptionRepository outboundEventSubscriptionRepository) {
        this.outboundEventSubscriptionRepository = outboundEventSubscriptionRepository;
    }

    public List<OutboundEventSubscriptionEntity> match(OutboundEventEnvelope envelope) {
        return outboundEventSubscriptionRepository.findAllByEnabledTrueOrderByCreatedAtDesc().stream()
                .filter(subscription -> matches(subscription.getEventType(), envelope.eventType()))
                .filter(subscription -> matches(subscription.getSeverity(), envelope.severity()))
                .filter(subscription -> matches(subscription.getEntityType(), envelope.entityType()))
                .filter(subscription -> matches(subscription.getProviderType(), envelope.providerType() == null ? null : envelope.providerType().name()))
                .filter(subscription -> subscription.getSiteProfileId() == null || subscription.getSiteProfileId().equals(envelope.siteProfileId()))
                .toList();
    }

    private boolean matches(String expected, String actual) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        if (actual == null || actual.isBlank()) {
            return false;
        }
        return expected.trim().toUpperCase(Locale.ROOT).equals(actual.trim().toUpperCase(Locale.ROOT));
    }
}
