package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.gateway.core.auth.RateLimitStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OpenAiWebhookSignatureVerifier {

    public static final String WEBHOOK_ID_HEADER = "webhook-id";
    public static final String WEBHOOK_TIMESTAMP_HEADER = "webhook-timestamp";
    public static final String WEBHOOK_SIGNATURE_HEADER = "webhook-signature";
    static final Duration DEFAULT_TOLERANCE = Duration.ofMinutes(5);
    static final Duration DEFAULT_REPLAY_WINDOW = Duration.ofHours(24);

    private final RateLimitStore replayStore;
    private final Clock clock;
    private final String configuredSecret;
    private final Duration timestampTolerance;
    private final Duration replayWindow;

    @Autowired
    public OpenAiWebhookSignatureVerifier(
            RateLimitStore replayStore,
            @Value("${gateway.openai.webhook.secret:}") String configuredSecret,
            @Value("${gateway.openai.webhook.timestamp-tolerance:PT5M}") Duration timestampTolerance,
            @Value("${gateway.openai.webhook.replay-window:PT24H}") Duration replayWindow) {
        this(replayStore, Clock.systemUTC(), configuredSecret, timestampTolerance, replayWindow);
    }

    OpenAiWebhookSignatureVerifier(
            RateLimitStore replayStore,
            Clock clock,
            String configuredSecret,
            Duration timestampTolerance,
            Duration replayWindow) {
        this.replayStore = replayStore;
        this.clock = clock;
        this.configuredSecret = normalizeBlank(configuredSecret);
        this.timestampTolerance = normalizeDuration(timestampTolerance, DEFAULT_TOLERANCE);
        this.replayWindow = normalizeDuration(replayWindow, DEFAULT_REPLAY_WINDOW);
    }

    public OpenAiWebhookVerificationResult verify(
            String webhookId,
            String webhookTimestamp,
            String webhookSignature,
            String rawPayload) {
        return verify(webhookId, webhookTimestamp, webhookSignature, rawPayload, null, true);
    }

    public OpenAiWebhookVerificationResult verify(
            String webhookId,
            String webhookTimestamp,
            String webhookSignature,
            String rawPayload,
            String signingSecret,
            boolean markReplay) {
        String resolvedWebhookId = required(webhookId, "webhook-id 不能为空。");
        long timestamp = parseTimestamp(required(webhookTimestamp, "webhook-timestamp 不能为空。"));
        String resolvedSignature = required(webhookSignature, "webhook-signature 不能为空。");
        String resolvedSecret = resolveSecret(signingSecret);
        assertTimestampFresh(timestamp);
        assertSignatureMatches(resolvedWebhookId, timestamp, rawPayload, resolvedSecret, resolvedSignature);
        boolean duplicateDelivery = false;
        if (markReplay) {
            duplicateDelivery = replayStore.increment(replayKey(resolvedWebhookId), 1L, replayWindow) > 1L;
        }
        return new OpenAiWebhookVerificationResult(resolvedWebhookId, Instant.ofEpochSecond(timestamp), duplicateDelivery);
    }

    private void assertTimestampFresh(long timestamp) {
        long now = Instant.now(clock).getEpochSecond();
        if (Math.abs(now - timestamp) > timestampTolerance.toSeconds()) {
            throw new IllegalArgumentException("OpenAI webhook timestamp 超出允许窗口。");
        }
    }

    private void assertSignatureMatches(
            String webhookId,
            long timestamp,
            String rawPayload,
            String signingSecret,
            String webhookSignature) {
        byte[] expected = hmacSha256(secretBytes(signingSecret), signedPayload(webhookId, timestamp, rawPayload));
        for (String candidate : webhookSignature.trim().split("\\s+")) {
            if (matchesSignature(expected, candidate)) {
                return;
            }
        }
        throw new IllegalArgumentException("OpenAI webhook signature 校验失败。");
    }

    private boolean matchesSignature(byte[] expected, String candidate) {
        if (candidate == null || !candidate.startsWith("v1,")) {
            return false;
        }
        try {
            byte[] actual = Base64.getDecoder().decode(candidate.substring(3));
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private byte[] signedPayload(String webhookId, long timestamp, String rawPayload) {
        String payload = rawPayload == null ? "" : rawPayload;
        return (webhookId + "." + timestamp + "." + payload).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] hmacSha256(byte[] secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算 OpenAI webhook signature。", exception);
        }
    }

    private byte[] secretBytes(String signingSecret) {
        String secret = required(signingSecret, "OpenAI webhook secret 不能为空。");
        if (secret.startsWith("whsec_")) {
            try {
                return Base64.getDecoder().decode(secret.substring("whsec_".length()));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("OpenAI webhook secret 不是合法 whsec_ base64。", exception);
            }
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    private String resolveSecret(String signingSecret) {
        String normalized = normalizeBlank(signingSecret);
        if (normalized != null) {
            return normalized;
        }
        return required(configuredSecret, "OpenAI webhook secret 不能为空。");
    }

    private long parseTimestamp(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("webhook-timestamp 必须是 Unix seconds。", exception);
        }
    }

    private String replayKey(String webhookId) {
        return "xag:openai:webhook:delivery:" + webhookId;
    }

    private String required(String value, String message) {
        String normalized = normalizeBlank(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Duration normalizeDuration(Duration value, Duration fallback) {
        if (value == null || value.isZero() || value.isNegative()) {
            return fallback;
        }
        return value;
    }

    public record OpenAiWebhookVerificationResult(
            String webhookId,
            Instant timestamp,
            boolean duplicateDelivery
    ) {
    }
}
