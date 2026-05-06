package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.PaymentAdminService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/payments")
public class PaymentAdminController {

    private final PaymentAdminService paymentAdminService;

    public PaymentAdminController(PaymentAdminService paymentAdminService) {
        this.paymentAdminService = paymentAdminService;
    }

    @GetMapping("/orders")
    public List<PaymentOrderResponse> listOrders() {
        return paymentAdminService.list();
    }

    @GetMapping("/providers")
    public List<PaymentProviderCapabilityResponse> providerCapabilities() {
        return paymentAdminService.providerCapabilities();
    }

    @GetMapping("/orders/{orderNo}")
    public PaymentOrderResponse getOrder(@PathVariable String orderNo) {
        return paymentAdminService.get(orderNo);
    }

    @GetMapping("/orders/{orderNo}/checkout")
    public PaymentCheckoutResponse checkout(@PathVariable String orderNo) {
        return paymentAdminService.checkout(orderNo);
    }

    @PostMapping("/orders")
    public PaymentOrderResponse createOrder(@Valid @RequestBody PaymentOrderCreateRequest request) {
        return paymentAdminService.create(request);
    }

    @PostMapping("/orders/{orderNo}/refund")
    public PaymentOrderResponse refund(
            @PathVariable String orderNo,
            @Valid @RequestBody(required = false) PaymentRefundRequest request) {
        return paymentAdminService.refund(orderNo, request);
    }

    @PostMapping("/orders/{orderNo}/dispute")
    public PaymentOrderResponse dispute(
            @PathVariable String orderNo,
            @RequestBody(required = false) PaymentDisputeRequest request) {
        return paymentAdminService.markDisputed(orderNo, request);
    }

    @PostMapping("/reconcile")
    public PaymentReconcileReportResponse reconcile(@RequestBody(required = false) PaymentReconcileRequest request) {
        return paymentAdminService.reconcile(request);
    }

    @PostMapping("/reconcile/scheduled")
    public PaymentScheduledReconcileRunResponse scheduledReconcile(@RequestBody(required = false) PaymentReconcileRequest request) {
        return paymentAdminService.runScheduledReconcile(request);
    }

    @PostMapping("/webhooks/mock")
    public PaymentOrderResponse mockWebhook(@Valid @RequestBody PaymentWebhookRequest request) {
        return paymentAdminService.acceptMockWebhook(request);
    }

    @PostMapping("/webhooks/provider")
    public PaymentOrderResponse providerWebhook(@Valid @RequestBody PaymentProviderWebhookRequest request) {
        return paymentAdminService.acceptProviderWebhook(request);
    }
}
