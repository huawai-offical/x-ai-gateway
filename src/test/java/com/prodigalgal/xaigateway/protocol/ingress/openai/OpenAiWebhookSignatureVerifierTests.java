package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.gateway.core.auth.RateLimitStore;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiWebhookSignatureVerifierTests {

    private static final Instant NOW = Instant.parse("2026-05-15T12:00:00Z");

    @Test
    void shouldVerifyStandardWebhookSignatureAndMarkDuplicateDelivery() {
        InMemoryRateLimitStore replayStore = new InMemoryRateLimitStore();
        String rawSecret = "openai-webhook-secret-32-bytes";
        String whsec = "whsec_" + Base64.getEncoder().encodeToString(rawSecret.getBytes(StandardCharsets.UTF_8));
        OpenAiWebhookSignatureVerifier verifier = verifier(replayStore, whsec);
        String payload = "{\"object\":\"event\",\"id\":\"evt_1\",\"type\":\"response.completed\"}";
        String signature = signature("wh_1", NOW.getEpochSecond(), payload, rawSecret);

        var first = verifier.verify("wh_1", Long.toString(NOW.getEpochSecond()), signature, payload);
        var second = verifier.verify("wh_1", Long.toString(NOW.getEpochSecond()), signature, payload);

        assertEquals("wh_1", first.webhookId());
        assertEquals(NOW, first.timestamp());
        assertFalse(first.duplicateDelivery());
        assertTrue(second.duplicateDelivery());
    }

    @Test
    void shouldSupportRawSecretOverrideAndMultipleSignatures() {
        OpenAiWebhookSignatureVerifier verifier = verifier(new InMemoryRateLimitStore(), "configured-secret");
        String payload = "{\"id\":\"evt_2\"}";
        String validSignature = signature("wh_2", NOW.getEpochSecond(), payload, "override-secret");
        String signatureHeader = "v1,invalid " + validSignature;

        var result = verifier.verify(
                "wh_2",
                Long.toString(NOW.getEpochSecond()),
                signatureHeader,
                payload,
                "override-secret",
                false);

        assertEquals("wh_2", result.webhookId());
        assertFalse(result.duplicateDelivery());
    }

    @Test
    void shouldRejectInvalidSignature() {
        OpenAiWebhookSignatureVerifier verifier = verifier(new InMemoryRateLimitStore(), "secret");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> verifier.verify(
                        "wh_bad",
                        Long.toString(NOW.getEpochSecond()),
                        "v1," + Base64.getEncoder().encodeToString("bad".getBytes(StandardCharsets.UTF_8)),
                        "{}"));

        assertEquals("OpenAI webhook signature 校验失败。", exception.getMessage());
    }

    @Test
    void shouldRejectExpiredTimestamp() {
        OpenAiWebhookSignatureVerifier verifier = verifier(new InMemoryRateLimitStore(), "secret");
        long expired = NOW.minus(Duration.ofMinutes(10)).getEpochSecond();
        String payload = "{}";
        String signature = signature("wh_old", expired, payload, "secret");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> verifier.verify("wh_old", Long.toString(expired), signature, payload));

        assertEquals("OpenAI webhook timestamp 超出允许窗口。", exception.getMessage());
    }

    @Test
    void shouldRejectMissingHeaders() {
        OpenAiWebhookSignatureVerifier verifier = verifier(new InMemoryRateLimitStore(), "secret");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> verifier.verify("", Long.toString(NOW.getEpochSecond()), "v1,abc", "{}"));

        assertEquals("webhook-id 不能为空。", exception.getMessage());
    }

    private OpenAiWebhookSignatureVerifier verifier(RateLimitStore replayStore, String secret) {
        return new OpenAiWebhookSignatureVerifier(
                replayStore,
                Clock.fixed(NOW, ZoneOffset.UTC),
                secret,
                Duration.ofMinutes(5),
                Duration.ofHours(24));
    }

    private String signature(String webhookId, long timestamp, String rawPayload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((webhookId + "." + timestamp + "." + rawPayload).getBytes(StandardCharsets.UTF_8));
            return "v1," + Base64.getEncoder().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static class InMemoryRateLimitStore implements RateLimitStore {

        private final Map<String, Long> values = new HashMap<>();

        @Override
        public long get(String key) {
            return values.getOrDefault(key, 0L);
        }

        @Override
        public long increment(String key, long amount, Duration ttl) {
            long current = values.getOrDefault(key, 0L) + amount;
            values.put(key, current);
            return current;
        }

        @Override
        public long decrement(String key) {
            long current = Math.max(0L, values.getOrDefault(key, 0L) - 1L);
            values.put(key, current);
            return current;
        }
    }
}
