package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.IntegrationAdminService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/integrations")
public class IntegrationAdminController {

    private final IntegrationAdminService integrationAdminService;

    public IntegrationAdminController(IntegrationAdminService integrationAdminService) {
        this.integrationAdminService = integrationAdminService;
    }

    @GetMapping("/webhooks")
    public List<WebhookEndpointResponse> listWebhooks() {
        return integrationAdminService.listWebhooks();
    }

    @PostMapping("/webhooks")
    public WebhookEndpointResponse createWebhook(@Valid @RequestBody WebhookEndpointRequest request) {
        return integrationAdminService.saveWebhook(null, request);
    }

    @PutMapping("/webhooks/{id}")
    public WebhookEndpointResponse updateWebhook(@PathVariable Long id, @Valid @RequestBody WebhookEndpointRequest request) {
        return integrationAdminService.saveWebhook(id, request);
    }

    @GetMapping("/channels")
    public List<NotificationChannelResponse> listChannels() {
        return integrationAdminService.listChannels();
    }

    @PostMapping("/channels")
    public NotificationChannelResponse createChannel(@Valid @RequestBody NotificationChannelRequest request) {
        return integrationAdminService.saveChannel(null, request);
    }

    @PutMapping("/channels/{id}")
    public NotificationChannelResponse updateChannel(@PathVariable Long id, @Valid @RequestBody NotificationChannelRequest request) {
        return integrationAdminService.saveChannel(id, request);
    }

    @GetMapping("/runbooks")
    public List<RunbookLinkResponse> listRunbooks() {
        return integrationAdminService.listRunbooks();
    }

    @PostMapping("/runbooks")
    public RunbookLinkResponse createRunbook(@Valid @RequestBody RunbookLinkRequest request) {
        return integrationAdminService.saveRunbook(null, request);
    }

    @PutMapping("/runbooks/{id}")
    public RunbookLinkResponse updateRunbook(@PathVariable Long id, @Valid @RequestBody RunbookLinkRequest request) {
        return integrationAdminService.saveRunbook(id, request);
    }

    @GetMapping("/subscriptions")
    public List<OutboundEventSubscriptionResponse> listSubscriptions() {
        return integrationAdminService.listSubscriptions();
    }

    @PostMapping("/subscriptions")
    public OutboundEventSubscriptionResponse createSubscription(@Valid @RequestBody OutboundEventSubscriptionRequest request) {
        return integrationAdminService.saveSubscription(null, request);
    }

    @PutMapping("/subscriptions/{id}")
    public OutboundEventSubscriptionResponse updateSubscription(@PathVariable Long id, @Valid @RequestBody OutboundEventSubscriptionRequest request) {
        return integrationAdminService.saveSubscription(id, request);
    }

    @GetMapping("/deliveries")
    public List<OutboundDeliveryResponse> listDeliveries(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String deliveryStatus,
            @RequestParam(required = false) String channelType,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityRef,
            @RequestParam(required = false) String requestId) {
        return integrationAdminService.listDeliveries(eventType, deliveryStatus, channelType, entityType, entityRef, requestId);
    }

    @GetMapping("/deliveries/{id}")
    public OutboundDeliveryResponse getDelivery(@PathVariable Long id) {
        return integrationAdminService.getDelivery(id);
    }

    @PostMapping("/deliveries/{id}/replay")
    public OutboundDeliveryResponse replayDelivery(@PathVariable Long id) {
        return integrationAdminService.replayDelivery(id);
    }

    @PostMapping("/test-delivery")
    public OutboundDeliveryResponse testDelivery(@RequestBody TestDeliveryRequest request) {
        return integrationAdminService.testDelivery(request);
    }
}
