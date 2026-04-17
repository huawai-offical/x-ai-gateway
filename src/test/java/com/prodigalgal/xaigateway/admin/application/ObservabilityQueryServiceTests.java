package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AsyncResourceDetailResponse;
import com.prodigalgal.xaigateway.admin.api.AsyncResourceSummaryResponse;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestStatus;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceType;
import com.prodigalgal.xaigateway.infra.persistence.entity.CacheHitLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RequestLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RouteDecisionLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCacheReferenceEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UsageRecordEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.CacheHitLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RequestLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RouteDecisionLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCacheReferenceRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UsageRecordRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.node.JsonNodeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObservabilityQueryServiceTests {

    @Test
    void shouldCombineDistributedKeyAndProviderFiltersForCacheHits() {
        RouteDecisionLogRepository routeDecisionLogRepository = Mockito.mock(RouteDecisionLogRepository.class);
        CacheHitLogRepository cacheHitLogRepository = Mockito.mock(CacheHitLogRepository.class);
        RequestLogRepository requestLogRepository = Mockito.mock(RequestLogRepository.class);
        UpstreamCacheReferenceRepository upstreamCacheReferenceRepository = Mockito.mock(UpstreamCacheReferenceRepository.class);
        UsageRecordRepository usageRecordRepository = Mockito.mock(UsageRecordRepository.class);

        CacheHitLogEntity entity = new CacheHitLogEntity();
        entity.setDistributedKeyId(7L);
        entity.setProviderType(ProviderType.OPENAI_DIRECT);
        entity.setCacheHitTokens(120);

        when(cacheHitLogRepository.search(eq(7L), eq(ProviderType.OPENAI_DIRECT), ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(entity));

        ObservabilityQueryService service = new ObservabilityQueryService(
                routeDecisionLogRepository,
                cacheHitLogRepository,
                requestLogRepository,
                upstreamCacheReferenceRepository,
                usageRecordRepository
        );

        assertEquals(1, service.listCacheHits(7L, ProviderType.OPENAI_DIRECT).size());
        verify(cacheHitLogRepository).search(eq(7L), eq(ProviderType.OPENAI_DIRECT), ArgumentMatchers.any(Pageable.class));
    }

    @Test
    void shouldCombineDistributedKeyAndProviderFiltersForRouteDecisions() {
        RouteDecisionLogRepository routeDecisionLogRepository = Mockito.mock(RouteDecisionLogRepository.class);
        CacheHitLogRepository cacheHitLogRepository = Mockito.mock(CacheHitLogRepository.class);
        RequestLogRepository requestLogRepository = Mockito.mock(RequestLogRepository.class);
        UpstreamCacheReferenceRepository upstreamCacheReferenceRepository = Mockito.mock(UpstreamCacheReferenceRepository.class);
        UsageRecordRepository usageRecordRepository = Mockito.mock(UsageRecordRepository.class);

        RouteDecisionLogEntity entity = new RouteDecisionLogEntity();
        entity.setDistributedKeyId(7L);
        entity.setSelectedProviderType(ProviderType.OPENAI_DIRECT);

        when(routeDecisionLogRepository.search(eq(7L), eq(ProviderType.OPENAI_DIRECT), ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(entity));

        ObservabilityQueryService service = new ObservabilityQueryService(
                routeDecisionLogRepository,
                cacheHitLogRepository,
                requestLogRepository,
                upstreamCacheReferenceRepository,
                usageRecordRepository
        );

        assertEquals(1, service.listRouteDecisions(7L, ProviderType.OPENAI_DIRECT).size());
        verify(routeDecisionLogRepository).search(eq(7L), eq(ProviderType.OPENAI_DIRECT), ArgumentMatchers.any(Pageable.class));
    }

    @Test
    void shouldFilterActiveReferencesByDistributedKeyAndProviderInSummary() {
        RouteDecisionLogRepository routeDecisionLogRepository = Mockito.mock(RouteDecisionLogRepository.class);
        CacheHitLogRepository cacheHitLogRepository = Mockito.mock(CacheHitLogRepository.class);
        RequestLogRepository requestLogRepository = Mockito.mock(RequestLogRepository.class);
        UpstreamCacheReferenceRepository upstreamCacheReferenceRepository = Mockito.mock(UpstreamCacheReferenceRepository.class);
        UsageRecordRepository usageRecordRepository = Mockito.mock(UsageRecordRepository.class);

        when(routeDecisionLogRepository.search(eq(7L), eq(ProviderType.OPENAI_DIRECT), ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(new RouteDecisionLogEntity()));
        when(cacheHitLogRepository.search(eq(7L), eq(ProviderType.OPENAI_DIRECT), ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(new CacheHitLogEntity()));
        when(upstreamCacheReferenceRepository.search(
                eq(7L),
                eq(ProviderType.OPENAI_DIRECT),
                eq("ACTIVE"),
                ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(new UpstreamCacheReferenceEntity()));
        UsageRecordEntity usageRecordEntity = new UsageRecordEntity();
        usageRecordEntity.setCompleteness(com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageCompleteness.FINAL);
        when(usageRecordRepository.search(eq(7L), eq(ProviderType.OPENAI_DIRECT), ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(usageRecordEntity));

        ObservabilityQueryService service = new ObservabilityQueryService(
                routeDecisionLogRepository,
                cacheHitLogRepository,
                requestLogRepository,
                upstreamCacheReferenceRepository,
                usageRecordRepository
        );

        var summary = service.summary(7L, ProviderType.OPENAI_DIRECT);
        assertEquals(1, summary.sampledActiveUpstreamCacheReferenceCount());
        assertEquals(1, summary.sampledUsageRecordCount());
        assertEquals(1, summary.sampledFinalUsageRecordCount());
        verify(routeDecisionLogRepository).search(eq(7L), eq(ProviderType.OPENAI_DIRECT), ArgumentMatchers.any(Pageable.class));
        verify(upstreamCacheReferenceRepository).search(
                eq(7L),
                eq(ProviderType.OPENAI_DIRECT),
                eq("ACTIVE"),
                ArgumentMatchers.any(Pageable.class));
    }

    @Test
    void shouldMapCanonicalFieldsForRequestLogs() {
        RouteDecisionLogRepository routeDecisionLogRepository = Mockito.mock(RouteDecisionLogRepository.class);
        CacheHitLogRepository cacheHitLogRepository = Mockito.mock(CacheHitLogRepository.class);
        RequestLogRepository requestLogRepository = Mockito.mock(RequestLogRepository.class);
        UpstreamCacheReferenceRepository upstreamCacheReferenceRepository = Mockito.mock(UpstreamCacheReferenceRepository.class);
        UsageRecordRepository usageRecordRepository = Mockito.mock(UsageRecordRepository.class);

        RequestLogEntity entity = new RequestLogEntity();
        entity.setRequestId("req-1");
        entity.setDistributedKeyId(7L);
        entity.setProtocol("openai");
        entity.setRequestPath("/v1/files/file_123/content");
        entity.setResourceType("file");
        entity.setOperation("file_content_get");
        entity.setRequestedModel("gpt-4o");
        entity.setPublicModel("gpt-4o");
        entity.setResolvedModelKey("gpt-4o");
        entity.setProviderType(ProviderType.OPENAI_DIRECT);
        entity.setSupportStatus("DEGRADED");
        entity.setDegradationLevel("LOSSY");
        entity.setObjectMode("resource-orchestration");
        entity.setGatewayResourceKey("batch_1");
        entity.setResponseKind("binary");
        entity.setResponseObjectType("file.content");
        entity.setResponseObjectId("file_123");
        entity.setResponseStatus("completed");
        entity.setCanonicalEventCount(2);

        when(requestLogRepository.search(eq(7L), eq(ProviderType.OPENAI_DIRECT), ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(entity));

        ObservabilityQueryService service = new ObservabilityQueryService(
                routeDecisionLogRepository,
                cacheHitLogRepository,
                requestLogRepository,
                upstreamCacheReferenceRepository,
                usageRecordRepository
        );

        var logs = service.listRequestLogs(7L, ProviderType.OPENAI_DIRECT, null, null);
        assertEquals(1, logs.size());
        assertEquals("DEGRADED", logs.get(0).supportStatus());
        assertEquals("LOSSY", logs.get(0).degradationLevel());
        assertEquals("batch_1", logs.get(0).gatewayResourceKey());
        assertEquals("binary", logs.get(0).responseKind());
        assertEquals("file.content", logs.get(0).responseObjectType());
        verify(requestLogRepository).search(eq(7L), eq(ProviderType.OPENAI_DIRECT), ArgumentMatchers.any(Pageable.class));
    }

    @Test
    void shouldFilterRequestLogsByRequestId() {
        RouteDecisionLogRepository routeDecisionLogRepository = Mockito.mock(RouteDecisionLogRepository.class);
        CacheHitLogRepository cacheHitLogRepository = Mockito.mock(CacheHitLogRepository.class);
        RequestLogRepository requestLogRepository = Mockito.mock(RequestLogRepository.class);
        UpstreamCacheReferenceRepository upstreamCacheReferenceRepository = Mockito.mock(UpstreamCacheReferenceRepository.class);
        UsageRecordRepository usageRecordRepository = Mockito.mock(UsageRecordRepository.class);

        RequestLogEntity entity = requestLog("req-1", "batch_1");
        when(requestLogRepository.findByRequestId("req-1")).thenReturn(Optional.of(entity));

        ObservabilityQueryService service = new ObservabilityQueryService(
                routeDecisionLogRepository,
                cacheHitLogRepository,
                requestLogRepository,
                upstreamCacheReferenceRepository,
                usageRecordRepository
        );

        var logs = service.listRequestLogs(null, null, null, null, "req-1", null, null);
        assertEquals(1, logs.size());
        assertEquals("req-1", logs.get(0).requestId());
        verify(requestLogRepository).findByRequestId("req-1");
    }

    @Test
    void shouldFilterRequestLogsByGatewayResourceKey() {
        RouteDecisionLogRepository routeDecisionLogRepository = Mockito.mock(RouteDecisionLogRepository.class);
        CacheHitLogRepository cacheHitLogRepository = Mockito.mock(CacheHitLogRepository.class);
        RequestLogRepository requestLogRepository = Mockito.mock(RequestLogRepository.class);
        UpstreamCacheReferenceRepository upstreamCacheReferenceRepository = Mockito.mock(UpstreamCacheReferenceRepository.class);
        UsageRecordRepository usageRecordRepository = Mockito.mock(UsageRecordRepository.class);

        when(requestLogRepository.findTop100ByGatewayResourceKeyOrderByCreatedAtDesc("batch_1"))
                .thenReturn(List.of(requestLog("req-1", "batch_1")));
        when(requestLogRepository.findByRequestId("req-1"))
                .thenReturn(Optional.of(requestLog("req-1", "batch_1")));

        ObservabilityQueryService service = new ObservabilityQueryService(
                routeDecisionLogRepository,
                cacheHitLogRepository,
                requestLogRepository,
                upstreamCacheReferenceRepository,
                usageRecordRepository
        );

        var logs = service.listRequestLogs(null, null, null, null, null, "batch_1", null);
        assertEquals(1, logs.size());
        assertEquals("batch_1", logs.get(0).gatewayResourceKey());
        verify(requestLogRepository).findTop100ByGatewayResourceKeyOrderByCreatedAtDesc("batch_1");
    }

    @Test
    void shouldResolveRequestLogsFromUpstreamObjectId() {
        RouteDecisionLogRepository routeDecisionLogRepository = Mockito.mock(RouteDecisionLogRepository.class);
        CacheHitLogRepository cacheHitLogRepository = Mockito.mock(CacheHitLogRepository.class);
        RequestLogRepository requestLogRepository = Mockito.mock(RequestLogRepository.class);
        UpstreamCacheReferenceRepository upstreamCacheReferenceRepository = Mockito.mock(UpstreamCacheReferenceRepository.class);
        UsageRecordRepository usageRecordRepository = Mockito.mock(UsageRecordRepository.class);
        AsyncResourceAdminService asyncResourceAdminService = Mockito.mock(AsyncResourceAdminService.class);

        when(asyncResourceAdminService.findAsyncResourcesByUpstreamObjectId("upstream-1"))
                .thenReturn(List.of(new AsyncResourceSummaryResponse(
                        "batch_1",
                        GatewayAsyncResourceType.BATCH,
                        "in_progress",
                        "IN_PROGRESS",
                        false,
                        false,
                        "gateway-object-lineage",
                        "upstream-1",
                        1,
                        null,
                        null,
                        null,
                        Instant.parse("2026-04-07T08:00:00Z"),
                        Instant.parse("2026-04-07T08:01:00Z")
                )));
        when(requestLogRepository.findTop100ByGatewayResourceKeyOrderByCreatedAtDesc("batch_1"))
                .thenReturn(List.of(requestLog("req-1", "batch_1")));
        when(requestLogRepository.findByRequestId("req-1"))
                .thenReturn(Optional.of(requestLog("req-1", "batch_1")));

        ObservabilityQueryService service = new ObservabilityQueryService(
                routeDecisionLogRepository,
                cacheHitLogRepository,
                requestLogRepository,
                upstreamCacheReferenceRepository,
                usageRecordRepository,
                asyncResourceAdminService
        );

        var logs = service.listRequestLogs(null, null, null, null, null, null, "upstream-1");
        assertEquals(1, logs.size());
        assertEquals("req-1", logs.get(0).requestId());
        verify(asyncResourceAdminService).findAsyncResourcesByUpstreamObjectId("upstream-1");
    }

    @Test
    void shouldAggregateTraceByRequestId() {
        RouteDecisionLogRepository routeDecisionLogRepository = Mockito.mock(RouteDecisionLogRepository.class);
        CacheHitLogRepository cacheHitLogRepository = Mockito.mock(CacheHitLogRepository.class);
        RequestLogRepository requestLogRepository = Mockito.mock(RequestLogRepository.class);
        UpstreamCacheReferenceRepository upstreamCacheReferenceRepository = Mockito.mock(UpstreamCacheReferenceRepository.class);
        UsageRecordRepository usageRecordRepository = Mockito.mock(UsageRecordRepository.class);
        AsyncResourceAdminService asyncResourceAdminService = Mockito.mock(AsyncResourceAdminService.class);

        RequestLogEntity requestLog = requestLog("req-1", "batch_1");
        requestLog.setDistributedKeyId(7L);
        requestLog.setProviderType(ProviderType.OPENAI_DIRECT);
        when(requestLogRepository.findByRequestId("req-1")).thenReturn(Optional.of(requestLog));

        RouteDecisionLogEntity routeDecision = new RouteDecisionLogEntity();
        routeDecision.setRequestId("req-1");
        routeDecision.setDistributedKeyId(7L);
        routeDecision.setSelectedProviderType(ProviderType.OPENAI_DIRECT);
        routeDecision.setModelGroup("gpt-4o");
        routeDecision.setPrefixHash("prefix");
        routeDecision.setSelectionSource("PREFIX_AFFINITY");
        when(routeDecisionLogRepository.findTopByRequestIdOrderByCreatedAtDesc("req-1"))
                .thenReturn(Optional.of(routeDecision));

        CacheHitLogEntity cacheHit = new CacheHitLogEntity();
        cacheHit.setRequestId("req-1");
        cacheHit.setDistributedKeyId(7L);
        cacheHit.setProviderType(ProviderType.OPENAI_DIRECT);
        cacheHit.setModelGroup("gpt-4o");
        cacheHit.setPrefixHash("prefix");
        when(cacheHitLogRepository.findAllByRequestIdOrderByCreatedAtDesc("req-1"))
                .thenReturn(List.of(cacheHit));

        UpstreamCacheReferenceEntity upstreamReference = new UpstreamCacheReferenceEntity();
        upstreamReference.setDistributedKeyId(7L);
        upstreamReference.setProviderType(ProviderType.OPENAI_DIRECT);
        upstreamReference.setModelGroup("gpt-4o");
        upstreamReference.setPrefixHash("prefix");
        upstreamReference.setStatus("ACTIVE");
        when(upstreamCacheReferenceRepository.findByDistributedKeyIdAndProviderTypeAndModelGroupAndPrefixHash(
                7L,
                ProviderType.OPENAI_DIRECT,
                "gpt-4o",
                "prefix"
        )).thenReturn(Optional.of(upstreamReference));

        when(asyncResourceAdminService.findAsyncResourceSummary("batch_1"))
                .thenReturn(Optional.of(new AsyncResourceSummaryResponse(
                        "batch_1",
                        GatewayAsyncResourceType.BATCH,
                        "in_progress",
                        "IN_PROGRESS",
                        false,
                        false,
                        "gateway-object-lineage",
                        "upstream-1",
                        1,
                        null,
                        null,
                        null,
                        Instant.parse("2026-04-07T08:00:00Z"),
                        Instant.parse("2026-04-07T08:01:00Z")
                )));
        when(asyncResourceAdminService.findAsyncResourceDetail("batch_1"))
                .thenReturn(Optional.of(new AsyncResourceDetailResponse(
                        null,
                        List.of(),
                        null,
                        List.of(),
                        JsonNodeFactory.instance.objectNode(),
                        JsonNodeFactory.instance.objectNode(),
                        JsonNodeFactory.instance.objectNode()
                )));

        ObservabilityQueryService service = new ObservabilityQueryService(
                routeDecisionLogRepository,
                cacheHitLogRepository,
                requestLogRepository,
                upstreamCacheReferenceRepository,
                usageRecordRepository,
                asyncResourceAdminService
        );

        var trace = service.trace("req-1");
        assertEquals("req-1", trace.requestLog().requestId());
        assertNotNull(trace.routeDecision());
        assertEquals(1, trace.cacheHits().size());
        assertEquals(1, trace.upstreamCacheReferences().size());
        assertNotNull(trace.asyncResourceSummary());
        assertNotNull(trace.asyncResourceDetail());
    }

    private RequestLogEntity requestLog(String requestId, String gatewayResourceKey) {
        RequestLogEntity entity = new RequestLogEntity();
        entity.setRequestId(requestId);
        entity.setDistributedKeyId(7L);
        entity.setProtocol("openai");
        entity.setRequestPath("/v1/batches/" + gatewayResourceKey);
        entity.setResourceType("batch");
        entity.setOperation("batch_get");
        entity.setRequestedModel("gpt-4o");
        entity.setPublicModel("gpt-4o");
        entity.setResolvedModelKey("gpt-4o");
        entity.setProviderType(ProviderType.OPENAI_DIRECT);
        entity.setSupportStatus("NATIVE");
        entity.setDegradationLevel("NATIVE");
        entity.setObjectMode("gateway-object-lineage");
        entity.setGatewayResourceKey(gatewayResourceKey);
        entity.setResponseKind("object");
        entity.setResponseObjectType("batch");
        entity.setResponseObjectId(gatewayResourceKey);
        entity.setResponseStatus("in_progress");
        entity.setCanonicalEventCount(1);
        entity.setStatus(GatewayRequestStatus.COMPLETED);
        return entity;
    }
}
