package com.prodigalgal.xaigateway.gateway.core.observability;

import com.prodigalgal.xaigateway.infra.persistence.entity.RequestTraceDetailEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RequestTraceDetailArchiveEntity;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.repository.RequestTraceDetailArchiveRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RequestTraceDetailRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class GatewayRequestTraceDetailService {

    private static final Logger log = LoggerFactory.getLogger(GatewayRequestTraceDetailService.class);
    private static final int DEFAULT_MAX_PAYLOAD_LENGTH = 12_000;
    private static final int DEFAULT_MAX_METADATA_LENGTH = 4_000;
    private static final String REDACTED = "[REDACTED]";

    private final RequestTraceDetailRepository requestTraceDetailRepository;
    private final RequestTraceDetailArchiveRepository requestTraceDetailArchiveRepository;
    private final ObjectMapper objectMapper;
    private final GatewayProperties gatewayProperties;

    public GatewayRequestTraceDetailService(
            RequestTraceDetailRepository requestTraceDetailRepository,
            RequestTraceDetailArchiveRepository requestTraceDetailArchiveRepository,
            GatewayProperties gatewayProperties,
            ObjectMapper objectMapper) {
        this.requestTraceDetailRepository = requestTraceDetailRepository;
        this.requestTraceDetailArchiveRepository = requestTraceDetailArchiveRepository;
        this.gatewayProperties = gatewayProperties == null ? new GatewayProperties() : gatewayProperties;
        this.objectMapper = objectMapper;
    }

    public GatewayRequestTraceDetailService(
            RequestTraceDetailRepository requestTraceDetailRepository,
            ObjectMapper objectMapper) {
        this(requestTraceDetailRepository, null, new GatewayProperties(), objectMapper);
    }

    public void record(
            String requestId,
            RequestTraceStage stage,
            RequestTraceDirection direction,
            RequestTraceContentKind contentKind,
            Object payload) {
        record(requestId, stage, direction, contentKind, payload, Map.of());
    }

    public void record(
            String requestId,
            RequestTraceStage stage,
            RequestTraceDirection direction,
            RequestTraceContentKind contentKind,
            Object payload,
            Map<String, ?> metadata) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        try {
            persist(requestId, stage, direction, contentKind, payload, metadata);
        } catch (RuntimeException exception) {
            log.warn("记录请求详情追踪失败，requestId={} stage={}。", requestId, stage, exception);
        }
    }

    public void recordError(
            String requestId,
            RequestTraceStage stage,
            RequestTraceDirection direction,
            Throwable error,
            Map<String, ?> metadata) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("errorType", error == null ? null : error.getClass().getName());
        payload.put("message", error == null ? null : error.getMessage());
        record(requestId, stage, direction, RequestTraceContentKind.ERROR, payload, metadata);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void persist(
            String requestId,
            RequestTraceStage stage,
            RequestTraceDirection direction,
            RequestTraceContentKind contentKind,
            Object payload,
            Map<String, ?> metadata) {
        TraceDetailSettings settings = traceDetailSettings();
        if (!settings.enabled() || !shouldSample(requestId, settings.samplingRate())) {
            return;
        }
        SanitizedText payloadText = sanitizePayload(payload, settings.maxPayloadLength());
        SanitizedText metadataText = sanitizePayload(metadata == null ? Map.of() : metadata, settings.maxMetadataLength());

        RequestTraceDetailEntity entity = new RequestTraceDetailEntity();
        entity.setRequestId(requestId);
        entity.setStage(enumName(stage, RequestTraceStage.CUSTOM));
        entity.setDirection(enumName(direction, RequestTraceDirection.INTERNAL));
        entity.setContentKind(enumName(contentKind, RequestTraceContentKind.JSON));
        entity.setPayloadJson(payloadText.value());
        entity.setMetadataJson(metadataText.value());
        entity.setPayloadHash(payloadText.hash());
        entity.setMetadataHash(metadataText.hash());
        entity.setOriginalLength(payloadText.originalLength());
        entity.setStoredLength(payloadText.storedLength());
        entity.setMetadataOriginalLength(metadataText.originalLength());
        entity.setMetadataStoredLength(metadataText.storedLength());
        entity.setTruncated(payloadText.truncated());
        entity.setMetadataTruncated(metadataText.truncated());
        entity.setRedacted(payloadText.redacted() || metadataText.redacted());
        entity.setMetadataRedacted(metadataText.redacted());
        entity.setExpiresAt(expiresAt(settings.retentionTtl()));
        requestTraceDetailRepository.save(entity);
    }

    @Scheduled(fixedDelayString = "${gateway.observability.trace-detail.cleanup-interval:PT1H}")
    public void cleanupExpiredTraceDetails() {
        TraceDetailSettings settings = traceDetailSettings();
        if (!settings.cleanupEnabled()) {
            return;
        }
        try {
            archiveAndDeleteExpiredTraceDetails(Instant.now(), settings.cleanupBatchSize());
        } catch (RuntimeException exception) {
            log.warn("清理过期请求详情追踪失败。", exception);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int archiveAndDeleteExpiredTraceDetails(Instant cutoffAt, int batchSize) {
        if (requestTraceDetailArchiveRepository == null || cutoffAt == null) {
            return 0;
        }
        int resolvedBatchSize = Math.max(1, batchSize);
        List<RequestTraceDetailEntity> expired = requestTraceDetailRepository.findAllByExpiresAtBeforeOrderByExpiresAtAscIdAsc(
                cutoffAt,
                PageRequest.of(0, resolvedBatchSize)
        );
        if (expired.isEmpty()) {
            return 0;
        }
        RequestTraceDetailArchiveEntity archive = buildArchiveSummary(cutoffAt, expired, null);
        requestTraceDetailArchiveRepository.save(archive);
        requestTraceDetailRepository.deleteAllInBatch(expired);
        return expired.size();
    }

    private RequestTraceDetailArchiveEntity buildArchiveSummary(
            Instant cutoffAt,
            List<RequestTraceDetailEntity> expired,
            String errorMessage) {
        RequestTraceDetailArchiveEntity archive = new RequestTraceDetailArchiveEntity();
        archive.setArchiveBatchId(UUID.randomUUID().toString());
        archive.setCutoffAt(cutoffAt);
        archive.setArchivedCount(expired.size());
        archive.setEarliestCreatedAt(expired.stream()
                .map(RequestTraceDetailEntity::getCreatedAt)
                .filter(item -> item != null)
                .min(Comparator.naturalOrder())
                .orElse(null));
        archive.setLatestCreatedAt(expired.stream()
                .map(RequestTraceDetailEntity::getCreatedAt)
                .filter(item -> item != null)
                .max(Comparator.naturalOrder())
                .orElse(null));
        archive.setStageCountsJson(serialize(stageCounts(expired)));
        archive.setStatus(errorMessage == null ? "COMPLETED" : "FAILED");
        archive.setErrorMessage(errorMessage);
        return archive;
    }

    private Map<String, Long> stageCounts(List<RequestTraceDetailEntity> details) {
        return details.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getStage() == null ? "UNKNOWN" : item.getStage(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    private SanitizedText sanitizePayload(Object value, int maxLength) {
        String serialized = serialize(value);
        String redacted = redact(serialized);
        boolean wasRedacted = !serialized.equals(redacted);
        boolean truncated = redacted.length() > maxLength;
        String stored = truncated ? redacted.substring(0, maxLength) : redacted;
        return new SanitizedText(
                stored,
                sha256Hex(redacted),
                serialized.length(),
                stored.length(),
                truncated,
                wasRedacted
        );
    }

    private String serialize(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof JsonNode jsonNode) {
            return jsonNode.toString();
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            return String.valueOf(value);
        }
    }

    private boolean shouldSample(String requestId, double samplingRate) {
        if (samplingRate >= 1.0d) {
            return true;
        }
        if (samplingRate <= 0.0d) {
            return false;
        }
        String hash = sha256Hex(requestId == null ? "" : requestId);
        long bucket = Long.parseUnsignedLong(hash.substring(0, 15), 16) % 10_000L;
        return bucket < Math.round(samplingRate * 10_000d);
    }

    private Instant expiresAt(Duration retentionTtl) {
        if (retentionTtl == null || retentionTtl.isZero() || retentionTtl.isNegative()) {
            return null;
        }
        return Instant.now().plus(retentionTtl);
    }

    private TraceDetailSettings traceDetailSettings() {
        GatewayProperties.Observability.TraceDetail traceDetail = gatewayProperties.getObservability().getTraceDetail();
        return new TraceDetailSettings(
                traceDetail.isEnabled(),
                clampRate(traceDetail.getSamplingRate()),
                positiveDuration(traceDetail.getRetentionTtl(), Duration.ofDays(7)),
                positiveInt(traceDetail.getMaxPayloadLength(), DEFAULT_MAX_PAYLOAD_LENGTH),
                positiveInt(traceDetail.getMaxMetadataLength(), DEFAULT_MAX_METADATA_LENGTH),
                traceDetail.isCleanupEnabled(),
                positiveInt(traceDetail.getCleanupBatchSize(), 1_000)
        );
    }

    private double clampRate(double samplingRate) {
        if (Double.isNaN(samplingRate) || samplingRate < 0.0d) {
            return 0.0d;
        }
        return Math.min(1.0d, samplingRate);
    }

    private Duration positiveDuration(Duration value, Duration fallback) {
        if (value == null || value.isZero() || value.isNegative()) {
            return fallback;
        }
        return value;
    }

    private int positiveInt(int value, int fallback) {
        return value <= 0 ? fallback : value;
    }

    private String redact(String value) {
        if (value == null || value.isBlank()) {
            return value == null ? "" : value;
        }
        String current = value
                .replaceAll("(?i)(authorization\"?\\s*[:=]\\s*\"?Bearer\\s+)[^\"\\s,}]+", "$1" + REDACTED)
                .replaceAll("(?i)(api[_-]?key\"?\\s*[:=]\\s*\"?)[^\"\\s,}]+", "$1" + REDACTED)
                .replaceAll("(?i)(access[_-]?token\"?\\s*[:=]\\s*\"?)[^\"\\s,}]+", "$1" + REDACTED)
                .replaceAll("(?i)(refresh[_-]?token\"?\\s*[:=]\\s*\"?)[^\"\\s,}]+", "$1" + REDACTED)
                .replaceAll("(?i)(id[_-]?token\"?\\s*[:=]\\s*\"?)[^\"\\s,}]+", "$1" + REDACTED)
                .replaceAll("sk-[A-Za-z0-9_-]{12,}", "sk-" + REDACTED)
                .replaceAll("Bearer\\s+[A-Za-z0-9._~+/=-]{8,}", "Bearer " + REDACTED);
        try {
            JsonNode node = objectMapper.readTree(current);
            JsonNode sanitized = redactJson(node);
            return sanitized.toString();
        } catch (RuntimeException ignored) {
            return current;
        }
    }

    private JsonNode redactJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode copy = objectMapper.createObjectNode();
            node.properties().forEach(entry -> {
                String key = entry.getKey();
                if (isSensitiveKey(key)) {
                    copy.put(key, REDACTED);
                } else {
                    copy.set(key, redactJson(entry.getValue()));
                }
            });
            return copy;
        }
        if (node.isArray()) {
            var array = objectMapper.createArrayNode();
            for (JsonNode item : node) {
                array.add(redactJson(item));
            }
            return array;
        }
        return node;
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace("-", "_");
        return normalized.contains("authorization")
                || normalized.contains("cookie")
                || normalized.contains("secret")
                || normalized.contains("api_key")
                || normalized.contains("access_token")
                || normalized.contains("refresh_token")
                || normalized.contains("id_token")
                || normalized.equals("token")
                || normalized.endsWith("_token");
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private <E extends Enum<E>> String enumName(E value, E fallback) {
        return (value == null ? fallback : value).name();
    }

    private record SanitizedText(
            String value,
            String hash,
            int originalLength,
            int storedLength,
            boolean truncated,
            boolean redacted
    ) {
    }

    private record TraceDetailSettings(
            boolean enabled,
            double samplingRate,
            Duration retentionTtl,
            int maxPayloadLength,
            int maxMetadataLength,
            boolean cleanupEnabled,
            int cleanupBatchSize
    ) {
    }
}
