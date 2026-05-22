package com.prodigalgal.xaigateway.gateway.core.observability;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageCompleteness;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageSource;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.CacheHitLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RequestLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RouteDecisionLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UsageRecordEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.CacheHitLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RequestLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RouteDecisionLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UsageRecordRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayObservabilityAsyncPersistenceServiceTests {

    @Test
    void shouldFlushStartFinishAndUsageIntoSingleRequestLogAndUpsertUsageRecord() {
        QueueFixture fixture = new QueueFixture();

        GatewayObservabilityAsyncPersistenceService service = fixture.service();
        service.enqueueRequestLogStart(new GatewayObservabilityAsyncPersistenceService.RequestLogSnapshot(
                "req-1",
                1L,
                "sk-gw-test",
                "openai",
                "/v1/chat/completions",
                "chat",
                "chat_completions",
                "model-a",
                "model-a",
                "model-a",
                "model-a",
                ProviderType.OPENAI_DIRECT,
                101L,
                "WEIGHTED_HASH",
                "native",
                "NATIVE",
                "NATIVE",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "prefix-1",
                "finger-1",
                false,
                GatewayRequestStatus.IN_PROGRESS,
                null,
                null,
                null,
                Instant.parse("2026-04-20T12:00:00Z"),
                null
        ));
        service.enqueueRequestLogFinish(new GatewayObservabilityAsyncPersistenceService.RequestLogSnapshot(
                "req-1",
                1L,
                "sk-gw-test",
                "openai",
                "/v1/chat/completions",
                "chat",
                "chat_completions",
                "model-a",
                "model-a",
                "model-a",
                "model-a",
                ProviderType.OPENAI_DIRECT,
                101L,
                "WEIGHTED_HASH",
                "native",
                "NATIVE",
                "NATIVE",
                null,
                "response-1",
                "object",
                "response",
                "resp_123",
                "completed",
                2,
                "prefix-1",
                "finger-1",
                false,
                GatewayRequestStatus.COMPLETED,
                null,
                null,
                320L,
                Instant.parse("2026-04-20T12:00:00Z"),
                Instant.parse("2026-04-20T12:00:00.320Z")
        ));
        service.enqueueUsageRecordUpsert(new GatewayObservabilityAsyncPersistenceService.UsageRecordSnapshot(
                "req-1",
                1L,
                "openai",
                "/v1/chat/completions",
                "model-a",
                ProviderType.OPENAI_DIRECT,
                101L,
                false,
                GatewayUsageCompleteness.PARTIAL,
                GatewayUsageSource.LAST_VISIBLE,
                10,
                8,
                4,
                1,
                0,
                0,
                0,
                0,
                2,
                null,
                13,
                "{\"stage\":\"partial\"}"
        ));
        service.enqueueUsageRecordUpsert(new GatewayObservabilityAsyncPersistenceService.UsageRecordSnapshot(
                "req-1",
                1L,
                "openai",
                "/v1/chat/completions",
                "model-a",
                ProviderType.OPENAI_DIRECT,
                101L,
                false,
                GatewayUsageCompleteness.FINAL,
                GatewayUsageSource.DIRECT_RESPONSE,
                12,
                10,
                5,
                2,
                1,
                0,
                1,
                0,
                3,
                "cache-ref",
                18,
                "{\"stage\":\"final\"}"
        ));

        assertEquals(4, service.flushBatch());
        assertEquals(1, fixture.requestStore.size());
        assertEquals(1, fixture.usageStore.size());
        assertEquals(GatewayRequestStatus.COMPLETED, fixture.requestStore.get("req-1").getStatus());
        assertEquals("resp_123", fixture.requestStore.get("req-1").getResponseObjectId());
        assertEquals(18, fixture.usageStore.get("req-1").getTotalTokens());
        assertEquals(GatewayUsageCompleteness.FINAL, fixture.usageStore.get("req-1").getCompleteness());
        assertEquals("cache-ref", fixture.usageStore.get("req-1").getCachedContentRef());
    }

    @Test
    void shouldFlushRouteDecisionAndCacheHitLogsAsBatchInsert() {
        QueueFixture fixture = new QueueFixture();

        GatewayObservabilityAsyncPersistenceService service = fixture.service();
        service.enqueueRouteDecisionLogInsert(new GatewayObservabilityAsyncPersistenceService.RouteDecisionLogSnapshot(
                "req-route",
                2L,
                "sk-gw-route",
                "model-b",
                "model-b",
                "model-b",
                "openai",
                "/v1/files",
                "file",
                "file_create",
                "model-b",
                "WEIGHTED_HASH",
                "orchestration",
                "DEGRADED",
                "LOSSY",
                "resource-orchestration",
                202L,
                ProviderType.OPENAI_DIRECT,
                "https://example.com",
                "prefix-route",
                "finger-route",
                3,
                "{\"candidates\":[]}"
        ));
        service.enqueueCacheHitLogInsert(new GatewayObservabilityAsyncPersistenceService.CacheHitLogSnapshot(
                "req-route",
                2L,
                "openai",
                "/v1/files",
                "file",
                "file_create",
                ProviderType.OPENAI_DIRECT,
                202L,
                "model-b",
                "prefix-route",
                "finger-route",
                "prompt_cache",
                "orchestration",
                "DEGRADED",
                "LOSSY",
                "resource-orchestration",
                12,
                4,
                8,
                "cache-ref"
        ));

        assertEquals(2, service.flushBatch());
        assertEquals(1, fixture.savedRouteDecisions.size());
        assertEquals(1, fixture.savedCacheHits.size());
        assertEquals("LOSSY", fixture.savedRouteDecisions.getFirst().getDegradationLevel());
        assertEquals(12, fixture.savedCacheHits.getFirst().getCacheHitTokens());
    }

    @Test
    void shouldRequeueBatchInOriginalOrderWhenDatabaseWriteFails() {
        QueueFixture fixture = new QueueFixture();
        Mockito.when(fixture.requestLogRepository.saveAll(Mockito.anyIterable()))
                .thenThrow(new IllegalStateException("db down"));

        GatewayObservabilityAsyncPersistenceService service = fixture.service();
        service.enqueueRequestLogStart(new GatewayObservabilityAsyncPersistenceService.RequestLogSnapshot(
                "req-a",
                1L,
                "sk-a",
                "openai",
                "/v1/a",
                "chat",
                "chat_completions",
                "model-a",
                "model-a",
                "model-a",
                "model-a",
                ProviderType.OPENAI_DIRECT,
                101L,
                "WEIGHTED_HASH",
                "native",
                "NATIVE",
                "NATIVE",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "prefix-a",
                "finger-a",
                false,
                GatewayRequestStatus.IN_PROGRESS,
                null,
                null,
                null,
                Instant.parse("2026-04-20T12:00:00Z"),
                null
        ));
        service.enqueueRequestLogStart(new GatewayObservabilityAsyncPersistenceService.RequestLogSnapshot(
                "req-b",
                2L,
                "sk-b",
                "openai",
                "/v1/b",
                "chat",
                "chat_completions",
                "model-b",
                "model-b",
                "model-b",
                "model-b",
                ProviderType.OPENAI_DIRECT,
                202L,
                "WEIGHTED_HASH",
                "native",
                "NATIVE",
                "NATIVE",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "prefix-b",
                "finger-b",
                false,
                GatewayRequestStatus.IN_PROGRESS,
                null,
                null,
                null,
                Instant.parse("2026-04-20T12:00:01Z"),
                null
        ));
        List<String> originalOrder = List.copyOf(fixture.queue);

        assertThrows(IllegalStateException.class, service::flushBatch);
        assertEquals(originalOrder, fixture.queue);
    }

    @Test
    void shouldBackOffFlushWhenRedisReadTimesOut() {
        QueueFixture fixture = new QueueFixture();
        fixture.gatewayProperties.getObservability().getAsync().setRedisFailureBackoff(Duration.ofMinutes(1));
        Mockito.when(fixture.listOperations.leftPop(Mockito.anyString(), Mockito.anyLong()))
                .thenThrow(new IllegalStateException("redis timeout"));

        GatewayObservabilityAsyncPersistenceService service = fixture.service();

        assertEquals(0, service.flushBatch());
        Mockito.verify(fixture.listOperations).leftPop("xag:observability:hot-path", 200L);

        Mockito.clearInvocations(fixture.redisTemplate, fixture.listOperations);
        assertEquals(0, service.flushBatch());
        Mockito.verifyNoInteractions(fixture.redisTemplate, fixture.listOperations);
    }

    @Test
    void shouldBackOffEnqueueWhenRedisWriteTimesOut() {
        QueueFixture fixture = new QueueFixture();
        fixture.gatewayProperties.getObservability().getAsync().setRedisFailureBackoff(Duration.ofMinutes(1));
        Mockito.doThrow(new IllegalStateException("redis timeout"))
                .when(fixture.listOperations)
                .rightPush(Mockito.anyString(), Mockito.anyString());

        GatewayObservabilityAsyncPersistenceService service = fixture.service();

        assertFalse(service.enqueueRequestLogStart(requestStartSnapshot("req-timeout")));
        Mockito.verify(fixture.listOperations).rightPush(Mockito.eq("xag:observability:hot-path"), Mockito.anyString());

        Mockito.clearInvocations(fixture.redisTemplate, fixture.listOperations);
        assertFalse(service.enqueueRequestLogStart(requestStartSnapshot("req-backoff")));
        Mockito.verifyNoInteractions(fixture.redisTemplate, fixture.listOperations);
        assertEquals(0, fixture.queue.size());
    }

    @Test
    void shouldRetryRedisQueueAfterBackoffExpires() {
        QueueFixture fixture = new QueueFixture();
        fixture.gatewayProperties.getObservability().getAsync().setRedisFailureBackoff(Duration.ZERO);
        Mockito.doThrow(new IllegalStateException("redis timeout"))
                .doAnswer(invocation -> {
                    fixture.queue.add(invocation.getArgument(1));
                    return (long) fixture.queue.size();
                })
                .when(fixture.listOperations)
                .rightPush(Mockito.anyString(), Mockito.anyString());

        GatewayObservabilityAsyncPersistenceService service = fixture.service();

        assertFalse(service.enqueueRequestLogStart(requestStartSnapshot("req-timeout")));
        assertTrue(service.enqueueRequestLogStart(requestStartSnapshot("req-recovered")));
        assertEquals(1, fixture.queue.size());
    }

    @Test
    void shouldMergeRuntimeMetricsThroughRedisQueueBeforeWritingBackToPg() {
        QueueFixture fixture = new QueueFixture();
        UpstreamCredentialEntity credential = new UpstreamCredentialEntity();
        UpstreamAccountEntity account = new UpstreamAccountEntity();
        Mockito.when(fixture.upstreamCredentialRepository.findById(101L)).thenReturn(java.util.Optional.of(credential));
        Mockito.when(fixture.upstreamAccountRepository.findById(201L)).thenReturn(java.util.Optional.of(account));

        GatewayObservabilityAsyncPersistenceService service = fixture.service();
        service.enqueueCredentialMetricsAccumulate(new GatewayObservabilityAsyncPersistenceService.RuntimeMetricSnapshot(
                101L,
                1,
                1,
                0,
                0,
                120,
                1,
                30,
                4,
                2,
                8,
                35,
                1,
                35L,
                35L,
                35L,
                Instant.parse("2026-04-20T12:00:00Z")
        ));
        service.enqueueCredentialMetricsAccumulate(new GatewayObservabilityAsyncPersistenceService.RuntimeMetricSnapshot(
                101L,
                1,
                0,
                1,
                0,
                180,
                1,
                20,
                5,
                0,
                6,
                20,
                1,
                20L,
                20L,
                20L,
                Instant.parse("2026-04-20T12:01:00Z")
        ));
        service.enqueueAccountMetricsAccumulate(new GatewayObservabilityAsyncPersistenceService.RuntimeMetricSnapshot(
                201L,
                1,
                0,
                0,
                1,
                90,
                1,
                10,
                0,
                0,
                0,
                0,
                0,
                null,
                null,
                null,
                Instant.parse("2026-04-20T12:02:00Z")
        ));

        assertEquals(3, service.flushBatch());
        assertEquals(2, credential.getTotalRequestCount());
        assertEquals(1, credential.getSuccessfulRequestCount());
        assertEquals(1, credential.getFailedRequestCount());
        assertEquals(300, credential.getTotalDurationMs());
        assertEquals(50, credential.getTotalTokenCount());
        assertEquals(9, credential.getTotalCacheHitTokenCount());
        assertEquals(14, credential.getTotalSavedInputTokenCount());
        assertEquals(55, credential.getTotalFirstTokenMs());
        assertEquals(2, credential.getFirstTokenSampleCount());
        assertEquals(20L, credential.getLastFirstTokenMs());
        assertEquals(20L, credential.getMinFirstTokenMs());
        assertEquals(35L, credential.getMaxFirstTokenMs());
        assertEquals(Instant.parse("2026-04-20T12:01:00Z"), credential.getLastUsedAt());
        assertEquals(1, account.getTotalRequestCount());
        assertEquals(1, account.getCanceledRequestCount());
        assertEquals(90, account.getTotalDurationMs());
        assertEquals(Instant.parse("2026-04-20T12:02:00Z"), account.getLastUsedAt());
        Mockito.verify(fixture.upstreamCredentialRepository).save(credential);
        Mockito.verify(fixture.upstreamAccountRepository).save(account);
    }

    private static GatewayObservabilityAsyncPersistenceService.RequestLogSnapshot requestStartSnapshot(String requestId) {
        return new GatewayObservabilityAsyncPersistenceService.RequestLogSnapshot(
                requestId,
                1L,
                "sk-gw-test",
                "openai",
                "/v1/chat/completions",
                "chat",
                "chat_completions",
                "model-a",
                "model-a",
                "model-a",
                "model-a",
                ProviderType.OPENAI_DIRECT,
                101L,
                "WEIGHTED_HASH",
                "native",
                "NATIVE",
                "NATIVE",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "prefix-1",
                "finger-1",
                false,
                GatewayRequestStatus.IN_PROGRESS,
                null,
                null,
                null,
                Instant.parse("2026-04-20T12:00:00Z"),
                null
        );
    }

    private static final class QueueFixture {
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final GatewayProperties gatewayProperties = new GatewayProperties();
        private final StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        private final ListOperations<String, String> listOperations = Mockito.mock(ListOperations.class);
        private final RequestLogRepository requestLogRepository = Mockito.mock(RequestLogRepository.class);
        private final UsageRecordRepository usageRecordRepository = Mockito.mock(UsageRecordRepository.class);
        private final RouteDecisionLogRepository routeDecisionLogRepository = Mockito.mock(RouteDecisionLogRepository.class);
        private final CacheHitLogRepository cacheHitLogRepository = Mockito.mock(CacheHitLogRepository.class);
        private final UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        private final UpstreamAccountRepository upstreamAccountRepository = Mockito.mock(UpstreamAccountRepository.class);
        private final PlatformTransactionManager transactionManager = Mockito.mock(PlatformTransactionManager.class);
        private final List<String> queue = new ArrayList<>();
        private final Map<String, RequestLogEntity> requestStore = new LinkedHashMap<>();
        private final Map<String, UsageRecordEntity> usageStore = new LinkedHashMap<>();
        private List<RouteDecisionLogEntity> savedRouteDecisions = List.of();
        private List<CacheHitLogEntity> savedCacheHits = List.of();

        QueueFixture() {
            Mockito.when(redisTemplate.opsForList()).thenReturn(listOperations);
            Mockito.when(transactionManager.getTransaction(Mockito.any())).thenReturn(new SimpleTransactionStatus());
            Mockito.when(listOperations.rightPush(Mockito.anyString(), Mockito.anyString()))
                    .thenAnswer(invocation -> {
                        queue.add(invocation.getArgument(1));
                        return (long) queue.size();
                    });
            Mockito.when(listOperations.leftPop(Mockito.anyString(), Mockito.anyLong()))
                    .thenAnswer(invocation -> {
                        long count = invocation.getArgument(1);
                        int size = Math.min((int) count, queue.size());
                        List<String> popped = new ArrayList<>(queue.subList(0, size));
                        queue.subList(0, size).clear();
                        return popped;
                    });
            Mockito.when(listOperations.leftPushAll(Mockito.anyString(), Mockito.<Collection<String>>any()))
                    .thenAnswer(invocation -> {
                        Collection<String> values = invocation.getArgument(1);
                        for (String value : values) {
                            queue.add(0, value);
                        }
                        return (long) queue.size();
                    });

            Mockito.when(requestLogRepository.saveAll(Mockito.anyIterable()))
                    .thenAnswer(invocation -> {
                        List<RequestLogEntity> saved = iterableToList(invocation.getArgument(0));
                        for (RequestLogEntity entity : saved) {
                            requestStore.put(entity.getRequestId(), entity);
                        }
                        return saved;
                    });
            Mockito.when(requestLogRepository.findAllByRequestIdIn(Mockito.anyIterable()))
                    .thenAnswer(invocation -> findRequestLogs(invocation.getArgument(0)));

            Mockito.when(usageRecordRepository.saveAll(Mockito.anyIterable()))
                    .thenAnswer(invocation -> {
                        List<UsageRecordEntity> saved = iterableToList(invocation.getArgument(0));
                        for (UsageRecordEntity entity : saved) {
                            usageStore.put(entity.getRequestId(), entity);
                        }
                        return saved;
                    });
            Mockito.when(usageRecordRepository.findAllByRequestIdIn(Mockito.anyIterable()))
                    .thenAnswer(invocation -> findUsageRecords(invocation.getArgument(0)));

            Mockito.when(routeDecisionLogRepository.saveAll(Mockito.anyIterable()))
                    .thenAnswer(invocation -> {
                        savedRouteDecisions = iterableToList(invocation.getArgument(0));
                        return savedRouteDecisions;
                    });
            Mockito.when(cacheHitLogRepository.saveAll(Mockito.anyIterable()))
                    .thenAnswer(invocation -> {
                        savedCacheHits = iterableToList(invocation.getArgument(0));
                        return savedCacheHits;
                    });
        }

        private GatewayObservabilityAsyncPersistenceService service() {
            return new GatewayObservabilityAsyncPersistenceService(
                    redisTemplate,
                    objectMapper,
                    requestLogRepository,
                    usageRecordRepository,
                    routeDecisionLogRepository,
                    cacheHitLogRepository,
                    upstreamCredentialRepository,
                    upstreamAccountRepository,
                    gatewayProperties,
                    transactionManager
            );
        }

        private List<RequestLogEntity> findRequestLogs(Iterable<String> requestIds) {
            List<RequestLogEntity> result = new ArrayList<>();
            for (String requestId : requestIds) {
                RequestLogEntity entity = requestStore.get(requestId);
                if (entity != null) {
                    result.add(entity);
                }
            }
            return result;
        }

        private List<UsageRecordEntity> findUsageRecords(Iterable<String> requestIds) {
            List<UsageRecordEntity> result = new ArrayList<>();
            for (String requestId : requestIds) {
                UsageRecordEntity entity = usageStore.get(requestId);
                if (entity != null) {
                    result.add(entity);
                }
            }
            return result;
        }

        private static <T> List<T> iterableToList(Iterable<T> iterable) {
            List<T> result = new ArrayList<>();
            for (T item : iterable) {
                result.add(item);
            }
            return result;
        }
    }
}
