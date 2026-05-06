package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.PaymentCheckoutResponse;
import com.prodigalgal.xaigateway.admin.api.PaymentDisputeRequest;
import com.prodigalgal.xaigateway.admin.api.PaymentOrderCreateRequest;
import com.prodigalgal.xaigateway.admin.api.PaymentOrderResponse;
import com.prodigalgal.xaigateway.admin.api.PaymentProviderCapabilityResponse;
import com.prodigalgal.xaigateway.admin.api.PaymentProviderWebhookRequest;
import com.prodigalgal.xaigateway.admin.api.PaymentReconcileReportResponse;
import com.prodigalgal.xaigateway.admin.api.PaymentReconcileRequest;
import com.prodigalgal.xaigateway.admin.api.PaymentRefundRequest;
import com.prodigalgal.xaigateway.admin.api.PaymentScheduledReconcileRunResponse;
import com.prodigalgal.xaigateway.admin.api.PaymentWebhookRequest;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserBalanceLedgerEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.PaymentAuditLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.PaymentOrderEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserBalanceLedgerRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.PaymentAuditLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.PaymentOrderRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class PaymentAdminService {

    private static final String LEDGER_REFERENCE_TYPE = "PAYMENT_ORDER";
    private static final String REFUND_LEDGER_REFERENCE_TYPE = "PAYMENT_REFUND";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PaymentCheckoutPlanner checkoutPlanner = new PaymentCheckoutPlanner(objectMapper);
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentAuditLogRepository paymentAuditLogRepository;
    private final GatewayUserRepository gatewayUserRepository;
    private final GatewayUserBalanceLedgerRepository gatewayUserBalanceLedgerRepository;

    public PaymentAdminService(
            PaymentOrderRepository paymentOrderRepository,
            PaymentAuditLogRepository paymentAuditLogRepository,
            GatewayUserRepository gatewayUserRepository,
            GatewayUserBalanceLedgerRepository gatewayUserBalanceLedgerRepository) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.paymentAuditLogRepository = paymentAuditLogRepository;
        this.gatewayUserRepository = gatewayUserRepository;
        this.gatewayUserBalanceLedgerRepository = gatewayUserBalanceLedgerRepository;
    }

    @Transactional(readOnly = true)
    public List<PaymentOrderResponse> list() {
        return paymentOrderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(order -> toResponse(order, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentProviderCapabilityResponse> providerCapabilities() {
        return List.of(
                capability("stripe", "Stripe", "stripe_checkout_session", "Stripe-Signature t/v1 HMAC", true),
                capability("easypay", "EasyPay", "easypay_page", "sha256 HMAC", true),
                capability("alipay", "支付宝官方", "alipay_page_pay", "sha256 HMAC", true),
                capability("wechat", "微信支付官方", "wechat_native_qr", "sha256 HMAC", true),
                capability("generic_hmac", "通用 HMAC Provider", "generic_hmac_page", "sha256 HMAC", true),
                capability("mock", "本地 Mock", "mock_page", "mock payload", false)
        );
    }

    @Transactional(readOnly = true)
    public PaymentOrderResponse get(String orderNo) {
        return toResponse(getRequired(orderNo), false);
    }

    @Transactional(readOnly = true)
    public PaymentCheckoutResponse checkout(String orderNo) {
        PaymentOrderEntity order = getRequired(orderNo);
        return new PaymentCheckoutResponse(
                toResponse(order, false),
                order.getCheckoutUrl(),
                order.getCheckoutMethod(),
                order.getProviderInstanceCode(),
                order.getProviderPayloadJson(),
                order.getCheckoutExpiresAt()
        );
    }

    public PaymentOrderResponse create(PaymentOrderCreateRequest request) {
        GatewayUserEntity user = gatewayUserRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("未找到支付订单用户。"));
        return createForUser(
                user,
                request.provider(),
                request.amountMinor(),
                request.currency(),
                request.tokenCredits(),
                request.metadataJson()
        );
    }

    public PaymentOrderResponse createForUser(
            GatewayUserEntity user,
            String provider,
            Long amountMinor,
            String currency,
            Long tokenCredits,
            String metadataJson) {
        PaymentOrderEntity order = new PaymentOrderEntity();
        order.setOrderNo("pay_" + UUID.randomUUID().toString().replace("-", ""));
        order.setUser(user);
        order.setProvider(normalizeProvider(provider));
        order.setAmountMinor(requiredPositive(amountMinor, "amountMinor"));
        order.setCurrency(defaultString(currency, "CNY").toUpperCase(Locale.ROOT));
        order.setTokenCredits(requiredPositive(tokenCredits, "tokenCredits"));
        order.setStatus("PENDING");
        order.setMetadataJson(defaultString(metadataJson, "{}"));
        checkoutPlanner.apply(order);
        PaymentOrderEntity saved = paymentOrderRepository.save(order);
        audit(saved, "ORDER_CREATED", null, saved.getMetadataJson());
        return toResponse(saved, false);
    }

    public PaymentOrderResponse refund(String orderNo, PaymentRefundRequest request) {
        PaymentOrderEntity order = getRequired(orderNo);
        if (!"PAID".equals(order.getStatus()) && !"PARTIAL_REFUNDED".equals(order.getStatus())) {
            throw new IllegalArgumentException("只有已支付订单可以退款。");
        }
        long refundableAmount = order.getAmountMinor() - order.getRefundAmountMinor();
        if (refundableAmount <= 0) {
            throw new IllegalArgumentException("订单已无可退款金额。");
        }
        long refundAmount = request == null || request.amountMinor() == null
                ? refundableAmount
                : requiredPositive(request.amountMinor(), "amountMinor");
        if (refundAmount > refundableAmount) {
            throw new IllegalArgumentException("退款金额不能超过可退款金额。");
        }

        long refundBefore = order.getRefundAmountMinor();
        long refundAfter = refundBefore + refundAmount;
        long previousRefundTokens = proratedRefundTokens(order, refundBefore);
        long nextRefundTokens = proratedRefundTokens(order, refundAfter);
        long deltaTokens = previousRefundTokens - nextRefundTokens;
        long balanceAfter = currentBalance(order.getUser());
        if (deltaTokens != 0) {
            balanceAfter += deltaTokens;
            GatewayUserBalanceLedgerEntity ledger = new GatewayUserBalanceLedgerEntity();
            ledger.setUser(order.getUser());
            ledger.setDeltaTokenCredits(deltaTokens);
            ledger.setBalanceAfterTokenCredits(balanceAfter);
            ledger.setReason("PAYMENT_REFUND");
            ledger.setReferenceType(REFUND_LEDGER_REFERENCE_TYPE);
            ledger.setReferenceId(order.getOrderNo() + ":" + refundAfter);
            gatewayUserBalanceLedgerRepository.save(ledger);
        }

        order.setRefundAmountMinor(refundAfter);
        order.setRefundedAt(Instant.now());
        order.setStatus(refundAfter >= order.getAmountMinor() ? "REFUNDED" : "PARTIAL_REFUNDED");
        PaymentOrderEntity saved = paymentOrderRepository.save(order);
        audit(saved, "REFUND_SUCCEEDED", null, defaultString(request == null ? null : request.payloadJson(), refundPayload(refundAmount, request == null ? null : request.reason())));
        return toResponse(saved, false, balanceAfter);
    }

    public PaymentOrderResponse markDisputed(String orderNo, PaymentDisputeRequest request) {
        PaymentOrderEntity order = getRequired(orderNo);
        if (!"PAID".equals(order.getStatus()) && !"PARTIAL_REFUNDED".equals(order.getStatus())) {
            throw new IllegalArgumentException("只有已支付订单可以标记争议。");
        }
        order.setStatus("DISPUTED");
        order.setDisputedAt(Instant.now());
        PaymentOrderEntity saved = paymentOrderRepository.save(order);
        audit(saved, "DISPUTE_MARKED", null, defaultString(request == null ? null : request.payloadJson(), disputePayload(request == null ? null : request.reason())));
        return toResponse(saved, false);
    }

    public PaymentReconcileReportResponse reconcile(PaymentReconcileRequest request) {
        return reconcileInternal(request, null, false).report();
    }

    public PaymentScheduledReconcileRunResponse runScheduledReconcile(PaymentReconcileRequest request) {
        String runId = "pay_recon_" + UUID.randomUUID().toString().replace("-", "");
        ReconcileRunResult result = reconcileInternal(request, runId, true);
        PaymentReconcileReportResponse report = result.report();
        return new PaymentScheduledReconcileRunResponse(
                runId,
                true,
                report.provider(),
                report.from(),
                report.to(),
                report.status(),
                report.totalOrders(),
                result.anomalyOrders(),
                report.reconciledAt(),
                report
        );
    }

    private ReconcileRunResult reconcileInternal(PaymentReconcileRequest request, String runId, boolean scheduled) {
        Instant now = Instant.now();
        Instant to = request == null || request.to() == null ? now : request.to();
        Instant from = request == null || request.from() == null ? to.minus(Duration.ofDays(1)) : request.from();
        String provider = request == null ? null : blankToNull(request.provider());
        String status = request == null ? null : blankToNull(request.status());
        List<PaymentOrderEntity> orders = paymentOrderRepository.findAllByCreatedAtBetweenOrderByCreatedAtDesc(from, to).stream()
                .filter(order -> provider == null || order.getProvider().equalsIgnoreCase(normalizeProvider(provider)))
                .filter(order -> status == null || order.getStatus().equalsIgnoreCase(status))
                .toList();
        long anomalyOrders = 0L;
        for (PaymentOrderEntity order : orders) {
            order.setReconciledAt(now);
            order.setReconcileStatus(reconcileStatus(order.getStatus()));
            paymentOrderRepository.save(order);
            audit(order, "ORDER_RECONCILED", null, reconcilePayload(from, to, order.getReconcileStatus(), runId, scheduled));
            if (scheduled && isReconcileAnomaly(order.getReconcileStatus())) {
                anomalyOrders++;
                audit(order, "RECONCILE_ANOMALY", null, reconcileAnomalyPayload(from, to, runId, order.getStatus(), order.getReconcileStatus()));
            }
        }
        List<PaymentOrderResponse> responses = orders.stream()
                .map(order -> toResponse(order, false))
                .toList();
        return new ReconcileRunResult(new PaymentReconcileReportResponse(
                provider == null ? "ALL" : normalizeProvider(provider),
                from,
                to,
                status == null ? "ALL" : status.toUpperCase(Locale.ROOT),
                orders.size(),
                countStatus(orders, "PENDING"),
                countStatus(orders, "PAID"),
                countStatus(orders, "FAILED"),
                countStatus(orders, "REFUNDED") + countStatus(orders, "PARTIAL_REFUNDED"),
                countStatus(orders, "DISPUTED"),
                orders.stream().mapToLong(PaymentOrderEntity::getAmountMinor).sum(),
                orders.stream().mapToLong(PaymentOrderEntity::getTokenCredits).sum(),
                now,
                responses
        ), anomalyOrders);
    }

    public PaymentOrderResponse acceptMockWebhook(PaymentWebhookRequest request) {
        return acceptNormalizedWebhook(
                defaultString(request.provider(), "mock"),
                request.orderNo(),
                request.providerEventId(),
                request.providerTradeNo(),
                request.status(),
                defaultString(request.payloadJson(), "{}"),
                null,
                null
        );
    }

    public PaymentOrderResponse acceptProviderWebhook(PaymentProviderWebhookRequest request) {
        String provider = normalizeProvider(request.provider());
        return switch (provider) {
            case "stripe" -> acceptStripeWebhook(request);
            case "easypay", "wechat", "alipay", "generic_hmac" -> acceptGenericHmacWebhook(provider, request);
            default -> throw new IllegalArgumentException("暂不支持的支付 provider：" + request.provider());
        };
    }

    private PaymentOrderResponse acceptStripeWebhook(PaymentProviderWebhookRequest request) {
        verifyStripeSignature(request.payloadJson(), request.signatureHeader(), request.webhookSecret());
        JsonNode root = readJson(request.payloadJson());
        JsonNode object = root.path("data").path("object");
        String orderNo = firstNonBlank(
                object.path("client_reference_id").asText(null),
                object.path("metadata").path("order_no").asText(null),
                object.path("metadata").path("orderNo").asText(null)
        );
        String status = firstNonBlank(
                object.path("payment_status").asText(null),
                object.path("status").asText(null),
                root.path("type").asText("").contains("completed") ? "PAID" : null
        );
        return acceptNormalizedWebhook(
                "stripe",
                requiredText(orderNo, "Stripe webhook 缺少订单号。"),
                requiredText(root.path("id").asText(null), "Stripe webhook 缺少 event id。"),
                firstNonBlank(object.path("payment_intent").asText(null), object.path("id").asText(null)),
                status,
                request.payloadJson(),
                longOrNull(object, "amount_total", "amount", "total"),
                firstNonBlank(object.path("currency").asText(null), "USD")
        );
    }

    private PaymentOrderResponse acceptGenericHmacWebhook(String provider, PaymentProviderWebhookRequest request) {
        verifySimpleHmacSignature(request.payloadJson(), request.signatureHeader(), request.webhookSecret());
        JsonNode root = readJson(request.payloadJson());
        return acceptNormalizedWebhook(
                provider,
                requiredText(firstNonBlank(root.path("orderNo").asText(null), root.path("order_no").asText(null)), "支付 webhook 缺少订单号。"),
                requiredText(firstNonBlank(root.path("eventId").asText(null), root.path("event_id").asText(null), root.path("id").asText(null)), "支付 webhook 缺少 event id。"),
                firstNonBlank(root.path("providerTradeNo").asText(null), root.path("trade_no").asText(null), root.path("transaction_id").asText(null)),
                firstNonBlank(root.path("status").asText(null), root.path("trade_status").asText(null)),
                request.payloadJson(),
                longOrNull(root, "amountMinor", "amount_minor", "amount"),
                firstNonBlank(root.path("currency").asText(null), "CNY")
        );
    }

    private PaymentOrderResponse acceptNormalizedWebhook(
            String provider,
            String orderNo,
            String providerEventId,
            String providerTradeNo,
            String status,
            String payloadJson,
            Long amountMinor,
            String currency) {
        String normalizedProvider = normalizeProvider(provider);
        String idempotencyKey = normalizedProvider + ":" + providerEventId.trim();
        PaymentOrderEntity order = getRequired(orderNo);
        if (paymentAuditLogRepository.findByIdempotencyKey(idempotencyKey).isPresent()
                || gatewayUserBalanceLedgerRepository.findByReferenceTypeAndReferenceId(LEDGER_REFERENCE_TYPE, order.getOrderNo()).isPresent()) {
            audit(order, "WEBHOOK_DUPLICATE", idempotencyKey, defaultString(payloadJson, "{}"));
            return toResponse(order, true);
        }

        if (!order.getProvider().equalsIgnoreCase(normalizedProvider)) {
            order.setStatus("FAILED");
            PaymentOrderEntity saved = paymentOrderRepository.save(order);
            audit(saved, "WEBHOOK_PROVIDER_MISMATCH", idempotencyKey, defaultString(payloadJson, "{}"));
            return toResponse(saved, false);
        }
        if (amountMinor != null && amountMinor != order.getAmountMinor()) {
            order.setStatus("FAILED");
            PaymentOrderEntity saved = paymentOrderRepository.save(order);
            audit(saved, "WEBHOOK_AMOUNT_MISMATCH", idempotencyKey, defaultString(payloadJson, "{}"));
            return toResponse(saved, false);
        }
        if (currency != null && !currency.isBlank() && !order.getCurrency().equalsIgnoreCase(currency)) {
            order.setStatus("FAILED");
            PaymentOrderEntity saved = paymentOrderRepository.save(order);
            audit(saved, "WEBHOOK_CURRENCY_MISMATCH", idempotencyKey, defaultString(payloadJson, "{}"));
            return toResponse(saved, false);
        }

        String normalizedStatus = defaultString(status, "PAID").toUpperCase(Locale.ROOT);
        if (!"PAID".equals(normalizedStatus) && !"SUCCEEDED".equals(normalizedStatus) && !"SUCCESS".equals(normalizedStatus)) {
            order.setStatus("FAILED");
            order.setProviderTradeNo(blankToNull(providerTradeNo));
            PaymentOrderEntity saved = paymentOrderRepository.save(order);
            audit(saved, "WEBHOOK_FAILED", idempotencyKey, defaultString(payloadJson, "{}"));
            return toResponse(saved, false);
        }

        long balanceAfter = currentBalance(order.getUser()) + order.getTokenCredits();
        GatewayUserBalanceLedgerEntity ledger = new GatewayUserBalanceLedgerEntity();
        ledger.setUser(order.getUser());
        ledger.setDeltaTokenCredits(order.getTokenCredits());
        ledger.setBalanceAfterTokenCredits(balanceAfter);
        ledger.setReason("PAYMENT_RECHARGE");
        ledger.setReferenceType(LEDGER_REFERENCE_TYPE);
        ledger.setReferenceId(order.getOrderNo());
        gatewayUserBalanceLedgerRepository.save(ledger);

        order.setStatus("PAID");
        order.setProviderTradeNo(blankToNull(providerTradeNo));
        order.setPaidAt(Instant.now());
        PaymentOrderEntity saved = paymentOrderRepository.save(order);
        audit(saved, "WEBHOOK_PAID", idempotencyKey, defaultString(payloadJson, "{}"));
        return toResponse(saved, false, balanceAfter);
    }

    private PaymentOrderEntity getRequired(String orderNo) {
        return paymentOrderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("未找到支付订单。"));
    }

    private long currentBalance(GatewayUserEntity user) {
        return gatewayUserBalanceLedgerRepository.findTopByUser_IdOrderByCreatedAtDescIdDesc(user.getId())
                .map(GatewayUserBalanceLedgerEntity::getBalanceAfterTokenCredits)
                .orElse(0L);
    }

    private void audit(PaymentOrderEntity order, String eventType, String idempotencyKey, String payloadJson) {
        PaymentAuditLogEntity audit = new PaymentAuditLogEntity();
        audit.setOrderNo(order.getOrderNo());
        audit.setProvider(order.getProvider());
        audit.setEventType(eventType);
        audit.setIdempotencyKey(idempotencyKey);
        audit.setPayloadJson(payloadJson);
        paymentAuditLogRepository.save(audit);
    }

    private PaymentOrderResponse toResponse(PaymentOrderEntity order, boolean idempotentWebhook) {
        return toResponse(order, idempotentWebhook, currentBalance(order.getUser()));
    }

    private PaymentOrderResponse toResponse(PaymentOrderEntity order, boolean idempotentWebhook, long balanceAfter) {
        GatewayUserEntity user = order.getUser();
        return new PaymentOrderResponse(
                order.getId(),
                order.getOrderNo(),
                user.getId(),
                user.getEmail(),
                order.getProvider(),
                order.getAmountMinor(),
                order.getCurrency(),
                order.getTokenCredits(),
                order.getStatus(),
                order.getProviderTradeNo(),
                order.getProviderInstanceCode(),
                order.getCheckoutUrl(),
                order.getCheckoutMethod(),
                order.getProviderPayloadJson(),
                order.getCheckoutExpiresAt(),
                order.getRefundAmountMinor(),
                order.getRefundedAt(),
                order.getDisputedAt(),
                order.getReconciledAt(),
                order.getReconcileStatus(),
                balanceAfter,
                idempotentWebhook,
                order.getMetadataJson(),
                order.getPaidAt(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private long requiredPositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " 必须大于 0。");
        }
        return value;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeProvider(String provider) {
        return defaultString(provider, "mock").toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private PaymentProviderCapabilityResponse capability(
            String provider,
            String displayName,
            String checkoutMethod,
            String webhookSignature,
            boolean productionReady) {
        return new PaymentProviderCapabilityResponse(
                provider,
                displayName,
                checkoutMethod,
                webhookSignature,
                productionReady,
                List.of("create_checkout", "provider_webhook", "refund", "dispute", "reconcile", "scheduled_reconcile"),
                "使用 metadataJson 注入 providerInstanceCode、checkoutBaseUrl、notifyUrl、successUrl 后执行本地 smoke。"
        );
    }

    private long proratedRefundTokens(PaymentOrderEntity order, long refundAmountMinor) {
        if (refundAmountMinor >= order.getAmountMinor()) {
            return order.getTokenCredits();
        }
        return (order.getTokenCredits() * refundAmountMinor) / order.getAmountMinor();
    }

    private long countStatus(List<PaymentOrderEntity> orders, String status) {
        return orders.stream().filter(order -> status.equals(order.getStatus())).count();
    }

    private String reconcileStatus(String orderStatus) {
        return switch (defaultString(orderStatus, "PENDING").toUpperCase(Locale.ROOT)) {
            case "PAID", "REFUNDED", "PARTIAL_REFUNDED" -> "MATCHED";
            case "FAILED" -> "FAILED_PROVIDER_CONFIRMATION";
            case "DISPUTED" -> "DISPUTED_REVIEW_REQUIRED";
            default -> "PENDING_PROVIDER_CONFIRMATION";
        };
    }

    private String refundPayload(long refundAmount, String reason) {
        return "{\"refundAmountMinor\":" + refundAmount + ",\"reason\":\"" + escapeJson(defaultString(reason, "admin_refund")) + "\"}";
    }

    private String disputePayload(String reason) {
        return "{\"reason\":\"" + escapeJson(defaultString(reason, "payment_dispute")) + "\"}";
    }

    private boolean isReconcileAnomaly(String reconcileStatus) {
        return !"MATCHED".equals(defaultString(reconcileStatus, "PENDING_PROVIDER_CONFIRMATION"));
    }

    private String reconcilePayload(Instant from, Instant to, String status, String runId, boolean scheduled) {
        StringBuilder builder = new StringBuilder("{\"from\":\"")
                .append(from)
                .append("\",\"to\":\"")
                .append(to)
                .append("\",\"reconcileStatus\":\"")
                .append(escapeJson(status))
                .append("\",\"scheduled\":")
                .append(scheduled);
        if (runId != null && !runId.isBlank()) {
            builder.append(",\"runId\":\"").append(escapeJson(runId)).append("\"");
        }
        return builder.append("}").toString();
    }

    private String reconcileAnomalyPayload(Instant from, Instant to, String runId, String orderStatus, String reconcileStatus) {
        return "{\"from\":\"" + from
                + "\",\"to\":\"" + to
                + "\",\"runId\":\"" + escapeJson(defaultString(runId, "manual"))
                + "\",\"orderStatus\":\"" + escapeJson(defaultString(orderStatus, "UNKNOWN"))
                + "\",\"reconcileStatus\":\"" + escapeJson(defaultString(reconcileStatus, "UNKNOWN"))
                + "\",\"action\":\"ops_review_required\"}";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private JsonNode readJson(String payloadJson) {
        try {
            return objectMapper.readTree(defaultString(payloadJson, "{}"));
        } catch (Exception exception) {
            throw new IllegalArgumentException("支付 webhook payload 不是合法 JSON。", exception);
        }
    }

    private void verifyStripeSignature(String payloadJson, String signatureHeader, String secret) {
        String timestamp = null;
        String actualSignature = null;
        for (String item : signatureHeader.split(",")) {
            String[] pair = item.split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            if ("t".equals(pair[0].trim())) {
                timestamp = pair[1].trim();
            }
            if ("v1".equals(pair[0].trim())) {
                actualSignature = pair[1].trim();
            }
        }
        if (timestamp == null || actualSignature == null) {
            throw new IllegalArgumentException("Stripe webhook 签名头缺少 t 或 v1。");
        }
        String expected = hmacSha256Hex(timestamp + "." + payloadJson, secret);
        if (!secureEquals(expected, actualSignature)) {
            throw new IllegalArgumentException("Stripe webhook 签名校验失败。");
        }
    }

    private void verifySimpleHmacSignature(String payloadJson, String signatureHeader, String secret) {
        String actual = signatureHeader.trim();
        if (actual.startsWith("sha256=")) {
            actual = actual.substring("sha256=".length());
        }
        String expected = hmacSha256Hex(payloadJson, secret);
        if (!secureEquals(expected, actual)) {
            throw new IllegalArgumentException("支付 provider webhook 签名校验失败。");
        }
    }

    private String hmacSha256Hex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("计算支付 webhook 签名失败。", exception);
        }
    }

    private boolean secureEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String requiredText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private Long longOrNull(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isNumber()) {
                return value.asLong();
            }
            if (value.isTextual() && !value.asText().isBlank()) {
                return Long.parseLong(value.asText());
            }
        }
        return null;
    }

    private record ReconcileRunResult(PaymentReconcileReportResponse report, long anomalyOrders) {
    }
}
