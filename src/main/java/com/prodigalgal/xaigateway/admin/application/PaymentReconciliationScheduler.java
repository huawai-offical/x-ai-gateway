package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.PaymentReconcileRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "gateway.payment.reconciliation.enabled", havingValue = "true")
public class PaymentReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationScheduler.class);

    private final PaymentAdminService paymentAdminService;
    private final String provider;
    private final String status;

    public PaymentReconciliationScheduler(
            PaymentAdminService paymentAdminService,
            @Value("${gateway.payment.reconciliation.provider:}") String provider,
            @Value("${gateway.payment.reconciliation.status:}") String status) {
        this.paymentAdminService = paymentAdminService;
        this.provider = provider;
        this.status = status;
    }

    @Scheduled(fixedDelayString = "${gateway.payment.reconciliation.fixed-delay:PT6H}")
    public void runScheduledReconciliation() {
        var result = paymentAdminService.runScheduledReconcile(new PaymentReconcileRequest(
                blankToNull(provider),
                null,
                null,
                blankToNull(status)
        ));
        log.info(
                "支付定时对账完成 runId={} provider={} totalOrders={} anomalyOrders={}",
                result.runId(),
                result.provider(),
                result.totalOrders(),
                result.anomalyOrders()
        );
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
