package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.NotificationChannelRequest;
import com.prodigalgal.xaigateway.admin.api.NotificationChannelResponse;
import com.prodigalgal.xaigateway.admin.api.OutboundDeliveryResponse;
import com.prodigalgal.xaigateway.admin.api.OutboundEventSubscriptionRequest;
import com.prodigalgal.xaigateway.admin.api.OutboundEventSubscriptionResponse;
import com.prodigalgal.xaigateway.admin.api.RunbookLinkRequest;
import com.prodigalgal.xaigateway.admin.api.RunbookLinkResponse;
import com.prodigalgal.xaigateway.admin.api.TestDeliveryRequest;
import com.prodigalgal.xaigateway.admin.api.WebhookEndpointRequest;
import com.prodigalgal.xaigateway.admin.api.WebhookEndpointResponse;
import com.prodigalgal.xaigateway.admin.application.integrations.NotificationChannelType;
import com.prodigalgal.xaigateway.admin.application.integrations.OutboundDeliveryService;
import com.prodigalgal.xaigateway.admin.application.integrations.PlatformEvent;
import com.prodigalgal.xaigateway.admin.application.integrations.PlatformEventType;
import com.prodigalgal.xaigateway.admin.application.integrations.OutboundEventService;
import com.prodigalgal.xaigateway.admin.application.integrations.PlatformEventPublisher;
import com.prodigalgal.xaigateway.admin.application.integrations.WebhookSigningMode;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.NotificationChannelEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.OutboundEventSubscriptionEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.WebhookEndpointEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.NotificationChannelRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.OutboundEventSubscriptionRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.WebhookEndpointRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IntegrationAdminService {

    private final WebhookEndpointRepository webhookEndpointRepository;
    private final NotificationChannelRepository notificationChannelRepository;
    private final OutboundEventSubscriptionRepository outboundEventSubscriptionRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final RunbookLinkService runbookLinkService;
    private final OutboundDeliveryService outboundDeliveryService;
    private final OutboundEventService outboundEventService;

    public IntegrationAdminService(
            WebhookEndpointRepository webhookEndpointRepository,
            NotificationChannelRepository notificationChannelRepository,
            OutboundEventSubscriptionRepository outboundEventSubscriptionRepository,
            CredentialCryptoService credentialCryptoService,
            RunbookLinkService runbookLinkService,
            OutboundDeliveryService outboundDeliveryService,
            OutboundEventService outboundEventService) {
        this.webhookEndpointRepository = webhookEndpointRepository;
        this.notificationChannelRepository = notificationChannelRepository;
        this.outboundEventSubscriptionRepository = outboundEventSubscriptionRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.runbookLinkService = runbookLinkService;
        this.outboundDeliveryService = outboundDeliveryService;
        this.outboundEventService = outboundEventService;
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpointResponse> listWebhooks() {
        return webhookEndpointRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toWebhookResponse).toList();
    }

    public WebhookEndpointResponse saveWebhook(Long id, WebhookEndpointRequest request) {
        WebhookEndpointEntity entity = id == null
                ? new WebhookEndpointEntity()
                : webhookEndpointRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("未找到 webhook endpoint。"));
        entity.setEndpointName(requireText(request.endpointName(), "endpointName"));
        entity.setEndpointUrl(requireText(request.endpointUrl(), "endpointUrl"));
        if (request.secret() != null) {
            String secret = blankToNull(request.secret());
            entity.setSecretCiphertext(secret == null ? null : credentialCryptoService.encrypt(secret));
            entity.setSecretFingerprint(secret == null ? null : credentialCryptoService.fingerprint(secret));
        }
        entity.setSigningMode(normalizeEnum(request.signingMode(), WebhookSigningMode.HMAC_SHA256.name()));
        entity.setTimeoutMs(request.timeoutMs() == null || request.timeoutMs() <= 0 ? 5000 : request.timeoutMs());
        entity.setEnabled(request.enabled() == null || request.enabled());
        return toWebhookResponse(webhookEndpointRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<NotificationChannelResponse> listChannels() {
        return notificationChannelRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toChannelResponse).toList();
    }

    public NotificationChannelResponse saveChannel(Long id, NotificationChannelRequest request) {
        NotificationChannelEntity entity = id == null
                ? new NotificationChannelEntity()
                : notificationChannelRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("未找到 notification channel。"));
        String channelType = normalizeEnum(request.channelType(), null);
        entity.setChannelName(requireText(request.channelName(), "channelName"));
        entity.setChannelType(requireText(channelType, "channelType"));
        entity.setWebhookEndpointId(request.webhookEndpointId());
        entity.setEmailTo(blankToNull(request.emailTo()));
        entity.setTemplateMode(blankToNull(request.templateMode()) == null ? "DEFAULT" : request.templateMode().trim());
        entity.setEnabled(request.enabled() == null || request.enabled());
        validateChannel(entity);
        return toChannelResponse(notificationChannelRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<RunbookLinkResponse> listRunbooks() {
        return runbookLinkService.list();
    }

    public RunbookLinkResponse saveRunbook(Long id, RunbookLinkRequest request) {
        return runbookLinkService.save(id, request);
    }

    @Transactional(readOnly = true)
    public List<OutboundEventSubscriptionResponse> listSubscriptions() {
        return outboundEventSubscriptionRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toSubscriptionResponse).toList();
    }

    public OutboundEventSubscriptionResponse saveSubscription(Long id, OutboundEventSubscriptionRequest request) {
        NotificationChannelEntity channel = notificationChannelRepository.findById(request.channelId())
                .orElseThrow(() -> new IllegalArgumentException("未找到 notification channel。"));
        OutboundEventSubscriptionEntity entity = id == null
                ? new OutboundEventSubscriptionEntity()
                : outboundEventSubscriptionRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("未找到 outbound subscription。"));
        entity.setSubscriptionName(requireText(request.subscriptionName(), "subscriptionName"));
        entity.setChannelId(channel.getId());
        entity.setEventType(normalizeUpper(request.eventType()));
        entity.setSeverity(normalizeUpper(request.severity()));
        entity.setEntityType(normalizeUpper(request.entityType()));
        entity.setProviderType(normalizeUpper(request.providerType()));
        entity.setSiteProfileId(request.siteProfileId());
        entity.setEnabled(request.enabled() == null || request.enabled());
        return toSubscriptionResponse(outboundEventSubscriptionRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<OutboundDeliveryResponse> listDeliveries(
            String eventType,
            String deliveryStatus,
            String channelType,
            String entityType,
            String entityRef,
            String requestId) {
        return outboundDeliveryService.list(eventType, deliveryStatus, channelType, entityType, entityRef, requestId);
    }

    @Transactional(readOnly = true)
    public OutboundDeliveryResponse getDelivery(Long id) {
        return outboundDeliveryService.get(id);
    }

    public OutboundDeliveryResponse replayDelivery(Long id) {
        return outboundDeliveryService.replay(id);
    }

    public OutboundDeliveryResponse testDelivery(TestDeliveryRequest request) {
        notificationChannelRepository.findById(request.channelId())
                .orElseThrow(() -> new IllegalArgumentException("未找到 notification channel。"));
        PlatformEvent event = new PlatformEvent(
                UUID.randomUUID().toString(),
                PlatformEventType.valueOf(normalizeEnum(request.eventType(), PlatformEventType.ALERT_OPENED.name())),
                Instant.now(),
                blankToNull(request.severity()) == null ? "INFO" : request.severity().trim().toUpperCase(Locale.ROOT),
                "TEST",
                blankToNull(request.entityType()) == null ? "SYSTEM" : request.entityType().trim().toUpperCase(Locale.ROOT),
                blankToNull(request.entityRef()) == null ? "test" : request.entityRef().trim(),
                parseProviderType(request.providerType()),
                request.siteProfileId(),
                null,
                null,
                blankToNull(request.requestId()),
                blankToNull(request.gatewayResourceKey()),
                blankToNull(request.upstreamObjectId()),
                blankToNull(request.summary()) == null ? "integration test delivery" : request.summary().trim(),
                request.details() == null ? Map.of("mode", "test-delivery") : request.details()
        );
        return outboundEventService.dispatchDirect(request.channelId(), event);
    }

    private void validateChannel(NotificationChannelEntity entity) {
        NotificationChannelType channelType = NotificationChannelType.valueOf(entity.getChannelType());
        if ((channelType == NotificationChannelType.WEBHOOK || channelType == NotificationChannelType.IM_WEBHOOK)
                && entity.getWebhookEndpointId() == null) {
            throw new IllegalArgumentException(channelType.name() + " channel 必须绑定 webhook endpoint。");
        }
        if (channelType == NotificationChannelType.EMAIL && blankToNull(entity.getEmailTo()) == null) {
            throw new IllegalArgumentException("EMAIL channel 必须指定 emailTo。");
        }
    }

    private WebhookEndpointResponse toWebhookResponse(WebhookEndpointEntity entity) {
        return new WebhookEndpointResponse(
                entity.getId(),
                entity.getEndpointName(),
                entity.getEndpointUrl(),
                entity.getSigningMode(),
                entity.getTimeoutMs(),
                entity.isEnabled(),
                entity.getSecretFingerprint(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private NotificationChannelResponse toChannelResponse(NotificationChannelEntity entity) {
        return new NotificationChannelResponse(
                entity.getId(),
                entity.getChannelName(),
                entity.getChannelType(),
                entity.getWebhookEndpointId(),
                entity.getEmailTo(),
                entity.getTemplateMode(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private OutboundEventSubscriptionResponse toSubscriptionResponse(OutboundEventSubscriptionEntity entity) {
        return new OutboundEventSubscriptionResponse(
                entity.getId(),
                entity.getSubscriptionName(),
                entity.getChannelId(),
                entity.getEventType(),
                entity.getSeverity(),
                entity.getEntityType(),
                entity.getProviderType(),
                entity.getSiteProfileId(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String requireText(String value, String fieldName) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " 不能为空。");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeUpper(String value) {
        return Optional.ofNullable(blankToNull(value))
                .map(item -> item.toUpperCase(Locale.ROOT))
                .orElse(null);
    }

    private String normalizeEnum(String value, String fallback) {
        String normalized = normalizeUpper(value);
        return normalized == null ? fallback : normalized;
    }

    private ProviderType parseProviderType(String providerType) {
        String normalized = normalizeUpper(providerType);
        return normalized == null ? null : ProviderType.valueOf(normalized);
    }
}
