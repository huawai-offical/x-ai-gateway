package com.prodigalgal.xaigateway.admin.application.integrations;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OutboundRetryService {

    private final OutboundDeliveryService outboundDeliveryService;

    public OutboundRetryService(OutboundDeliveryService outboundDeliveryService) {
        this.outboundDeliveryService = outboundDeliveryService;
    }

    @Scheduled(fixedDelay = 60000L)
    public void processRetries() {
        outboundDeliveryService.processDueRetries();
    }
}
