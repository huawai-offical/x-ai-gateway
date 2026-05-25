package com.prodigalgal.xaigateway.gateway.core.observability;

import com.prodigalgal.xaigateway.infra.persistence.entity.RequestTraceDetailEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RequestTraceDetailArchiveEntity;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.repository.RequestTraceDetailArchiveRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RequestTraceDetailRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayRequestTraceDetailServiceTests {

    private static final int MAX_PAYLOAD_LENGTH = 12_000;
    private static final int MAX_METADATA_LENGTH = 4_000;
    private static final String REDACTED = "[REDACTED]";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRedactSensitiveFieldsFromMapPayloadBeforePersistingBody() {
        RequestTraceDetailRepository repository = Mockito.mock(RequestTraceDetailRepository.class);
        GatewayRequestTraceDetailService service = new GatewayRequestTraceDetailService(repository, objectMapper);
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("token", "plain-token-value");
        nested.put("safe", "visible");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("Authorization", "Bearer bearer-secret-value");
        payload.put("api_key", "sk-secret-api-key-value");
        payload.put("nested", nested);

        service.record(
                "req-redact-map",
                RequestTraceStage.DOWNSTREAM_REQUEST,
                RequestTraceDirection.DOWNSTREAM,
                RequestTraceContentKind.JSON,
                payload
        );

        RequestTraceDetailEntity entity = savedEntity(repository);
        String body = entity.getPayloadJson();
        assertTrue(entity.isRedacted());
        assertFalse(entity.isTruncated());
        assertTrue(body.contains("\"Authorization\":\"" + REDACTED + "\""));
        assertTrue(body.contains("\"api_key\":\"" + REDACTED + "\""));
        assertTrue(body.contains("\"token\":\"" + REDACTED + "\""));
        assertTrue(body.contains("\"safe\":\"visible\""));
        assertFalse(body.contains("bearer-secret-value"));
        assertFalse(body.contains("sk-secret-api-key-value"));
        assertFalse(body.contains("plain-token-value"));
        assertNotNull(entity.getExpiresAt());
    }

    @Test
    void shouldRedactSensitiveFieldsFromJsonPayloadBeforePersistingBody() throws Exception {
        RequestTraceDetailRepository repository = Mockito.mock(RequestTraceDetailRepository.class);
        GatewayRequestTraceDetailService service = new GatewayRequestTraceDetailService(repository, objectMapper);
        var payload = objectMapper.readTree("""
                {
                  "messages": [
                    {
                      "content": "hello",
                      "secret": "json-secret-value",
                      "refresh-token": "json-refresh-token-value"
                    }
                  ],
                  "id_token": "json-id-token-value"
                }
                """);

        service.record(
                "req-redact-json",
                RequestTraceStage.UPSTREAM_REQUEST,
                RequestTraceDirection.UPSTREAM,
                RequestTraceContentKind.JSON,
                payload
        );

        RequestTraceDetailEntity entity = savedEntity(repository);
        String body = entity.getPayloadJson();
        assertTrue(entity.isRedacted());
        assertTrue(body.contains("\"secret\":\"" + REDACTED + "\""));
        assertTrue(body.contains("\"refresh-token\":\"" + REDACTED + "\""));
        assertTrue(body.contains("\"id_token\":\"" + REDACTED + "\""));
        assertTrue(body.contains("\"content\":\"hello\""));
        assertFalse(body.contains("json-secret-value"));
        assertFalse(body.contains("json-refresh-token-value"));
        assertFalse(body.contains("json-id-token-value"));
    }

    @Test
    void shouldTruncateOversizedPayloadAndKeepTraceLengthMetadata() {
        RequestTraceDetailRepository repository = Mockito.mock(RequestTraceDetailRepository.class);
        GatewayRequestTraceDetailService service = new GatewayRequestTraceDetailService(repository, objectMapper);
        String payload = "x".repeat(MAX_PAYLOAD_LENGTH + 512);

        service.record(
                "req-truncate-payload",
                RequestTraceStage.UPSTREAM_RESPONSE,
                RequestTraceDirection.UPSTREAM,
                RequestTraceContentKind.TEXT,
                payload
        );

        RequestTraceDetailEntity entity = savedEntity(repository);
        assertTrue(entity.isTruncated());
        assertFalse(entity.isRedacted());
        assertEquals(MAX_PAYLOAD_LENGTH, entity.getStoredLength());
        assertEquals(MAX_PAYLOAD_LENGTH, entity.getPayloadJson().length());
        assertEquals(payload.length(), entity.getOriginalLength());
        assertNotNull(entity.getPayloadHash());
        assertEquals(64, entity.getPayloadHash().length());
    }

    @Test
    void shouldRedactAndTruncateMetadataWhenMetadataIsPersisted() {
        RequestTraceDetailRepository repository = Mockito.mock(RequestTraceDetailRepository.class);
        GatewayRequestTraceDetailService service = new GatewayRequestTraceDetailService(repository, objectMapper);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("access_token", "metadata-access-token-value");
        metadata.put("secret", "metadata-secret-value");
        metadata.put("large", "m".repeat(MAX_METADATA_LENGTH + 512));

        service.record(
                "req-metadata",
                RequestTraceStage.CUSTOM,
                RequestTraceDirection.INTERNAL,
                RequestTraceContentKind.JSON,
                Map.of("status", "ok"),
                metadata
        );

        RequestTraceDetailEntity entity = savedEntity(repository);
        String storedMetadata = entity.getMetadataJson();
        assertTrue(entity.isRedacted());
        assertFalse(entity.isTruncated());
        assertEquals(MAX_METADATA_LENGTH, storedMetadata.length());
        assertTrue(entity.isMetadataTruncated());
        assertTrue(entity.isMetadataRedacted());
        assertEquals(MAX_METADATA_LENGTH, entity.getMetadataStoredLength());
        assertTrue(entity.getMetadataOriginalLength() > entity.getMetadataStoredLength());
        assertNotNull(entity.getMetadataHash());
        assertTrue(storedMetadata.contains("\"access_token\":\"" + REDACTED + "\""));
        assertTrue(storedMetadata.contains("\"secret\":\"" + REDACTED + "\""));
        assertFalse(storedMetadata.contains("metadata-access-token-value"));
        assertFalse(storedMetadata.contains("metadata-secret-value"));
    }

    @Test
    void shouldSkipPersistingWhenSamplingRateIsZero() {
        RequestTraceDetailRepository repository = Mockito.mock(RequestTraceDetailRepository.class);
        GatewayProperties properties = new GatewayProperties();
        properties.getObservability().getTraceDetail().setSamplingRate(0.0d);
        GatewayRequestTraceDetailService service = new GatewayRequestTraceDetailService(
                repository,
                Mockito.mock(RequestTraceDetailArchiveRepository.class),
                properties,
                objectMapper
        );

        service.record(
                "req-sampled-out",
                RequestTraceStage.CUSTOM,
                RequestTraceDirection.INTERNAL,
                RequestTraceContentKind.JSON,
                Map.of("status", "ok")
        );

        Mockito.verify(repository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void shouldUseConfiguredRetentionTtlWhenPersistingExpiresAt() {
        RequestTraceDetailRepository repository = Mockito.mock(RequestTraceDetailRepository.class);
        GatewayProperties properties = new GatewayProperties();
        properties.getObservability().getTraceDetail().setRetentionTtl(Duration.ofHours(2));
        GatewayRequestTraceDetailService service = new GatewayRequestTraceDetailService(
                repository,
                Mockito.mock(RequestTraceDetailArchiveRepository.class),
                properties,
                objectMapper
        );

        Instant before = Instant.now().plus(Duration.ofMinutes(119));
        service.record(
                "req-expiry",
                RequestTraceStage.CUSTOM,
                RequestTraceDirection.INTERNAL,
                RequestTraceContentKind.JSON,
                Map.of("status", "ok")
        );
        Instant after = Instant.now().plus(Duration.ofMinutes(121));

        RequestTraceDetailEntity entity = savedEntity(repository);
        assertNotNull(entity.getExpiresAt());
        assertFalse(entity.getExpiresAt().isBefore(before));
        assertFalse(entity.getExpiresAt().isAfter(after));
    }

    @Test
    void shouldArchiveAndDeleteExpiredTraceDetailsInBatch() {
        RequestTraceDetailRepository repository = Mockito.mock(RequestTraceDetailRepository.class);
        RequestTraceDetailArchiveRepository archiveRepository = Mockito.mock(RequestTraceDetailArchiveRepository.class);
        GatewayRequestTraceDetailService service = new GatewayRequestTraceDetailService(
                repository,
                archiveRepository,
                new GatewayProperties(),
                objectMapper
        );
        Instant cutoff = Instant.parse("2026-05-25T10:00:00Z");
        RequestTraceDetailEntity first = traceDetail(1L, "DOWNSTREAM_REQUEST", "2026-05-24T09:00:00Z");
        RequestTraceDetailEntity second = traceDetail(2L, "UPSTREAM_RESPONSE", "2026-05-24T09:01:00Z");
        Mockito.when(repository.findAllByExpiresAtBeforeOrderByExpiresAtAscIdAsc(Mockito.eq(cutoff), Mockito.any(Pageable.class)))
                .thenReturn(List.of(first, second));

        int deleted = service.archiveAndDeleteExpiredTraceDetails(cutoff, 500);

        assertEquals(2, deleted);
        ArgumentCaptor<RequestTraceDetailArchiveEntity> archiveCaptor =
                ArgumentCaptor.forClass(RequestTraceDetailArchiveEntity.class);
        Mockito.verify(archiveRepository).save(archiveCaptor.capture());
        RequestTraceDetailArchiveEntity archive = archiveCaptor.getValue();
        assertEquals(2, archive.getArchivedCount());
        assertEquals(cutoff, archive.getCutoffAt());
        assertEquals("COMPLETED", archive.getStatus());
        assertTrue(archive.getStageCountsJson().contains("DOWNSTREAM_REQUEST"));
        assertTrue(archive.getStageCountsJson().contains("UPSTREAM_RESPONSE"));
        Mockito.verify(repository).deleteAllInBatch(List.of(first, second));
    }

    private RequestTraceDetailEntity traceDetail(Long id, String stage, String createdAt) {
        RequestTraceDetailEntity entity = new RequestTraceDetailEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse(createdAt));
        entity.setStage(stage);
        return entity;
    }

    private RequestTraceDetailEntity savedEntity(RequestTraceDetailRepository repository) {
        ArgumentCaptor<RequestTraceDetailEntity> captor = ArgumentCaptor.forClass(RequestTraceDetailEntity.class);
        Mockito.verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
