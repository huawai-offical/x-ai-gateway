package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.PaymentDisputeRequest;
import com.prodigalgal.xaigateway.admin.api.PaymentOrderCreateRequest;
import com.prodigalgal.xaigateway.admin.api.PaymentProviderWebhookRequest;
import com.prodigalgal.xaigateway.admin.api.PaymentReconcileRequest;
import com.prodigalgal.xaigateway.admin.api.PaymentRefundRequest;
import com.prodigalgal.xaigateway.admin.api.PaymentWebhookRequest;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserBalanceLedgerEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.PaymentAuditLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.PaymentOrderEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserBalanceLedgerRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.PaymentAuditLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.PaymentOrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

class PaymentAdminServiceTests {

    @Test
    void shouldCreateMockOrderAndCreditBalanceIdempotentlyFromWebhook() {
        PaymentOrderRepository orderRepository = Mockito.mock(PaymentOrderRepository.class);
        PaymentAuditLogRepository auditRepository = Mockito.mock(PaymentAuditLogRepository.class);
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        GatewayUserBalanceLedgerRepository ledgerRepository = Mockito.mock(GatewayUserBalanceLedgerRepository.class);
        PaymentAdminService service = new PaymentAdminService(orderRepository, auditRepository, userRepository, ledgerRepository);

        GatewayUserEntity user = new GatewayUserEntity();
        ReflectionTestUtils.setField(user, "id", 7L);
        user.setEmail("paid@example.com");
        Mockito.when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        Mockito.when(orderRepository.save(any())).thenAnswer(invocation -> {
            PaymentOrderEntity order = invocation.getArgument(0);
            if (order.getId() == null) {
                ReflectionTestUtils.setField(order, "id", 11L);
            }
            Mockito.when(orderRepository.findByOrderNo(order.getOrderNo())).thenReturn(Optional.of(order));
            return order;
        });
        Mockito.when(auditRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        GatewayUserBalanceLedgerEntity existingLedger = ledger(user, 1_000L, "BOOTSTRAP", "seed");
        Mockito.when(ledgerRepository.findTopByUser_IdOrderByCreatedAtDescIdDesc(7L))
                .thenReturn(Optional.of(existingLedger));
        Mockito.when(ledgerRepository.findByReferenceTypeAndReferenceId(Mockito.eq("PAYMENT_ORDER"), Mockito.anyString()))
                .thenReturn(Optional.empty());
        Mockito.when(ledgerRepository.save(any())).thenAnswer(invocation -> {
            GatewayUserBalanceLedgerEntity ledger = invocation.getArgument(0);
            ReflectionTestUtils.setField(ledger, "id", 99L);
            Mockito.when(ledgerRepository.findTopByUser_IdOrderByCreatedAtDescIdDesc(7L)).thenReturn(Optional.of(ledger));
            Mockito.when(ledgerRepository.findByReferenceTypeAndReferenceId("PAYMENT_ORDER", ledger.getReferenceId()))
                    .thenReturn(Optional.of(ledger));
            return ledger;
        });

        var order = service.create(new PaymentOrderCreateRequest(7L, "mock", 1_990L, "cny", 500L, "{}"));
        var paid = service.acceptMockWebhook(new PaymentWebhookRequest(order.orderNo(), "mock", "evt_1", "trade_1", "PAID", "{\"ok\":true}"));
        Mockito.when(auditRepository.findByIdempotencyKey("mock:evt_1"))
                .thenReturn(Optional.of(new PaymentAuditLogEntity()));
        var duplicate = service.acceptMockWebhook(new PaymentWebhookRequest(order.orderNo(), "mock", "evt_1", "trade_1", "PAID", "{}"));

        assertEquals("PENDING", order.status());
        assertEquals("PAID", paid.status());
        assertEquals(1_500L, paid.balanceAfterTokenCredits());
        assertTrue(duplicate.idempotentWebhook());
        ArgumentCaptor<GatewayUserBalanceLedgerEntity> ledgerCaptor = ArgumentCaptor.forClass(GatewayUserBalanceLedgerEntity.class);
        Mockito.verify(ledgerRepository, Mockito.times(1)).save(ledgerCaptor.capture());
        assertEquals(500L, ledgerCaptor.getValue().getDeltaTokenCredits());
        assertEquals("PAYMENT_ORDER", ledgerCaptor.getValue().getReferenceType());
        Mockito.verify(auditRepository, Mockito.atLeast(3)).save(any());
    }

    @Test
    void shouldAcceptStripeSignedWebhookIdempotently() {
        PaymentOrderRepository orderRepository = Mockito.mock(PaymentOrderRepository.class);
        PaymentAuditLogRepository auditRepository = Mockito.mock(PaymentAuditLogRepository.class);
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        GatewayUserBalanceLedgerRepository ledgerRepository = Mockito.mock(GatewayUserBalanceLedgerRepository.class);
        PaymentAdminService service = new PaymentAdminService(orderRepository, auditRepository, userRepository, ledgerRepository);
        GatewayUserEntity user = user();
        Mockito.when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        Mockito.when(orderRepository.save(any())).thenAnswer(invocation -> {
            PaymentOrderEntity order = invocation.getArgument(0);
            if (order.getId() == null) {
                ReflectionTestUtils.setField(order, "id", 21L);
            }
            Mockito.when(orderRepository.findByOrderNo(order.getOrderNo())).thenReturn(Optional.of(order));
            return order;
        });
        Mockito.when(auditRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(ledgerRepository.findTopByUser_IdOrderByCreatedAtDescIdDesc(7L))
                .thenReturn(Optional.of(ledger(user, 2_000L, "BOOTSTRAP", "seed")));
        Mockito.when(ledgerRepository.findByReferenceTypeAndReferenceId(Mockito.eq("PAYMENT_ORDER"), Mockito.anyString()))
                .thenReturn(Optional.empty());
        Mockito.when(ledgerRepository.save(any())).thenAnswer(invocation -> {
            GatewayUserBalanceLedgerEntity ledger = invocation.getArgument(0);
            Mockito.when(ledgerRepository.findByReferenceTypeAndReferenceId("PAYMENT_ORDER", ledger.getReferenceId()))
                    .thenReturn(Optional.of(ledger));
            return ledger;
        });

        var order = service.create(new PaymentOrderCreateRequest(7L, "stripe", 1_990L, "cny", 500L, "{}"));
        String payload = """
                {"id":"evt_stripe_1","type":"checkout.session.completed","data":{"object":{"id":"cs_1","client_reference_id":"%s","payment_intent":"pi_1","amount_total":1990,"currency":"cny","payment_status":"paid"}}}
                """.formatted(order.orderNo()).trim();
        String signature = stripeSignature(payload, "whsec_test", "1712894400");

        var paid = service.acceptProviderWebhook(new PaymentProviderWebhookRequest("stripe", payload, signature, "whsec_test"));
        Mockito.when(auditRepository.findByIdempotencyKey("stripe:evt_stripe_1"))
                .thenReturn(Optional.of(new PaymentAuditLogEntity()));
        var duplicate = service.acceptProviderWebhook(new PaymentProviderWebhookRequest("stripe", payload, signature, "whsec_test"));

        assertEquals("PAID", paid.status());
        assertEquals("pi_1", paid.providerTradeNo());
        assertEquals(2_500L, paid.balanceAfterTokenCredits());
        assertTrue(duplicate.idempotentWebhook());
        Mockito.verify(ledgerRepository, Mockito.times(1)).save(any());
    }

    @Test
    void shouldRejectBadStripeSignatureAndFailAmountMismatch() {
        PaymentOrderRepository orderRepository = Mockito.mock(PaymentOrderRepository.class);
        PaymentAuditLogRepository auditRepository = Mockito.mock(PaymentAuditLogRepository.class);
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        GatewayUserBalanceLedgerRepository ledgerRepository = Mockito.mock(GatewayUserBalanceLedgerRepository.class);
        PaymentAdminService service = new PaymentAdminService(orderRepository, auditRepository, userRepository, ledgerRepository);
        GatewayUserEntity user = user();
        Mockito.when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        Mockito.when(orderRepository.save(any())).thenAnswer(invocation -> {
            PaymentOrderEntity order = invocation.getArgument(0);
            if (order.getId() == null) {
                ReflectionTestUtils.setField(order, "id", 22L);
            }
            Mockito.when(orderRepository.findByOrderNo(order.getOrderNo())).thenReturn(Optional.of(order));
            return order;
        });
        Mockito.when(auditRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(ledgerRepository.findTopByUser_IdOrderByCreatedAtDescIdDesc(7L))
                .thenReturn(Optional.empty());
        Mockito.when(ledgerRepository.findByReferenceTypeAndReferenceId(Mockito.eq("PAYMENT_ORDER"), Mockito.anyString()))
                .thenReturn(Optional.empty());

        var order = service.create(new PaymentOrderCreateRequest(7L, "stripe", 1_990L, "cny", 500L, "{}"));
        String payload = """
                {"id":"evt_stripe_bad_amount","type":"checkout.session.completed","data":{"object":{"client_reference_id":"%s","amount_total":2990,"currency":"cny","payment_status":"paid"}}}
                """.formatted(order.orderNo()).trim();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                service.acceptProviderWebhook(new PaymentProviderWebhookRequest("stripe", payload, "t=1712894400,v1=bad", "whsec_test")));
        var mismatch = service.acceptProviderWebhook(new PaymentProviderWebhookRequest("stripe", payload, stripeSignature(payload, "whsec_test", "1712894400"), "whsec_test"));

        assertEquals("FAILED", mismatch.status());
        Mockito.verify(ledgerRepository, Mockito.never()).save(any());
        Mockito.verify(auditRepository, Mockito.atLeastOnce()).save(Mockito.argThat(audit ->
                "WEBHOOK_AMOUNT_MISMATCH".equals(audit.getEventType())));
    }

    @Test
    void shouldAcceptGenericHmacProviderWebhook() {
        PaymentOrderRepository orderRepository = Mockito.mock(PaymentOrderRepository.class);
        PaymentAuditLogRepository auditRepository = Mockito.mock(PaymentAuditLogRepository.class);
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        GatewayUserBalanceLedgerRepository ledgerRepository = Mockito.mock(GatewayUserBalanceLedgerRepository.class);
        PaymentAdminService service = new PaymentAdminService(orderRepository, auditRepository, userRepository, ledgerRepository);
        GatewayUserEntity user = user();
        Mockito.when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        Mockito.when(orderRepository.save(any())).thenAnswer(invocation -> {
            PaymentOrderEntity order = invocation.getArgument(0);
            if (order.getId() == null) {
                ReflectionTestUtils.setField(order, "id", 23L);
            }
            Mockito.when(orderRepository.findByOrderNo(order.getOrderNo())).thenReturn(Optional.of(order));
            return order;
        });
        Mockito.when(auditRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(ledgerRepository.findTopByUser_IdOrderByCreatedAtDescIdDesc(7L))
                .thenReturn(Optional.of(ledger(user, 0L, "BOOTSTRAP", "seed")));
        Mockito.when(ledgerRepository.findByReferenceTypeAndReferenceId(Mockito.eq("PAYMENT_ORDER"), Mockito.anyString()))
                .thenReturn(Optional.empty());
        Mockito.when(ledgerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var order = service.create(new PaymentOrderCreateRequest(7L, "wechat", 6_600L, "cny", 900L, "{}"));
        String payload = """
                {"id":"evt_wechat_1","orderNo":"%s","transaction_id":"wx_1","status":"SUCCESS","amountMinor":6600,"currency":"CNY"}
                """.formatted(order.orderNo()).trim();
        var paid = service.acceptProviderWebhook(new PaymentProviderWebhookRequest(
                "wechat",
                payload,
                "sha256=" + hmacSha256Hex(payload, "wechat_secret"),
                "wechat_secret"
        ));

        assertEquals("PAID", paid.status());
        assertEquals("wx_1", paid.providerTradeNo());
        assertEquals(900L, paid.balanceAfterTokenCredits());
    }

    @Test
    void shouldCreateProductionCheckoutAndExposeProviderCapabilities() {
        PaymentOrderRepository orderRepository = Mockito.mock(PaymentOrderRepository.class);
        PaymentAuditLogRepository auditRepository = Mockito.mock(PaymentAuditLogRepository.class);
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        GatewayUserBalanceLedgerRepository ledgerRepository = Mockito.mock(GatewayUserBalanceLedgerRepository.class);
        PaymentAdminService service = new PaymentAdminService(orderRepository, auditRepository, userRepository, ledgerRepository);
        GatewayUserEntity user = user();
        Mockito.when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        Mockito.when(orderRepository.save(any())).thenAnswer(invocation -> {
            PaymentOrderEntity order = invocation.getArgument(0);
            if (order.getId() == null) {
                ReflectionTestUtils.setField(order, "id", 31L);
            }
            Mockito.when(orderRepository.findByOrderNo(order.getOrderNo())).thenReturn(Optional.of(order));
            return order;
        });
        Mockito.when(auditRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(ledgerRepository.findTopByUser_IdOrderByCreatedAtDescIdDesc(7L)).thenReturn(Optional.empty());

        var order = service.create(new PaymentOrderCreateRequest(
                7L,
                "stripe",
                1_990L,
                "cny",
                500L,
                "{\"providerInstanceCode\":\"stripe-prod-cn\",\"checkoutBaseUrl\":\"https://checkout.example/pay\"}"
        ));
        var checkout = service.checkout(order.orderNo());
        var capabilities = service.providerCapabilities();

        assertEquals("stripe_checkout_session", order.checkoutMethod());
        assertEquals("stripe-prod-cn", order.providerInstanceCode());
        assertTrue(order.checkoutUrl().contains("https://checkout.example/pay"));
        assertTrue(order.providerPayloadJson().contains("\"orderNo\":\"" + order.orderNo() + "\""));
        assertEquals(order.checkoutUrl(), checkout.checkoutUrl());
        assertTrue(capabilities.stream().anyMatch(item -> "wechat".equals(item.provider()) && item.productionReady()));
        assertTrue(capabilities.stream().anyMatch(item -> "alipay".equals(item.provider()) && item.productionReady()));
    }

    @Test
    void shouldEmbedInvoiceTaxSettlementAndBillingSnapshotsIntoProviderPayload() throws Exception {
        PaymentOrderRepository orderRepository = Mockito.mock(PaymentOrderRepository.class);
        PaymentAuditLogRepository auditRepository = Mockito.mock(PaymentAuditLogRepository.class);
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        GatewayUserBalanceLedgerRepository ledgerRepository = Mockito.mock(GatewayUserBalanceLedgerRepository.class);
        PaymentAdminService service = new PaymentAdminService(orderRepository, auditRepository, userRepository, ledgerRepository);
        GatewayUserEntity user = user();
        Mockito.when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        Mockito.when(orderRepository.save(any())).thenAnswer(invocation -> {
            PaymentOrderEntity order = invocation.getArgument(0);
            if (order.getId() == null) {
                ReflectionTestUtils.setField(order, "id", 33L);
            }
            Mockito.when(orderRepository.findByOrderNo(order.getOrderNo())).thenReturn(Optional.of(order));
            return order;
        });
        Mockito.when(auditRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(ledgerRepository.findTopByUser_IdOrderByCreatedAtDescIdDesc(7L)).thenReturn(Optional.empty());

        var order = service.create(new PaymentOrderCreateRequest(
                7L,
                "stripe",
                1_990L,
                "cny",
                500L,
                """
                        {
                          "invoiceNo":"INV-20260506-001",
                          "invoiceTitle":"测试企业",
                          "taxProfileCode":"CN-VAT-13",
                          "taxRateBps":1300,
                          "taxInclusive":true,
                          "settlementCurrency":"USD",
                          "exchangeRate":"7.2000",
                          "baseAmountMinor":1990,
                          "settlementAmountMinor":276,
                          "subscriptionId":"sub_ent_001",
                          "billingPeriodStart":"2026-05-01T00:00:00Z",
                          "billingPeriodEnd":"2026-06-01T00:00:00Z"
                        }
                        """
        ));

        JsonNode payload = new ObjectMapper().readTree(order.providerPayloadJson());
        assertEquals("INV-20260506-001", payload.path("invoice").path("number").asText());
        assertEquals("测试企业", payload.path("invoice").path("title").asText());
        assertEquals("CN-VAT-13", payload.path("tax").path("profileCode").asText());
        assertEquals(1300L, payload.path("tax").path("rateBps").asLong());
        assertTrue(payload.path("tax").path("inclusive").asBoolean());
        assertEquals("USD", payload.path("settlement").path("currency").asText());
        assertEquals("7.2000", payload.path("settlement").path("exchangeRate").asText());
        assertEquals(1990L, payload.path("settlement").path("baseAmountMinor").asLong());
        assertEquals(276L, payload.path("settlement").path("settlementAmountMinor").asLong());
        assertEquals("sub_ent_001", payload.path("billing").path("subscriptionId").asText());
        assertEquals("2026-05-01T00:00:00Z", payload.path("billing").path("periodStart").asText());
    }

    @Test
    void shouldRefundAndDisputePaidOrderWithBalanceLedger() {
        PaymentOrderRepository orderRepository = Mockito.mock(PaymentOrderRepository.class);
        PaymentAuditLogRepository auditRepository = Mockito.mock(PaymentAuditLogRepository.class);
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        GatewayUserBalanceLedgerRepository ledgerRepository = Mockito.mock(GatewayUserBalanceLedgerRepository.class);
        PaymentAdminService service = new PaymentAdminService(orderRepository, auditRepository, userRepository, ledgerRepository);
        GatewayUserEntity user = user();
        Mockito.when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        Mockito.when(orderRepository.save(any())).thenAnswer(invocation -> {
            PaymentOrderEntity order = invocation.getArgument(0);
            if (order.getId() == null) {
                ReflectionTestUtils.setField(order, "id", 32L);
            }
            Mockito.when(orderRepository.findByOrderNo(order.getOrderNo())).thenReturn(Optional.of(order));
            return order;
        });
        Mockito.when(auditRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(ledgerRepository.findTopByUser_IdOrderByCreatedAtDescIdDesc(7L))
                .thenReturn(Optional.of(ledger(user, 1_000L, "BOOTSTRAP", "seed")));
        Mockito.when(ledgerRepository.findByReferenceTypeAndReferenceId(Mockito.eq("PAYMENT_ORDER"), Mockito.anyString()))
                .thenReturn(Optional.empty());
        Mockito.when(ledgerRepository.save(any())).thenAnswer(invocation -> {
            GatewayUserBalanceLedgerEntity ledger = invocation.getArgument(0);
            Mockito.when(ledgerRepository.findTopByUser_IdOrderByCreatedAtDescIdDesc(7L)).thenReturn(Optional.of(ledger));
            if ("PAYMENT_ORDER".equals(ledger.getReferenceType())) {
                Mockito.when(ledgerRepository.findByReferenceTypeAndReferenceId("PAYMENT_ORDER", ledger.getReferenceId()))
                        .thenReturn(Optional.of(ledger));
            }
            return ledger;
        });

        var order = service.create(new PaymentOrderCreateRequest(7L, "mock", 1_990L, "cny", 500L, "{}"));
        var paid = service.acceptMockWebhook(new PaymentWebhookRequest(order.orderNo(), "mock", "evt_refund_1", "trade_1", "PAID", "{}"));
        var refunded = service.refund(order.orderNo(), new PaymentRefundRequest(995L, "customer_request", null));
        var disputed = service.markDisputed(order.orderNo(), new PaymentDisputeRequest("chargeback", null));

        assertEquals("PAID", paid.status());
        assertEquals("PARTIAL_REFUNDED", refunded.status());
        assertEquals(995L, refunded.refundAmountMinor());
        assertEquals(1_250L, refunded.balanceAfterTokenCredits());
        assertEquals("DISPUTED", disputed.status());
        Mockito.verify(auditRepository, Mockito.atLeastOnce()).save(Mockito.argThat(audit ->
                "REFUND_SUCCEEDED".equals(audit.getEventType())));
        Mockito.verify(auditRepository, Mockito.atLeastOnce()).save(Mockito.argThat(audit ->
                "DISPUTE_MARKED".equals(audit.getEventType())));
    }

    @Test
    void shouldReconcileOrdersByProviderAndStatus() {
        PaymentOrderRepository orderRepository = Mockito.mock(PaymentOrderRepository.class);
        PaymentAuditLogRepository auditRepository = Mockito.mock(PaymentAuditLogRepository.class);
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        GatewayUserBalanceLedgerRepository ledgerRepository = Mockito.mock(GatewayUserBalanceLedgerRepository.class);
        PaymentAdminService service = new PaymentAdminService(orderRepository, auditRepository, userRepository, ledgerRepository);
        GatewayUserEntity user = user();
        PaymentOrderEntity pending = paymentOrder(user, "pay_pending", "stripe", "PENDING");
        PaymentOrderEntity paid = paymentOrder(user, "pay_paid", "wechat", "PAID");
        Mockito.when(orderRepository.findAllByCreatedAtBetweenOrderByCreatedAtDesc(Mockito.any(), Mockito.any()))
                .thenReturn(List.of(pending, paid));
        Mockito.when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(auditRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(ledgerRepository.findTopByUser_IdOrderByCreatedAtDescIdDesc(7L)).thenReturn(Optional.empty());

        var report = service.reconcile(new PaymentReconcileRequest(
                "stripe",
                Instant.parse("2026-05-06T00:00:00Z"),
                Instant.parse("2026-05-07T00:00:00Z"),
                null
        ));

        assertEquals(1, report.totalOrders());
        assertEquals(1, report.pendingOrders());
        assertEquals("PENDING_PROVIDER_CONFIRMATION", pending.getReconcileStatus());
        assertEquals("stripe", report.provider());
        Mockito.verify(orderRepository).save(pending);
        Mockito.verify(auditRepository).save(Mockito.argThat(audit ->
                "ORDER_RECONCILED".equals(audit.getEventType()) && "pay_pending".equals(audit.getOrderNo())));
    }

    @Test
    void shouldRunScheduledReconciliationAndAuditAnomalies() {
        PaymentOrderRepository orderRepository = Mockito.mock(PaymentOrderRepository.class);
        PaymentAuditLogRepository auditRepository = Mockito.mock(PaymentAuditLogRepository.class);
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        GatewayUserBalanceLedgerRepository ledgerRepository = Mockito.mock(GatewayUserBalanceLedgerRepository.class);
        PaymentAdminService service = new PaymentAdminService(orderRepository, auditRepository, userRepository, ledgerRepository);
        GatewayUserEntity user = user();
        PaymentOrderEntity pending = paymentOrder(user, "pay_pending", "stripe", "PENDING");
        PaymentOrderEntity paid = paymentOrder(user, "pay_paid", "stripe", "PAID");
        PaymentOrderEntity failed = paymentOrder(user, "pay_failed", "stripe", "FAILED");
        PaymentOrderEntity disputed = paymentOrder(user, "pay_disputed", "stripe", "DISPUTED");
        PaymentOrderEntity refunded = paymentOrder(user, "pay_refunded", "stripe", "REFUNDED");
        Mockito.when(orderRepository.findAllByCreatedAtBetweenOrderByCreatedAtDesc(Mockito.any(), Mockito.any()))
                .thenReturn(List.of(pending, paid, failed, disputed, refunded));
        Mockito.when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(auditRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(ledgerRepository.findTopByUser_IdOrderByCreatedAtDescIdDesc(7L)).thenReturn(Optional.empty());

        var result = service.runScheduledReconcile(new PaymentReconcileRequest(
                "stripe",
                Instant.parse("2026-05-06T00:00:00Z"),
                Instant.parse("2026-05-07T00:00:00Z"),
                null
        ));

        assertTrue(result.runId().startsWith("pay_recon_"));
        assertTrue(result.scheduled());
        assertEquals(5, result.totalOrders());
        assertEquals(3, result.anomalyOrders());
        assertEquals("MATCHED", paid.getReconcileStatus());
        assertEquals("PENDING_PROVIDER_CONFIRMATION", pending.getReconcileStatus());
        assertEquals("FAILED_PROVIDER_CONFIRMATION", failed.getReconcileStatus());
        assertEquals("DISPUTED_REVIEW_REQUIRED", disputed.getReconcileStatus());
        ArgumentCaptor<PaymentAuditLogEntity> auditCaptor = ArgumentCaptor.forClass(PaymentAuditLogEntity.class);
        Mockito.verify(auditRepository, Mockito.atLeast(8)).save(auditCaptor.capture());
        List<PaymentAuditLogEntity> audits = auditCaptor.getAllValues();
        assertEquals(3, audits.stream().filter(audit -> "RECONCILE_ANOMALY".equals(audit.getEventType())).count());
        assertTrue(audits.stream()
                .filter(audit -> "RECONCILE_ANOMALY".equals(audit.getEventType()))
                .allMatch(audit -> audit.getPayloadJson().contains(result.runId())));
    }

    private GatewayUserBalanceLedgerEntity ledger(
            GatewayUserEntity user,
            long balanceAfter,
            String referenceType,
            String referenceId) {
        GatewayUserBalanceLedgerEntity ledger = new GatewayUserBalanceLedgerEntity();
        ledger.setUser(user);
        ledger.setDeltaTokenCredits(balanceAfter);
        ledger.setBalanceAfterTokenCredits(balanceAfter);
        ledger.setReason("TEST");
        ledger.setReferenceType(referenceType);
        ledger.setReferenceId(referenceId);
        return ledger;
    }

    private GatewayUserEntity user() {
        GatewayUserEntity user = new GatewayUserEntity();
        ReflectionTestUtils.setField(user, "id", 7L);
        user.setEmail("paid@example.com");
        return user;
    }

    private PaymentOrderEntity paymentOrder(GatewayUserEntity user, String orderNo, String provider, String status) {
        PaymentOrderEntity order = new PaymentOrderEntity();
        order.setUser(user);
        order.setOrderNo(orderNo);
        order.setProvider(provider);
        order.setAmountMinor(1_990L);
        order.setCurrency("CNY");
        order.setTokenCredits(500L);
        order.setStatus(status);
        order.setMetadataJson("{}");
        return order;
    }

    private String stripeSignature(String payload, String secret, String timestamp) {
        return "t=" + timestamp + ",v1=" + hmacSha256Hex(timestamp + "." + payload, secret);
    }

    private String hmacSha256Hex(String payload, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
