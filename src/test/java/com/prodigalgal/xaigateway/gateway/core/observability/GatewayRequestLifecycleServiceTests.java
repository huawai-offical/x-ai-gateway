package com.prodigalgal.xaigateway.gateway.core.observability;

import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceResponse;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.SupportStatus;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
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
