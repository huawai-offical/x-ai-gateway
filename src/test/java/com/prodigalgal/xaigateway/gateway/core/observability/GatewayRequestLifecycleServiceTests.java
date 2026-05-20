package com.prodigalgal.xaigateway.gateway.core.observability;

import com.prodigalgal.xaigateway.admin.application.CostRoutingService;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceResponse;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.SupportStatus;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageCompleteness;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageSource;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.infra.persistence.entity.RequestLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UsageRecordEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.RequestLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UsageRecordRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GatewayRequestLifecycleServiceTests {

    @Test
    void shouldPersistCanonicalSummaryOnComplete() {
        RequestLogRepository requestLogRepository = Mockito.mock(RequestLogRepository.class);
        UsageRecordRepository usageRecordRepository = Mockito.mock(UsageRecordRepository.class);
        GatewayAuditLogService gatewayAuditLogService = Mockito.mock(GatewayAuditLogService.class);
        AtomicReference<RequestLogEntity> stored = new AtomicReference<>();
        Mockito.when(requestLogRepository.save(Mockito.any(RequestLogEntity.class)))
                .thenAnswer(invocation -> {
                    RequestLogEntity entity = invocation.getArgument(0);
                    stored.set(entity);
                    return entity;
                });
        Mockito.when(requestLogRepository.findByRequestId("req-1"))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));

        GatewayRequestLifecycleService service = new GatewayRequestLifecycleService(
                requestLogRepository,
                usageRecordRepository,
                gatewayAuditLogService,
                new SimpleMeterRegistry(),
                new tools.jackson.databind.ObjectMapper()
        );

        RouteSelectionResult selectionResult = selectionResult();
        CanonicalResourceRequest request = new CanonicalResourceRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.OPENAI,
                "GET",
                "/v1/files/file_123/content",
                "/v1/files/{fileId}/content",
                Map.of("fileId", "file_123"),
                "model-a",
                TranslationResourceType.FILE,
                TranslationOperation.FILE_CONTENT_GET,
                null,
                Map.of(),
                List.of(),
                true,
                false
        );
        CanonicalExecutionPlan plan = new CanonicalExecutionPlan(
                true,
                CanonicalIngressProtocol.OPENAI,
                "/v1/files/file_123/content",
                "/v1/files/{fileId}/content",
                "files",
                "model-a",
                "model-a",
                "model-a",
                TranslationResourceType.FILE,
                TranslationOperation.FILE_CONTENT_GET,
                ExecutionKind.NATIVE,
                ExecutionBackend.NATIVE,
                SupportStatus.NATIVE,
                "resource-orchestration",
                List.of(ExecutionBackend.NATIVE),
                "test",
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                List.of(),
                List.of(),
                Map.of(),
                List.of(),
                List.of()
        );
        CanonicalResourceResponse canonicalResponse = new CanonicalResourceResponse(
                TranslationResourceType.FILE,
                TranslationOperation.FILE_CONTENT_GET,
                "binary",
                "file.content",
                "file_123",
                "completed",
                List.of(),
                List.of(),
                null,
                42,
                Map.of("contentType", "application/pdf")
        );

        service.startRequest("req-1", selectionResult, request, plan, false, Instant.now());
        service.completeRequest("req-1", selectionResult, request, plan, false, GatewayUsageView.empty(), canonicalResponse, Instant.now());

        RequestLogEntity entity = stored.get();
        assertEquals("NATIVE", entity.getSupportStatus());
        assertEquals("NATIVE", entity.getDegradationLevel());
        assertEquals("binary", entity.getResponseKind());
        assertEquals("file.content", entity.getResponseObjectType());
        assertEquals("file_123", entity.getResponseObjectId());
        assertEquals("completed", entity.getResponseStatus());
        assertEquals(0, entity.getCanonicalEventCount());
    }

    @Test
    void shouldPersistGatewayResourceKeyForAsyncResourceRequests() {
        RequestLogRepository requestLogRepository = Mockito.mock(RequestLogRepository.class);
        UsageRecordRepository usageRecordRepository = Mockito.mock(UsageRecordRepository.class);
        GatewayAuditLogService gatewayAuditLogService = Mockito.mock(GatewayAuditLogService.class);
        AtomicReference<RequestLogEntity> stored = new AtomicReference<>();
        Mockito.when(requestLogRepository.save(Mockito.any(RequestLogEntity.class)))
                .thenAnswer(invocation -> {
                    RequestLogEntity entity = invocation.getArgument(0);
                    stored.set(entity);
                    return entity;
                });
        Mockito.when(requestLogRepository.findByRequestId("req-async"))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));

        GatewayRequestLifecycleService service = new GatewayRequestLifecycleService(
                requestLogRepository,
                usageRecordRepository,
                gatewayAuditLogService,
                new SimpleMeterRegistry(),
                new tools.jackson.databind.ObjectMapper()
        );

        RouteSelectionResult selectionResult = selectionResult();
        CanonicalResourceRequest request = new CanonicalResourceRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.OPENAI,
                "GET",
                "/v1/uploads/upload_1",
                "/v1/uploads/{uploadId}",
                Map.of("uploadId", "upload_1"),
                "file-support",
                TranslationResourceType.UPLOAD,
                TranslationOperation.UPLOAD_GET,
                null,
                Map.of(),
                List.of(),
                false,
                false
        );
        CanonicalExecutionPlan plan = new CanonicalExecutionPlan(
                true,
                CanonicalIngressProtocol.OPENAI,
                "/v1/uploads/upload_1",
                "/v1/uploads/{uploadId}",
                "uploads",
                "file-support",
                "file-support",
                "file-support",
                TranslationResourceType.UPLOAD,
                TranslationOperation.UPLOAD_GET,
                ExecutionKind.NATIVE,
                ExecutionBackend.ORCHESTRATION,
                SupportStatus.NATIVE,
                "gateway-object-lineage",
                List.of(ExecutionBackend.ORCHESTRATION),
                "test",
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                List.of(),
                List.of(),
                Map.of(),
                List.of(),
                List.of()
        );
        CanonicalResourceResponse canonicalResponse = new CanonicalResourceResponse(
                TranslationResourceType.UPLOAD,
                TranslationOperation.UPLOAD_GET,
                "object",
                "upload",
                "upload_1",
                "in_progress",
                List.of(),
                List.of(),
                null,
                null,
                Map.of()
        );

        service.startRequest("req-async", selectionResult, request, plan, false, Instant.now());
        service.completeRequest("req-async", selectionResult, request, plan, false, GatewayUsageView.empty(), canonicalResponse, Instant.now());

        assertEquals("upload_1", stored.get().getGatewayResourceKey());
    }

    @Test
    void shouldPersistOpenAiFileDeleteCanonicalSummary() {
        RequestLogRepository requestLogRepository = Mockito.mock(RequestLogRepository.class);
        UsageRecordRepository usageRecordRepository = Mockito.mock(UsageRecordRepository.class);
        GatewayAuditLogService gatewayAuditLogService = Mockito.mock(GatewayAuditLogService.class);
        AtomicReference<RequestLogEntity> stored = new AtomicReference<>();
        Mockito.when(requestLogRepository.save(Mockito.any(RequestLogEntity.class)))
                .thenAnswer(invocation -> {
                    RequestLogEntity entity = invocation.getArgument(0);
                    stored.set(entity);
                    return entity;
                });
        Mockito.when(requestLogRepository.findByRequestId("req-file-delete"))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));

        GatewayRequestLifecycleService service = new GatewayRequestLifecycleService(
                requestLogRepository,
                usageRecordRepository,
                gatewayAuditLogService,
                new SimpleMeterRegistry(),
                new tools.jackson.databind.ObjectMapper()
        );

        RouteSelectionResult selectionResult = selectionResult();
        CanonicalResourceRequest request = new CanonicalResourceRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.OPENAI,
                "DELETE",
                "/v1/files/file_123",
                "/v1/files/{fileId}",
                Map.of("fileId", "file_123"),
                "resource-orchestration",
                TranslationResourceType.FILE,
                TranslationOperation.FILE_DELETE,
                null,
                Map.of(),
                List.of(),
                false,
                false
        );
        CanonicalExecutionPlan plan = new CanonicalExecutionPlan(
                true,
                CanonicalIngressProtocol.OPENAI,
                "/v1/files/file_123",
                "/v1/files/{fileId}",
                "files",
                "resource-orchestration",
                "resource-orchestration",
                "resource-orchestration",
                TranslationResourceType.FILE,
                TranslationOperation.FILE_DELETE,
                ExecutionKind.NATIVE,
                ExecutionBackend.ORCHESTRATION,
                SupportStatus.ORCHESTRATION,
                "upstream_object_with_local_lineage",
                List.of(ExecutionBackend.ORCHESTRATION),
                "test",
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                List.of(),
                List.of(),
                Map.of(),
                List.of(),
                List.of()
        );
        CanonicalResourceResponse canonicalResponse = new CanonicalResourceResponse(
                TranslationResourceType.FILE,
                TranslationOperation.FILE_DELETE,
                "object",
                "file.deleted",
                "file_123",
                "deleted",
                List.of(),
                List.of(),
                null,
                null,
                Map.of()
        );

        service.startRequest("req-file-delete", selectionResult, request, plan, false, Instant.now());
        service.completeRequest("req-file-delete", selectionResult, request, plan, false, GatewayUsageView.empty(), canonicalResponse, Instant.now());

        RequestLogEntity entity = stored.get();
        assertEquals("ORCHESTRATION", entity.getSupportStatus());
        assertEquals("NATIVE", entity.getDegradationLevel());
        assertEquals("object", entity.getResponseKind());
        assertEquals("file.deleted", entity.getResponseObjectType());
        assertEquals("file_123", entity.getResponseObjectId());
        assertEquals("deleted", entity.getResponseStatus());
    }

    @Test
    void shouldPersistNoRouteResourceSummaryOnComplete() {
        RequestLogRepository requestLogRepository = Mockito.mock(RequestLogRepository.class);
        UsageRecordRepository usageRecordRepository = Mockito.mock(UsageRecordRepository.class);
        GatewayAuditLogService gatewayAuditLogService = Mockito.mock(GatewayAuditLogService.class);
        AtomicReference<RequestLogEntity> stored = new AtomicReference<>();
        Mockito.when(requestLogRepository.save(Mockito.any(RequestLogEntity.class)))
                .thenAnswer(invocation -> {
                    RequestLogEntity entity = invocation.getArgument(0);
                    stored.set(entity);
                    return entity;
                });
        Mockito.when(requestLogRepository.findByRequestId("req-upload-1"))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));

        GatewayRequestLifecycleService service = new GatewayRequestLifecycleService(
                requestLogRepository,
                usageRecordRepository,
                gatewayAuditLogService,
                new SimpleMeterRegistry(),
                new tools.jackson.databind.ObjectMapper()
        );

        CanonicalResourceRequest request = new CanonicalResourceRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.OPENAI,
                "POST",
                "/v1/uploads",
                "/v1/uploads",
                Map.of(),
                "resource-orchestration",
                TranslationResourceType.UPLOAD,
                TranslationOperation.UPLOAD_CREATE,
                null,
                Map.of(),
                List.of(),
                false,
                false
        );
        CanonicalExecutionPlan plan = new CanonicalExecutionPlan(
                true,
                CanonicalIngressProtocol.OPENAI,
                "/v1/uploads",
                "/v1/uploads",
                "uploads",
                "resource-orchestration",
                "resource-orchestration",
                "resource-orchestration",
                TranslationResourceType.UPLOAD,
                TranslationOperation.UPLOAD_CREATE,
                ExecutionKind.NATIVE,
                ExecutionBackend.ORCHESTRATION,
                SupportStatus.ORCHESTRATION,
                "gateway_upload_object",
                List.of(ExecutionBackend.ORCHESTRATION),
                "gateway_local_orchestration",
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                List.of(),
                List.of(),
                Map.of(),
                List.of(),
                List.of()
        );
        CanonicalResourceResponse canonicalResponse = new CanonicalResourceResponse(
                TranslationResourceType.UPLOAD,
                TranslationOperation.UPLOAD_CREATE,
                "object",
                "upload",
                "upload_1",
                "created",
                List.of(),
                List.of(),
                null,
                null,
                Map.of()
        );
        Instant startedAt = Instant.now();

        service.startRequest("req-upload-1", 1L, "sk-gw-test", "openai", request, plan, false, startedAt);
        service.completeRequest(
                "req-upload-1",
                1L,
                "sk-gw-test",
                "openai",
                request,
                plan,
                false,
                GatewayUsageView.empty(),
                canonicalResponse,
                startedAt
        );

        RequestLogEntity entity = stored.get();
        assertEquals("req-upload-1", entity.getRequestId());
        assertEquals("openai", entity.getProtocol());
        assertEquals("ORCHESTRATION", entity.getSupportStatus());
        assertEquals("NATIVE", entity.getDegradationLevel());
        assertEquals("gateway_upload_object", entity.getObjectMode());
        assertEquals("upload_1", entity.getGatewayResourceKey());
        assertEquals("upload", entity.getResponseObjectType());
        assertEquals("created", entity.getResponseStatus());
    }

    @Test
    void shouldPersistNoRouteFailureDetails() {
        RequestLogRepository requestLogRepository = Mockito.mock(RequestLogRepository.class);
        UsageRecordRepository usageRecordRepository = Mockito.mock(UsageRecordRepository.class);
        GatewayAuditLogService gatewayAuditLogService = Mockito.mock(GatewayAuditLogService.class);
        AtomicReference<RequestLogEntity> stored = new AtomicReference<>();
        Mockito.when(requestLogRepository.save(Mockito.any(RequestLogEntity.class)))
                .thenAnswer(invocation -> {
                    RequestLogEntity entity = invocation.getArgument(0);
                    stored.set(entity);
                    return entity;
                });
        Mockito.when(requestLogRepository.findByRequestId("req-realtime-1"))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));

        GatewayRequestLifecycleService service = new GatewayRequestLifecycleService(
                requestLogRepository,
                usageRecordRepository,
                gatewayAuditLogService,
                new SimpleMeterRegistry(),
                new tools.jackson.databind.ObjectMapper()
        );

        CanonicalResourceRequest request = new CanonicalResourceRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.OPENAI,
                "POST",
                "/v1/realtime/client_secrets",
                "/v1/realtime/client_secrets",
                Map.of(),
                "resource-orchestration",
                TranslationResourceType.REALTIME,
                TranslationOperation.REALTIME_CLIENT_SECRET_CREATE,
                null,
                Map.of(),
                List.of(),
                false,
                false
        );
        CanonicalExecutionPlan plan = new CanonicalExecutionPlan(
                false,
                CanonicalIngressProtocol.OPENAI,
                "/v1/realtime/client_secrets",
                "/v1/realtime/client_secrets",
                "realtime",
                "resource-orchestration",
                "resource-orchestration",
                "resource-orchestration",
                TranslationResourceType.REALTIME,
                TranslationOperation.REALTIME_CLIENT_SECRET_CREATE,
                ExecutionKind.NATIVE,
                ExecutionBackend.ORCHESTRATION,
                SupportStatus.BLOCKED,
                "gateway-object-lineage",
                List.of(ExecutionBackend.ORCHESTRATION),
                "blocked_by_policy",
                InteropCapabilityLevel.UNSUPPORTED,
                InteropCapabilityLevel.UNSUPPORTED,
                InteropCapabilityLevel.UNSUPPORTED,
                InteropCapabilityLevel.UNSUPPORTED,
                List.of("realtime blocked"),
                List.of(),
                Map.of(),
                List.of(),
                List.of("realtime blocked")
        );
        Instant startedAt = Instant.now();

        service.startRequest("req-realtime-1", 1L, "sk-gw-test", "openai", request, plan, false, startedAt);
        service.failRequest(
                "req-realtime-1",
                1L,
                "sk-gw-test",
                "openai",
                request,
                plan,
                false,
                new IllegalStateException("realtime blocked"),
                startedAt
        );

        RequestLogEntity entity = stored.get();
        assertEquals(GatewayRequestStatus.FAILED, entity.getStatus());
        assertEquals("IllegalStateException", entity.getErrorCode());
        assertEquals("realtime blocked", entity.getErrorMessage());
        assertEquals("BLOCKED", entity.getSupportStatus());
        assertEquals("UNSUPPORTED", entity.getDegradationLevel());
    }

    @Test
    void shouldFallbackToSynchronousPersistenceWhenAsyncEnqueueFails() {
        RequestLogRepository requestLogRepository = Mockito.mock(RequestLogRepository.class);
        UsageRecordRepository usageRecordRepository = Mockito.mock(UsageRecordRepository.class);
        GatewayAuditLogService gatewayAuditLogService = Mockito.mock(GatewayAuditLogService.class);
        GatewayObservabilityAsyncPersistenceService asyncPersistenceService = Mockito.mock(GatewayObservabilityAsyncPersistenceService.class);
        AtomicReference<RequestLogEntity> storedRequest = new AtomicReference<>();
        AtomicReference<UsageRecordEntity> storedUsage = new AtomicReference<>();

        Mockito.when(asyncPersistenceService.enqueueRequestLogStart(Mockito.any())).thenReturn(false);
        Mockito.when(asyncPersistenceService.enqueueRequestLogFinish(Mockito.any())).thenReturn(false);
        Mockito.when(asyncPersistenceService.enqueueUsageRecordUpsert(Mockito.any())).thenReturn(false);
        Mockito.when(requestLogRepository.save(Mockito.any(RequestLogEntity.class)))
                .thenAnswer(invocation -> {
                    RequestLogEntity entity = invocation.getArgument(0);
                    storedRequest.set(entity);
                    return entity;
                });
        Mockito.when(requestLogRepository.findByRequestId("req-fallback"))
                .thenAnswer(invocation -> Optional.ofNullable(storedRequest.get()));
        Mockito.when(usageRecordRepository.save(Mockito.any(UsageRecordEntity.class)))
                .thenAnswer(invocation -> {
                    UsageRecordEntity entity = invocation.getArgument(0);
                    storedUsage.set(entity);
                    return entity;
                });
        Mockito.when(usageRecordRepository.findByRequestId("req-fallback"))
                .thenReturn(Optional.empty());

        GatewayRequestLifecycleService service = new GatewayRequestLifecycleService(
                requestLogRepository,
                usageRecordRepository,
                gatewayAuditLogService,
                new SimpleMeterRegistry(),
                new tools.jackson.databind.ObjectMapper(),
                asyncPersistenceService
        );

        CanonicalRequest request = new CanonicalRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.OPENAI,
                "/v1/chat/completions",
                "model-a",
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null
        );
        GatewayUsageView usage = new GatewayUsageView(
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
                GatewayUsageCompleteness.FINAL,
                GatewayUsageSource.DIRECT_RESPONSE,
                Map.of("provider", "openai")
        );
        Instant startedAt = Instant.now();

        service.startRequest("req-fallback", selectionResult(), request, false, startedAt);
        service.completeRequest("req-fallback", selectionResult(), request, false, usage, startedAt);

        Mockito.verify(asyncPersistenceService).enqueueRequestLogStart(Mockito.any());
        Mockito.verify(asyncPersistenceService).enqueueRequestLogFinish(Mockito.any());
        Mockito.verify(asyncPersistenceService).enqueueUsageRecordUpsert(Mockito.any());
        assertNotNull(storedRequest.get());
        assertEquals(GatewayRequestStatus.COMPLETED, storedRequest.get().getStatus());
        assertNotNull(storedUsage.get());
        assertEquals(18, storedUsage.get().getTotalTokens());
    }

    @Test
    void shouldSettleCompletedUsageWhenPersistingUsageRecord() {
        RequestLogRepository requestLogRepository = Mockito.mock(RequestLogRepository.class);
        UsageRecordRepository usageRecordRepository = Mockito.mock(UsageRecordRepository.class);
        GatewayAuditLogService gatewayAuditLogService = Mockito.mock(GatewayAuditLogService.class);
        CostRoutingService costRoutingService = Mockito.mock(CostRoutingService.class);

        Mockito.when(requestLogRepository.save(Mockito.any(RequestLogEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(requestLogRepository.findByRequestId("req-cost"))
                .thenReturn(Optional.empty());
        Mockito.when(usageRecordRepository.findByRequestId("req-cost"))
                .thenReturn(Optional.empty());
        Mockito.when(usageRecordRepository.save(Mockito.any(UsageRecordEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GatewayRequestLifecycleService service = new GatewayRequestLifecycleService(
                requestLogRepository,
                usageRecordRepository,
                gatewayAuditLogService,
                new SimpleMeterRegistry(),
                new tools.jackson.databind.ObjectMapper(),
                null,
                null,
                null,
                costRoutingService
        );

        CanonicalRequest request = new CanonicalRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.OPENAI,
                "/v1/chat/completions",
                "model-a",
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null
        );
        GatewayUsageView usage = new GatewayUsageView(
                12,
                10,
                5,
                0,
                1,
                0,
                0,
                0,
                0,
                null,
                15,
                GatewayUsageCompleteness.FINAL,
                GatewayUsageSource.DIRECT_RESPONSE,
                null
        );
        Instant startedAt = Instant.now();
        RouteSelectionResult selectionResult = selectionResult();

        service.startRequest("req-cost", selectionResult, request, false, startedAt);
        service.completeRequest("req-cost", selectionResult, request, false, usage, startedAt);

        Mockito.verify(costRoutingService).settleCompletedUsage("req-cost", selectionResult, usage);
    }

    private RouteSelectionResult selectionResult() {
        CatalogCandidateView candidate = new CatalogCandidateView(
                101L,
                "candidate",
                ProviderType.OPENAI_DIRECT,
                1L,
                ProviderFamily.OPENAI,
                UpstreamSiteKind.OPENAI_DIRECT,
                AuthStrategy.BEARER,
                PathStrategy.OPENAI_V1,
                ErrorSchemaStrategy.OPENAI_ERROR,
                "https://example.com",
                "model-a",
                "model-a",
                List.of("openai"),
                true,
                true,
                true,
                true,
                true,
                true,
                ReasoningTransport.OPENAI_CHAT,
                InteropCapabilityLevel.NATIVE
        );
        RouteCandidateView selected = new RouteCandidateView(candidate, 11L, 10, 100);
        return new RouteSelectionResult(
                1L,
                "sk-gw-test",
                "model-a",
                "model-a",
                "model-a",
                "openai",
                "prefix",
                "fingerprint",
                "model-a",
                GatewayClientFamily.GENERIC_OPENAI,
                List.of(),
                null,
                RouteSelectionSource.WEIGHTED_HASH,
                selected,
                List.of(selected)
        );
    }
}
