package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AsyncResourceDetailResponse;
import com.prodigalgal.xaigateway.admin.api.AsyncResourceSummaryResponse;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestStatus;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceType;
import com.prodigalgal.xaigateway.infra.persistence.entity.CacheHitLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RequestLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RequestTraceDetailEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RouteDecisionLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCacheReferenceEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UsageRecordEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.CacheHitLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RequestLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RequestTraceDetailRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RouteDecisionLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCacheReferenceRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UsageRecordRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.node.JsonNodeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObservabilityQueryServiceTests {

    @Test
    void shouldCombineDistributedKeyAndProviderFiltersForCacheHits() {
        RouteDecisionLogRepository routeDecisionLogRepository = Mockito.mock(RouteDecisionLogRepository.class);
        CacheHitLogRepository cacheHitLogRepository = Mockito.mock(CacheHitLogRepository.class);
        RequestLogRepository requestLogRepository = Mockito.mock(RequestLogRepository.class);
        RequestTraceDetailRepository requestTraceDetailRepository = Mockito.mock(RequestTraceDetailRepository.class);
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
        entity.setGatewayResourceKey("upload_1");
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
        assertEquals("upload_1", logs.get(0).gatewayResourceKey());
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

        RequestLogEntity entity = requestLog("req-1", "upload_1");
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

        when(requestLogRepository.findTop100ByGatewayResourceKeyOrderByCreatedAtDesc("upload_1"))
                .thenReturn(List.of(requestLog("req-1", "upload_1")));
        when(requestLogRepository.findByRequestId("req-1"))
                .thenReturn(Optional.of(requestLog("req-1", "upload_1")));

        ObservabilityQueryService service = new ObservabilityQueryService(
                routeDecisionLogRepository,
                cacheHitLogRepository,
                requestLogRepository,
                upstreamCacheReferenceRepository,
                usageRecordRepository
        );

        var logs = service.listRequestLogs(null, null, null, null, null, "upload_1", null);
        assertEquals(1, logs.size());
        assertEquals("upload_1", logs.get(0).gatewayResourceKey());
        verify(requestLogRepository).findTop100ByGatewayResourceKeyOrderByCreatedAtDesc("upload_1");
    }

    @Test
    void shouldResolveRequestLogsFromUpstreamObjectId() {
        RouteDecisionLogRepository routeDecisionLogRepository = Mockito.mock(RouteDecisionLogRepository.class);
        CacheHitLogRepository cacheHitLogRepository = Mockito.mock(CacheHitLogRepository.class);
        RequestLogRepository requestLogRepository = Mockito.mock(RequestLogRepository.class);
        RequestTraceDetailRepository requestTraceDetailRepository = Mockito.mock(RequestTraceDetailRepository.class);
        UpstreamCacheReferenceRepository upstreamCacheReferenceRepository = Mockito.mock(UpstreamCacheReferenceRepository.class);
        UsageRecordRepository usageRecordRepository = Mockito.mock(UsageRecordRepository.class);
        AsyncResourceAdminService asyncResourceAdminService = Mockito.mock(AsyncResourceAdminService.class);

        when(asyncResourceAdminService.findAsyncResourcesByUpstreamObjectId("upstream-1"))
                .thenReturn(List.of(new AsyncResourceSummaryResponse(
                        "upload_1",
                        GatewayAsyncResourceType.UPLOAD,
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
        when(requestLogRepository.findTop100ByGatewayResourceKeyOrderByCreatedAtDesc("upload_1"))
                .thenReturn(List.of(requestLog("req-1", "upload_1")));
        when(requestLogRepository.findByRequestId("req-1"))
                .thenReturn(Optional.of(requestLog("req-1", "upload_1")));

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
        RequestTraceDetailRepository requestTraceDetailRepository = Mockito.mock(RequestTraceDetailRepository.class);
        UpstreamCacheReferenceRepository upstreamCacheReferenceRepository = Mockito.mock(UpstreamCacheReferenceRepository.class);
        UsageRecordRepository usageRecordRepository = Mockito.mock(UsageRecordRepository.class);
        AsyncResourceAdminService asyncResourceAdminService = Mockito.mock(AsyncResourceAdminService.class);

        RequestLogEntity requestLog = requestLog("req-1", "upload_1");
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

        when(asyncResourceAdminService.findAsyncResourceSummary("upload_1"))
                .thenReturn(Optional.of(new AsyncResourceSummaryResponse(
                        "upload_1",
                        GatewayAsyncResourceType.UPLOAD,
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
        when(asyncResourceAdminService.findAsyncResourceDetail("upload_1"))
                .thenReturn(Optional.of(new AsyncResourceDetailResponse(
                        null,
                        List.of(),
                        null,
                        List.of(),
                        JsonNodeFactory.instance.objectNode(),
                        JsonNodeFactory.instance.objectNode(),
                        JsonNodeFactory.instance.objectNode()
                )));

        RequestTraceDetailEntity traceDetail = new RequestTraceDetailEntity();
        ReflectionTestUtils.setField(traceDetail, "id", 11L);
        ReflectionTestUtils.setField(traceDetail, "createdAt", Instant.parse("2026-04-07T08:00:02Z"));
        traceDetail.setRequestId("req-1");
        traceDetail.setStage("UPSTREAM_REQUEST");
        traceDetail.setDirection("UPSTREAM");
        traceDetail.setContentKind("JSON");
        traceDetail.setPayloadJson("{\"model\":\"gpt-4o\"}");
        traceDetail.setMetadataJson("{\"providerType\":\"OPENAI_DIRECT\"}");
        traceDetail.setPayloadHash("payload-hash");
        traceDetail.setMetadataHash("metadata-hash");
        traceDetail.setOriginalLength(200);
        traceDetail.setStoredLength(120);
        traceDetail.setMetadataOriginalLength(5000);
        traceDetail.setMetadataStoredLength(4000);
        traceDetail.setTruncated(true);
        traceDetail.setMetadataTruncated(true);
        traceDetail.setRedacted(true);
        traceDetail.setMetadataRedacted(true);
        traceDetail.setExpiresAt(Instant.parse("2026-04-14T08:00:02Z"));
        when(requestTraceDetailRepository.findAllByRequestIdOrderByCreatedAtAscIdAsc("req-1"))
                .thenReturn(List.of(traceDetail));

        ObservabilityQueryService service = new ObservabilityQueryService(
                routeDecisionLogRepository,
                cacheHitLogRepository,
                requestLogRepository,
                requestTraceDetailRepository,
                null,
                upstreamCacheReferenceRepository,
                usageRecordRepository,
                asyncResourceAdminService
        );

        var trace = service.trace("req-1");
        assertEquals("req-1", trace.requestLog().requestId());
        assertNotNull(trace.routeDecision());
        assertEquals(1, trace.cacheHits().size());
        assertEquals(1, trace.upstreamCacheReferences().size());
        assertEquals(1, trace.traceDetails().size());
        assertEquals("metadata-hash", trace.traceDetails().getFirst().metadataHash());
        assertEquals(5000, trace.traceDetails().getFirst().metadataOriginalLength());
        assertEquals(4000, trace.traceDetails().getFirst().metadataStoredLength());
        assertTrue(trace.traceDetails().getFirst().metadataTruncated());
        assertTrue(trace.traceDetails().getFirst().metadataRedacted());
        assertEquals(Instant.parse("2026-04-14T08:00:02Z"), trace.traceDetails().getFirst().expiresAt());
        assertNotNull(trace.asyncResourceSummary());
        assertNotNull(trace.asyncResourceDetail());
    }

    @Test
    void shouldBuildCodexObservabilityProjectionWithUsageCacheAndRedaction() {
        RouteDecisionLogRepository routeDecisionLogRepository = Mockito.mock(RouteDecisionLogRepository.class);
        CacheHitLogRepository cacheHitLogRepository = Mockito.mock(CacheHitLogRepository.class);
        RequestLogRepository requestLogRepository = Mockito.mock(RequestLogRepository.class);
        UpstreamCacheReferenceRepository upstreamCacheReferenceRepository = Mockito.mock(UpstreamCacheReferenceRepository.class);
        UsageRecordRepository usageRecordRepository = Mockito.mock(UsageRecordRepository.class);

        RequestLogEntity requestLog = new RequestLogEntity();
        requestLog.setRequestId("req-codex-1");
        requestLog.setDistributedKeyId(7L);
        requestLog.setDistributedKeyPrefix("xag_codex");
        requestLog.setClientFamily("CODEX");
        requestLog.setClientInstance("codex-cli-default");
        requestLog.setSessionAffinitySource("client-instance");
        requestLog.setSessionAffinityKey("session-alpha");
        requestLog.setProtocol("responses");
        requestLog.setRequestPath("/v1/responses");
        requestLog.setRequestedModel("gpt-5.4@low");
        requestLog.setPublicModel("gpt-5.4@low");
        requestLog.setResolvedModelKey("gpt-5.4@low");
        requestLog.setModelGroup("gpt-5.4");
        requestLog.setProviderType(ProviderType.OPENAI_DIRECT);
        requestLog.setCredentialId(17L);
        requestLog.setSupportStatus("NATIVE");
        requestLog.setStatus(GatewayRequestStatus.FAILED);
        requestLog.setErrorCode("UPSTREAM_429");
        requestLog.setErrorMessage("Bearer abcdefghijklmnopqrstuvwxyz hit rate limit");
        when(requestLogRepository.search(Mockito.isNull(), Mockito.isNull(), ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(requestLog));

        RouteDecisionLogEntity routeDecision = new RouteDecisionLogEntity();
        routeDecision.setRequestId("req-codex-1");
        routeDecision.setDistributedKeyId(7L);
        routeDecision.setRequestedModel("gpt-5.4@low");
        routeDecision.setPublicModel("gpt-5.4@low");
        routeDecision.setResolvedModelKey("gpt-5.4@low");
        routeDecision.setProtocol("responses");
        routeDecision.setModelGroup("gpt-5.4");
        routeDecision.setSelectedProviderType(ProviderType.OPENAI_DIRECT);
        routeDecision.setSelectedCredentialId(17L);
        routeDecision.setSupportStatus("NATIVE");
        routeDecision.setCandidateCount(3);
        routeDecision.setCandidateSummaryJson("{\"x_ai_gateway_filter\":{\"action\":\"REDACT\"}}");
        when(routeDecisionLogRepository.findTopByRequestIdOrderByCreatedAtDesc("req-codex-1"))
                .thenReturn(Optional.of(routeDecision));

        CacheHitLogEntity cacheHit = new CacheHitLogEntity();
        cacheHit.setRequestId("req-codex-1");
        cacheHit.setCacheHitTokens(20);
        cacheHit.setCacheWriteTokens(5);
        cacheHit.setSavedInputTokens(40);
        when(cacheHitLogRepository.findAllByRequestIdOrderByCreatedAtDesc("req-codex-1"))
                .thenReturn(List.of(cacheHit));

        UsageRecordEntity usageRecord = new UsageRecordEntity();
        usageRecord.setRequestId("req-codex-1");
        usageRecord.setPromptTokens(120);
        usageRecord.setCompletionTokens(30);
        usageRecord.setReasoningTokens(10);
        usageRecord.setTotalTokens(160);
        when(usageRecordRepository.findAllByRequestIdIn(ArgumentMatchers.any()))
                .thenReturn(List.of(usageRecord));

        ObservabilityQueryService service = new ObservabilityQueryService(
                routeDecisionLogRepository,
                cacheHitLogRepository,
                requestLogRepository,
                upstreamCacheReferenceRepository,
                usageRecordRepository
        );

        var rows = service.listCodexRequests(
                null,
                null,
                null,
                "codex-cli",
                "session-alpha",
                "gpt-5",
                "FAILED",
                null,
                null
        );

        assertEquals(1, rows.size());
        assertEquals("req-codex-1", rows.get(0).requestId());
        assertEquals(120, rows.get(0).usageInputTokens());
        assertEquals(160, rows.get(0).usageTotalTokens());
        assertEquals(20, rows.get(0).cacheHitTokens());
        assertTrue(rows.get(0).filterSummaryJson().contains("route_candidate_summary"));
        assertTrue(rows.get(0).errorSummary().contains("Bearer ***"));
    }

    @Test
    void shouldAggregateHealthMetricsByTotalProviderAndCredential() {
        RouteDecisionLogRepository routeDecisionLogRepository = Mockito.mock(RouteDecisionLogRepository.class);
        CacheHitLogRepository cacheHitLogRepository = Mockito.mock(CacheHitLogRepository.class);
        RequestLogRepository requestLogRepository = Mockito.mock(RequestLogRepository.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        UpstreamCacheReferenceRepository upstreamCacheReferenceRepository = Mockito.mock(UpstreamCacheReferenceRepository.class);
        UsageRecordRepository usageRecordRepository = Mockito.mock(UsageRecordRepository.class);

        Instant from = Instant.parse("2026-05-25T00:00:00Z");
        Instant to = Instant.parse("2026-05-25T06:00:00Z");
        RequestLogEntity success = requestLog("req-success", "upload_1");
        success.setCredentialId(101L);
        success.setStartedAt(Instant.parse("2026-05-25T01:00:00Z"));
        success.setCompletedAt(Instant.parse("2026-05-25T01:00:01Z"));
        success.setDurationMs(100L);
        success.setStatus(GatewayRequestStatus.COMPLETED);
        RequestLogEntity failed = requestLog("req-failed", "upload_1");
        failed.setCredentialId(101L);
        failed.setStartedAt(Instant.parse("2026-05-25T02:00:00Z"));
        failed.setCompletedAt(Instant.parse("2026-05-25T02:00:02Z"));
        failed.setDurationMs(300L);
        failed.setStatus(GatewayRequestStatus.FAILED);
        RequestLogEntity canceled = requestLog("req-canceled", "upload_1");
        canceled.setCredentialId(102L);
        canceled.setProviderType(ProviderType.GEMINI_DIRECT);
        canceled.setStartedAt(Instant.parse("2026-05-25T03:00:00Z"));
        canceled.setCompletedAt(Instant.parse("2026-05-25T03:00:03Z"));
        canceled.setDurationMs(200L);
        canceled.setStatus(GatewayRequestStatus.CANCELED);

        when(requestLogRepository.searchHealthWithinWindow(ProviderType.OPENAI_DIRECT, null, from, to))
                .thenReturn(List.of(success, failed));

        UpstreamCredentialEntity credential = new UpstreamCredentialEntity();
        ReflectionTestUtils.setField(credential, "id", 101L);
        credential.setCredentialName("openai-main");
        credential.setProviderType(ProviderType.OPENAI_DIRECT);
        credential.setApiKeyFingerprint("abcdef1234567890");
        when(upstreamCredentialRepository.findAllByIdInAndDeletedFalse(List.of(101L)))
                .thenReturn(List.of(credential));

        ObservabilityQueryService service = new ObservabilityQueryService(
                routeDecisionLogRepository,
                cacheHitLogRepository,
                requestLogRepository,
                null,
                upstreamCredentialRepository,
                upstreamCacheReferenceRepository,
                usageRecordRepository,
                null
        );

        var health = service.health(ProviderType.OPENAI_DIRECT, null, from, to);

        assertEquals(from, health.sampledFrom());
        assertEquals(to, health.sampledTo());
        assertEquals(2, health.total().totalRequests());
        assertEquals(1, health.total().successfulRequests());
        assertEquals(1, health.total().failedRequests());
        assertEquals(0.5D, health.total().successRate());
        assertEquals(0.5D, health.total().availabilityRate());
        assertEquals(200D, health.total().avgDurationMs());
        assertEquals(1, health.providers().size());
        assertEquals(ProviderType.OPENAI_DIRECT, health.providers().get(0).providerType());
        assertEquals(1, health.credentials().size());
        assertEquals(101L, health.credentials().get(0).credentialId());
        assertEquals("openai-main", health.credentials().get(0).credentialLabel());
        assertEquals("abcdef123456", health.credentials().get(0).credentialPrefix());
        verify(requestLogRepository).searchHealthWithinWindow(ProviderType.OPENAI_DIRECT, null, from, to);
    }

    private RequestLogEntity requestLog(String requestId, String gatewayResourceKey) {
        RequestLogEntity entity = new RequestLogEntity();
        entity.setRequestId(requestId);
        entity.setDistributedKeyId(7L);
        entity.setProtocol("openai");
        entity.setRequestPath("/v1/uploads/" + gatewayResourceKey);
        entity.setResourceType("upload");
        entity.setOperation("upload_get");
        entity.setRequestedModel("gpt-4o");
        entity.setPublicModel("gpt-4o");
        entity.setResolvedModelKey("gpt-4o");
        entity.setProviderType(ProviderType.OPENAI_DIRECT);
        entity.setSupportStatus("NATIVE");
        entity.setDegradationLevel("NATIVE");
        entity.setObjectMode("gateway-object-lineage");
        entity.setGatewayResourceKey(gatewayResourceKey);
        entity.setResponseKind("object");
        entity.setResponseObjectType("upload");
        entity.setResponseObjectId(gatewayResourceKey);
        entity.setResponseStatus("in_progress");
        entity.setCanonicalEventCount(1);
        entity.setStatus(GatewayRequestStatus.COMPLETED);
        return entity;
    }
}
