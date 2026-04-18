package com.prodigalgal.xaigateway.admin.application.integrations;

import com.prodigalgal.xaigateway.admin.api.OutboundDeliveryResponse;
import com.prodigalgal.xaigateway.admin.application.RunbookLinkService;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OutboundEventService {

    private final OutboundSubscriptionService outboundSubscriptionService;
    private final OutboundDeliveryService outboundDeliveryService;
    private final RunbookLinkService runbookLinkService;
    private final GatewayProperties gatewayProperties;

    public OutboundEventService(
            OutboundSubscriptionService outboundSubscriptionService,
            OutboundDeliveryService outboundDeliveryService,
            RunbookLinkService runbookLinkService,
            GatewayProperties gatewayProperties) {
        this.outboundSubscriptionService = outboundSubscriptionService;
        this.outboundDeliveryService = outboundDeliveryService;
        this.runbookLinkService = runbookLinkService;
        this.gatewayProperties = gatewayProperties;
    }

    public List<OutboundDeliveryResponse> handlePlatformEvent(PlatformEvent event) {
        OutboundEventEnvelope envelope = toEnvelope(event);
        return outboundSubscriptionService.match(envelope).stream()
                .map(subscription -> outboundDeliveryService.createPendingDelivery(subscription.getChannelId(), envelope))
                .map(OutboundDeliveryResponse::id)
                .map(outboundDeliveryService::deliver)
                .toList();
    }

    public OutboundDeliveryResponse dispatchDirect(Long channelId, PlatformEvent event) {
        OutboundEventEnvelope envelope = toEnvelope(event);
        OutboundDeliveryResponse pending = outboundDeliveryService.createPendingDelivery(channelId, envelope);
        return outboundDeliveryService.deliver(pending.id());
    }

    private OutboundEventEnvelope toEnvelope(PlatformEvent event) {
        String traceUrl = resolveTraceUrl(event);
        String runbookUrl = runbookLinkService.resolveUrl(event.eventType().name(), event.entityType());
        return new OutboundEventEnvelope(
                event.eventId(),
                event.eventType().name(),
                event.occurredAt(),
                event.severity(),
                event.category(),
                event.entityType(),
                event.entityRef(),
                event.providerType(),
                event.siteProfileId(),
                event.credentialId(),
                event.accountId(),
                event.requestId(),
                event.gatewayResourceKey(),
                event.upstreamObjectId(),
                event.summary(),
                event.details(),
                traceUrl,
                runbookUrl,
                "x-ai-gateway"
        );
    }

    private String resolveTraceUrl(PlatformEvent event) {
        String publicBaseUrl = gatewayProperties.getWeb().getPublicBaseUrl();
        if (event.requestId() != null && !event.requestId().isBlank()) {
            return publicBaseUrl + "/traces?requestId=" + event.requestId();
        }
        if (event.gatewayResourceKey() != null && !event.gatewayResourceKey().isBlank()) {
            return publicBaseUrl + "/traces?gatewayResourceKey=" + event.gatewayResourceKey();
        }
        if (event.upstreamObjectId() != null && !event.upstreamObjectId().isBlank()) {
            return publicBaseUrl + "/traces?upstreamObjectId=" + event.upstreamObjectId();
        }
        return null;
    }
}
