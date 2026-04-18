package com.prodigalgal.xaigateway.admin.application.integrations;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.OutboundEventSubscriptionEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.OutboundEventSubscriptionRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutboundSubscriptionServiceTests {

    @Test
    void shouldMatchByEventSeverityProviderAndSite() {
        OutboundEventSubscriptionRepository repository = Mockito.mock(OutboundEventSubscriptionRepository.class);
        OutboundSubscriptionService service = new OutboundSubscriptionService(repository);

        OutboundEventSubscriptionEntity hit = new OutboundEventSubscriptionEntity();
        hit.setChannelId(1L);
        hit.setEventType("ALERT_OPENED");
        hit.setSeverity("HIGH");
        hit.setProviderType("OPENAI_DIRECT");
        hit.setSiteProfileId(7L);
        hit.setEnabled(true);

        OutboundEventSubscriptionEntity miss = new OutboundEventSubscriptionEntity();
        miss.setChannelId(2L);
        miss.setEventType("ALERT_OPENED");
        miss.setSeverity("LOW");
        miss.setEnabled(true);

        Mockito.when(repository.findAllByEnabledTrueOrderByCreatedAtDesc()).thenReturn(List.of(hit, miss));

        List<OutboundEventSubscriptionEntity> matches = service.match(new OutboundEventEnvelope(
                "evt-1",
                "ALERT_OPENED",
                Instant.now(),
                "HIGH",
                "OPS_ALERT",
                "CREDENTIAL",
                "101",
                ProviderType.OPENAI_DIRECT,
                7L,
                101L,
                null,
                "req-1",
                null,
                null,
                "summary",
                java.util.Map.of(),
                null,
                null,
                "x-ai-gateway"
        ));

        assertEquals(1, matches.size());
        assertEquals(1L, matches.getFirst().getChannelId());
    }
}
