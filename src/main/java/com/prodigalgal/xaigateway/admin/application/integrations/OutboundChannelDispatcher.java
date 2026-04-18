package com.prodigalgal.xaigateway.admin.application.integrations;

import com.prodigalgal.xaigateway.infra.persistence.entity.NotificationChannelEntity;
import org.springframework.stereotype.Service;

@Service
public class OutboundChannelDispatcher {

    private final WebhookDispatcher webhookDispatcher;
    private final EmailDispatcher emailDispatcher;

    public OutboundChannelDispatcher(
            WebhookDispatcher webhookDispatcher,
            EmailDispatcher emailDispatcher) {
        this.webhookDispatcher = webhookDispatcher;
        this.emailDispatcher = emailDispatcher;
    }

    public OutboundDispatchResult dispatch(NotificationChannelEntity channel, OutboundEventEnvelope envelope) {
        NotificationChannelType channelType = NotificationChannelType.valueOf(channel.getChannelType());
        return switch (channelType) {
            case WEBHOOK, IM_WEBHOOK -> webhookDispatcher.dispatch(channel, envelope);
            case EMAIL -> emailDispatcher.dispatch(channel, envelope);
        };
    }
}
