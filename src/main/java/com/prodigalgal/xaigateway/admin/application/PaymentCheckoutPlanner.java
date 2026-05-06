package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.infra.persistence.entity.PaymentOrderEntity;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class PaymentCheckoutPlanner {

    private static final Duration DEFAULT_CHECKOUT_TTL = Duration.ofMinutes(30);

    private final ObjectMapper objectMapper;

    PaymentCheckoutPlanner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    PaymentCheckoutPlan apply(PaymentOrderEntity order) {
        PaymentCheckoutPlan plan = plan(order);
        order.setProviderInstanceCode(plan.providerInstanceCode());
        order.setCheckoutUrl(plan.checkoutUrl());
        order.setCheckoutMethod(plan.checkoutMethod());
        order.setProviderPayloadJson(plan.providerPayloadJson());
        order.setCheckoutExpiresAt(plan.expiresAt());
        return plan;
    }

    PaymentCheckoutPlan plan(PaymentOrderEntity order) {
        JsonNode metadata = readMetadata(order.getMetadataJson());
        String provider = normalizeProvider(order.getProvider());
        String providerInstanceCode = firstNonBlank(
                text(metadata, "providerInstanceCode"),
                text(metadata, "provider_instance_code"),
                provider + "-default"
        );
        String subject = firstNonBlank(text(metadata, "subject"), "x-ai-gateway token recharge");
        Instant expiresAt = Instant.now().plus(DEFAULT_CHECKOUT_TTL);

        Map<String, String> query = new LinkedHashMap<>();
        query.put("order_no", order.getOrderNo());
        query.put("amount_minor", String.valueOf(order.getAmountMinor()));
        query.put("currency", order.getCurrency());
        query.put("token_credits", String.valueOf(order.getTokenCredits()));
        query.put("provider_instance_code", providerInstanceCode);

        String method = switch (provider) {
            case "stripe" -> "stripe_checkout_session";
            case "easypay" -> "easypay_page";
            case "alipay" -> "alipay_page_pay";
            case "wechat" -> "wechat_native_qr";
            case "generic_hmac" -> "generic_hmac_page";
            default -> "mock_page";
        };
        String checkoutUrl = appendQuery(resolveCheckoutBaseUrl(provider, metadata), query);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", provider);
        payload.put("providerInstanceCode", providerInstanceCode);
        payload.put("checkoutMethod", method);
        payload.put("orderNo", order.getOrderNo());
        payload.put("amountMinor", order.getAmountMinor());
        payload.put("currency", order.getCurrency());
        payload.put("tokenCredits", order.getTokenCredits());
        payload.put("subject", subject);
        payload.put("successUrl", firstNonBlank(text(metadata, "successUrl"), text(metadata, "success_url")));
        payload.put("cancelUrl", firstNonBlank(text(metadata, "cancelUrl"), text(metadata, "cancel_url")));
        payload.put("notifyUrl", firstNonBlank(text(metadata, "notifyUrl"), text(metadata, "notify_url")));
        payload.put("expiresAt", expiresAt.toString());
        putSnapshot(payload, "invoice", invoiceSnapshot(metadata));
        putSnapshot(payload, "tax", taxSnapshot(metadata));
        putSnapshot(payload, "settlement", settlementSnapshot(order, metadata));
        putSnapshot(payload, "billing", billingSnapshot(metadata));

        return new PaymentCheckoutPlan(
                checkoutUrl,
                method,
                providerInstanceCode,
                writeJson(payload),
                expiresAt
        );
    }

    private String resolveCheckoutBaseUrl(String provider, JsonNode metadata) {
        String configured = firstNonBlank(text(metadata, "checkoutBaseUrl"), text(metadata, "checkout_base_url"));
        if (configured != null) {
            return configured;
        }
        return switch (provider) {
            case "stripe" -> "https://checkout.stripe.com/c/pay";
            case "easypay" -> "https://pay.easypay.example/submit";
            case "alipay" -> "https://openapi.alipay.com/gateway.do";
            case "wechat" -> "weixin://wxpay/bizpayurl";
            case "generic_hmac" -> "https://gateway.local/pay/generic_hmac";
            default -> "https://gateway.local/pay/mock";
        };
    }

    private JsonNode readMetadata(String metadataJson) {
        try {
            return objectMapper.readTree(metadataJson == null || metadataJson.isBlank() ? "{}" : metadataJson);
        } catch (Exception exception) {
            throw new IllegalArgumentException("支付订单 metadataJson 不是合法 JSON。", exception);
        }
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("生成支付 provider payload 失败。", exception);
        }
    }

    private String appendQuery(String baseUrl, Map<String, String> query) {
        StringBuilder builder = new StringBuilder(baseUrl);
        builder.append(baseUrl.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, String> entry : query.entrySet()) {
            if (!first) {
                builder.append("&");
            }
            builder.append(encode(entry.getKey())).append("=").append(encode(entry.getValue()));
            first = false;
        }
        return builder.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private Map<String, Object> invoiceSnapshot(JsonNode metadata) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        putIfPresent(snapshot, "number", firstNonBlank(text(metadata, "invoiceNo"), text(metadata, "invoice_no"), text(metadata, "invoiceNumber"), text(metadata, "invoice_number")));
        putIfPresent(snapshot, "title", firstNonBlank(text(metadata, "invoiceTitle"), text(metadata, "invoice_title")));
        putIfPresent(snapshot, "email", firstNonBlank(text(metadata, "invoiceEmail"), text(metadata, "invoice_email")));
        return snapshot;
    }

    private Map<String, Object> taxSnapshot(JsonNode metadata) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        putIfPresent(snapshot, "profileCode", firstNonBlank(text(metadata, "taxProfileCode"), text(metadata, "tax_profile_code"), text(metadata, "taxProfile"), text(metadata, "tax_profile")));
        putIfPresent(snapshot, "rateBps", longValue(metadata, "taxRateBps", "tax_rate_bps"));
        putIfPresent(snapshot, "inclusive", booleanValue(metadata, "taxInclusive", "tax_inclusive"));
        return snapshot;
    }

    private Map<String, Object> settlementSnapshot(PaymentOrderEntity order, JsonNode metadata) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        putIfPresent(snapshot, "currency", upper(firstNonBlank(text(metadata, "settlementCurrency"), text(metadata, "settlement_currency"))));
        putIfPresent(snapshot, "exchangeRate", firstNonBlank(text(metadata, "exchangeRate"), text(metadata, "exchange_rate")));
        putIfPresent(snapshot, "baseCurrency", upper(firstNonBlank(text(metadata, "baseCurrency"), text(metadata, "base_currency"), order.getCurrency())));
        putIfPresent(snapshot, "baseAmountMinor", longValue(metadata, "baseAmountMinor", "base_amount_minor", "baseAmount", "base_amount"));
        putIfPresent(snapshot, "settlementAmountMinor", longValue(metadata, "settlementAmountMinor", "settlement_amount_minor"));
        return snapshot;
    }

    private Map<String, Object> billingSnapshot(JsonNode metadata) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        putIfPresent(snapshot, "subscriptionId", firstNonBlank(text(metadata, "subscriptionId"), text(metadata, "subscription_id")));
        putIfPresent(snapshot, "periodStart", firstNonBlank(text(metadata, "billingPeriodStart"), text(metadata, "billing_period_start")));
        putIfPresent(snapshot, "periodEnd", firstNonBlank(text(metadata, "billingPeriodEnd"), text(metadata, "billing_period_end")));
        putIfPresent(snapshot, "cycle", firstNonBlank(text(metadata, "billingCycle"), text(metadata, "billing_cycle")));
        return snapshot;
    }

    private void putSnapshot(Map<String, Object> payload, String field, Map<String, Object> snapshot) {
        if (!snapshot.isEmpty()) {
            payload.put(field, snapshot);
        }
    }

    private void putIfPresent(Map<String, Object> snapshot, String field, Object value) {
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        if (value != null) {
            snapshot.put(field, value);
        }
    }

    private Long longValue(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isNumber()) {
                return value.asLong();
            }
            if (value.isTextual() && !value.asText().isBlank()) {
                return Long.parseLong(value.asText().trim());
            }
        }
        return null;
    }

    private Boolean booleanValue(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isBoolean()) {
                return value.asBoolean();
            }
            if (value.isTextual() && !value.asText().isBlank()) {
                return Boolean.parseBoolean(value.asText().trim());
            }
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText().trim() : null;
    }

    private String upper(String value) {
        return value == null ? null : value.toUpperCase(java.util.Locale.ROOT);
    }

    private String normalizeProvider(String provider) {
        return provider == null || provider.isBlank()
                ? "mock"
                : provider.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    record PaymentCheckoutPlan(
            String checkoutUrl,
            String checkoutMethod,
            String providerInstanceCode,
            String providerPayloadJson,
            Instant expiresAt
    ) {
    }
}
