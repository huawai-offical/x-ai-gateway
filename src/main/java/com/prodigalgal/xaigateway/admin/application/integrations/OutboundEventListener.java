package com.prodigalgal.xaigateway.admin.application.integrations;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OutboundEventListener {

    private final OutboundEventService outboundEventService;

    public OutboundEventListener(OutboundEventService outboundEventService) {
        this.outboundEventService = outboundEventService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PlatformEvent event) {
        outboundEventService.handlePlatformEvent(event);
    }
}
