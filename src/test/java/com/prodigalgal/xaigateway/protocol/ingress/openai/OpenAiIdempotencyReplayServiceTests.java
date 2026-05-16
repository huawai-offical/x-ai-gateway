package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.infra.persistence.entity.OpenAiIdempotencyRecordEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.OpenAiIdempotencyRecordRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiIdempotencyReplayServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRememberAndReplayMatchingPayload() throws Exception {
        OpenAiIdempotencyRecordRepository repository = Mockito.mock(OpenAiIdempotencyRecordRepository.class);
        OpenAiIdempotencyReplayService service = new OpenAiIdempotencyReplayService(repository, objectMapper);
        JsonNode request = objectMapper.readTree("{\"model\":\"gpt-4o\",\"input\":\"hello\"}");
        JsonNode response = objectMapper.readTree("{\"id\":\"resp_1\",\"object\":\"response\",\"output_text\":\"ok\"}");
        Mockito.when(repository.findByDistributedKeyIdAndRequestPathAndIdempotencyKey(
                        1L,
                        "/v1/responses",
                        "idem-1"
                ))
                .thenReturn(Optional.empty());
        Mockito.when(repository.saveAndFlush(Mockito.any(OpenAiIdempotencyRecordEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        JsonNode remembered = service.remember(1L, "/v1/responses", " idem-1 ", request, response);

        assertEquals("ok", remembered.path("output_text").asText());
        ArgumentCaptor<OpenAiIdempotencyRecordEntity> captor = ArgumentCaptor.forClass(OpenAiIdempotencyRecordEntity.class);
        Mockito.verify(repository).saveAndFlush(captor.capture());
        OpenAiIdempotencyRecordEntity saved = captor.getValue();
        assertEquals(1L, saved.getDistributedKeyId());
        assertEquals("/v1/responses", saved.getRequestPath());
        assertEquals("idem-1", saved.getIdempotencyKey());
        assertEquals("response", saved.getResponseObjectType());

        Mockito.when(repository.findByDistributedKeyIdAndRequestPathAndIdempotencyKey(
                        1L,
                        "/v1/responses",
                        "idem-1"
                ))
                .thenReturn(Optional.of(saved));

        Optional<JsonNode> replayed = service.replay(1L, "/v1/responses", "idem-1", request);

        assertTrue(replayed.isPresent());
        assertEquals("resp_1", replayed.get().path("id").asText());
        assertEquals("ok", replayed.get().path("output_text").asText());
    }

    @Test
    void shouldRejectSameIdempotencyKeyWithDifferentRequestPayload() throws Exception {
        OpenAiIdempotencyRecordRepository repository = Mockito.mock(OpenAiIdempotencyRecordRepository.class);
        OpenAiIdempotencyReplayService service = new OpenAiIdempotencyReplayService(repository, objectMapper);
        JsonNode request = objectMapper.readTree("{\"model\":\"gpt-4o\",\"input\":\"first\"}");
        JsonNode response = objectMapper.readTree("{\"id\":\"resp_1\",\"object\":\"response\"}");
        Mockito.when(repository.findByDistributedKeyIdAndRequestPathAndIdempotencyKey(
                        1L,
                        "/v1/responses",
                        "idem-1"
                ))
                .thenReturn(Optional.empty());
        Mockito.when(repository.saveAndFlush(Mockito.any(OpenAiIdempotencyRecordEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service.remember(1L, "/v1/responses", "idem-1", request, response);
        ArgumentCaptor<OpenAiIdempotencyRecordEntity> captor = ArgumentCaptor.forClass(OpenAiIdempotencyRecordEntity.class);
        Mockito.verify(repository).saveAndFlush(captor.capture());
        Mockito.when(repository.findByDistributedKeyIdAndRequestPathAndIdempotencyKey(
                        1L,
                        "/v1/responses",
                        "idem-1"
                ))
                .thenReturn(Optional.of(captor.getValue()));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.replay(
                        1L,
                        "/v1/responses",
                        "idem-1",
                        objectMapper.readTree("{\"model\":\"gpt-4o\",\"input\":\"second\"}")
                )
        );

        assertEquals("Idempotency-Key 已被不同请求体使用。", exception.getMessage());
    }

    @Test
    void shouldIgnoreBlankIdempotencyKey() throws Exception {
        OpenAiIdempotencyRecordRepository repository = Mockito.mock(OpenAiIdempotencyRecordRepository.class);
        OpenAiIdempotencyReplayService service = new OpenAiIdempotencyReplayService(repository, objectMapper);
        JsonNode request = objectMapper.readTree("{\"model\":\"gpt-4o\"}");
        JsonNode response = objectMapper.readTree("{\"id\":\"chatcmpl_1\",\"object\":\"chat.completion\"}");

        assertFalse(service.replay(1L, "/v1/chat/completions", " ", request).isPresent());
        JsonNode remembered = service.remember(1L, "/v1/chat/completions", null, request, response);

        assertEquals("chatcmpl_1", remembered.path("id").asText());
        Mockito.verifyNoInteractions(repository);
    }

    @Test
    void shouldPurgeExpiredRecordsByRetentionWindow() {
        OpenAiIdempotencyRecordRepository repository = Mockito.mock(OpenAiIdempotencyRecordRepository.class);
        OpenAiIdempotencyReplayService service = new OpenAiIdempotencyReplayService(
                repository,
                objectMapper,
                Duration.ofHours(12));
        Instant now = Instant.parse("2026-05-15T12:00:00Z");
        Instant cutoff = Instant.parse("2026-05-15T00:00:00Z");
        Mockito.when(repository.deleteByCreatedAtBefore(cutoff)).thenReturn(3L);

        long deleted = service.purgeExpiredRecords(now);

        assertEquals(3L, deleted);
        Mockito.verify(repository).deleteByCreatedAtBefore(cutoff);
    }

    @Test
    void shouldUseDefaultRetentionWhenConfiguredRetentionIsInvalid() {
        OpenAiIdempotencyRecordRepository repository = Mockito.mock(OpenAiIdempotencyRecordRepository.class);
        OpenAiIdempotencyReplayService service = new OpenAiIdempotencyReplayService(
                repository,
                objectMapper,
                Duration.ZERO);

        assertEquals(OpenAiIdempotencyReplayService.DEFAULT_RETENTION_WINDOW, service.retentionWindow());
    }
}
