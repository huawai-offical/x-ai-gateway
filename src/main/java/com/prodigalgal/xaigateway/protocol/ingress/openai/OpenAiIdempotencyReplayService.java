package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.infra.persistence.entity.OpenAiIdempotencyRecordEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.OpenAiIdempotencyRecordRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class OpenAiIdempotencyReplayService {

    public static final String REPLAYED_HEADER = "X-AI-Gateway-Idempotency-Replayed";
    static final Duration DEFAULT_RETENTION_WINDOW = Duration.ofHours(24);

    private final OpenAiIdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final Duration retentionWindow;

    @Autowired
    public OpenAiIdempotencyReplayService(
            OpenAiIdempotencyRecordRepository repository,
            ObjectMapper objectMapper,
            @Value("${gateway.openai.idempotency.retention:PT24H}") Duration retentionWindow) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.retentionWindow = normalizeRetention(retentionWindow);
    }

    OpenAiIdempotencyReplayService(
            OpenAiIdempotencyRecordRepository repository,
            ObjectMapper objectMapper) {
        this(repository, objectMapper, DEFAULT_RETENTION_WINDOW);
    }

    @Transactional(readOnly = true)
    public Optional<JsonNode> replay(
            Long distributedKeyId,
            String requestPath,
            String idempotencyKey,
            JsonNode requestPayload) {
        String normalizedKey = normalizeKey(idempotencyKey);
        if (distributedKeyId == null || normalizedKey == null || requestPath == null || requestPath.isBlank()) {
            return Optional.empty();
        }
        return repository.findByDistributedKeyIdAndRequestPathAndIdempotencyKey(
                        distributedKeyId,
                        requestPath,
                        normalizedKey
                )
                .map(record -> {
                    ensureSameRequest(record, requestPayload);
                    return parseResponse(record);
                });
    }

    @Transactional
    public JsonNode remember(
            Long distributedKeyId,
            String requestPath,
            String idempotencyKey,
            JsonNode requestPayload,
            JsonNode responsePayload) {
        String normalizedKey = normalizeKey(idempotencyKey);
        if (distributedKeyId == null || normalizedKey == null || requestPath == null || requestPath.isBlank()) {
            return responsePayload;
        }
        Optional<OpenAiIdempotencyRecordEntity> existing = repository
                .findByDistributedKeyIdAndRequestPathAndIdempotencyKey(distributedKeyId, requestPath, normalizedKey);
        if (existing.isPresent()) {
            ensureSameRequest(existing.get(), requestPayload);
            return parseResponse(existing.get());
        }

        OpenAiIdempotencyRecordEntity entity = new OpenAiIdempotencyRecordEntity();
        entity.setDistributedKeyId(distributedKeyId);
        entity.setRequestPath(requestPath);
        entity.setIdempotencyKey(normalizedKey);
        entity.setRequestFingerprint(fingerprint(requestPayload));
        entity.setResponseStatus(200);
        entity.setResponseObjectType(responseObjectType(responsePayload));
        entity.setResponsePayloadJson(writeJson(responsePayload));
        try {
            repository.saveAndFlush(entity);
            return responsePayload;
        } catch (DataIntegrityViolationException exception) {
            OpenAiIdempotencyRecordEntity raced = repository
                    .findByDistributedKeyIdAndRequestPathAndIdempotencyKey(distributedKeyId, requestPath, normalizedKey)
                    .orElseThrow(() -> exception);
            ensureSameRequest(raced, requestPayload);
            return parseResponse(raced);
        }
    }

    @Scheduled(fixedDelayString = "${gateway.openai.idempotency.cleanup-fixed-delay:PT1H}")
    @Transactional
    public void purgeExpiredRecordsOnSchedule() {
        purgeExpiredRecords(Instant.now());
    }

    @Transactional
    public long purgeExpiredRecords(Instant now) {
        Instant resolvedNow = now == null ? Instant.now() : now;
        return repository.deleteByCreatedAtBefore(resolvedNow.minus(retentionWindow));
    }

    Duration retentionWindow() {
        return retentionWindow;
    }

    private void ensureSameRequest(OpenAiIdempotencyRecordEntity record, JsonNode requestPayload) {
        String currentFingerprint = fingerprint(requestPayload);
        if (!currentFingerprint.equals(record.getRequestFingerprint())) {
            throw new IllegalArgumentException("Idempotency-Key 已被不同请求体使用。");
        }
    }

    private JsonNode parseResponse(OpenAiIdempotencyRecordEntity record) {
        try {
            return objectMapper.readTree(record.getResponsePayloadJson());
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取 Idempotency-Key 缓存响应。", exception);
        }
    }

    private String responseObjectType(JsonNode responsePayload) {
        if (responsePayload == null || responsePayload.isNull()) {
            return null;
        }
        String objectType = responsePayload.path("object").asText(null);
        return objectType == null || objectType.isBlank() ? null : objectType;
    }

    private String fingerprint(JsonNode payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(writeJson(payload).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成 Idempotency-Key 请求指纹。", exception);
        }
    }

    private String writeJson(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? objectMapper.createObjectNode() : payload);
        } catch (Exception exception) {
            throw new IllegalStateException("无法序列化 Idempotency-Key JSON。", exception);
        }
    }

    private String normalizeKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String normalized = idempotencyKey.trim();
        return normalized.length() <= 256 ? normalized : normalized.substring(0, 256);
    }

    private Duration normalizeRetention(Duration retentionWindow) {
        if (retentionWindow == null || retentionWindow.isZero() || retentionWindow.isNegative()) {
            return DEFAULT_RETENTION_WINDOW;
        }
        return retentionWindow;
    }
}
