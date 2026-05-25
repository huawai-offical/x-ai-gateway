package com.prodigalgal.xaigateway.gateway.core.observability;

import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.SupportStatus;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateEvaluation;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import com.prodigalgal.xaigateway.infra.persistence.entity.CacheHitLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RouteDecisionLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.CacheHitLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RouteDecisionLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCacheReferenceRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayObservabilityServiceTests {

    @Test
    void shouldPersistSupportAndDegradationForRouteDecisionAndCacheUsage() {
        RouteDecisionLogRepository routeDecisionLogRepository = Mockito.mock(RouteDecisionLogRepository.class);
        CacheHitLogRepository cacheHitLogRepository = Mockito.mock(CacheHitLogRepository.class);
        UpstreamCacheReferenceRepository upstreamCacheReferenceRepository = Mockito.mock(UpstreamCacheReferenceRepository.class);
        GatewayObservabilityService service = new GatewayObservabilityService(
                routeDecisionLogRepository,
                cacheHitLogRepository,
                upstreamCacheReferenceRepository,
                new ObjectMapper()
        );

        RouteSelectionResult selectionResult = selectionResult();
        service.recordRouteDecision(
                "req-1",
                selectionResult,
                "/v1/files/file_123/content",
                "file",
                "file_content_get",
                ExecutionBackend.NATIVE,
                SupportStatus.DEGRADED,
                "resource-orchestration",
                InteropCapabilityLevel.LOSSY
        );
        service.recordCacheUsage(
                "req-1",
                selectionResult,
                GatewayUsage.empty(),
                "none",
                null,
                "/v1/files/file_123/content",
                "file",
                "file_content_get",
                ExecutionBackend.NATIVE,
                SupportStatus.DEGRADED,
                "resource-orchestration",
                InteropCapabilityLevel.LOSSY
        );

        ArgumentCaptor<RouteDecisionLogEntity> routeCaptor = ArgumentCaptor.forClass(RouteDecisionLogEntity.class);
        ArgumentCaptor<CacheHitLogEntity> cacheCaptor = ArgumentCaptor.forClass(CacheHitLogEntity.class);
        Mockito.verify(routeDecisionLogRepository).save(routeCaptor.capture());
        Mockito.verify(cacheHitLogRepository).save(cacheCaptor.capture());

        assertEquals("DEGRADED", routeCaptor.getValue().getSupportStatus());
        assertEquals("LOSSY", routeCaptor.getValue().getDegradationLevel());
        assertEquals("DEGRADED", cacheCaptor.getValue().getSupportStatus());
        assertEquals("LOSSY", cacheCaptor.getValue().getDegradationLevel());
    }

    @Test
    void shouldFallbackToSynchronousPersistenceWhenAsyncEnqueueFails() {
        RouteDecisionLogRepository routeDecisionLogRepository = Mockito.mock(RouteDecisionLogRepository.class);
        CacheHitLogRepository cacheHitLogRepository = Mockito.mock(CacheHitLogRepository.class);
        UpstreamCacheReferenceRepository upstreamCacheReferenceRepository = Mockito.mock(UpstreamCacheReferenceRepository.class);
        GatewayObservabilityAsyncPersistenceService asyncPersistenceService = Mockito.mock(GatewayObservabilityAsyncPersistenceService.class);
        Mockito.when(asyncPersistenceService.enqueueRouteDecisionLogInsert(Mockito.any())).thenReturn(false);
        Mockito.when(asyncPersistenceService.enqueueCacheHitLogInsert(Mockito.any())).thenReturn(false);

        GatewayObservabilityService service = new GatewayObservabilityService(
                routeDecisionLogRepository,
                cacheHitLogRepository,
                upstreamCacheReferenceRepository,
                new ObjectMapper(),
                asyncPersistenceService
        );

        RouteSelectionResult selectionResult = selectionResult();
        service.recordRouteDecision(
                "req-fallback",
                selectionResult,
                "/v1/files/file_123/content",
                "file",
                "file_content_get",
                ExecutionBackend.NATIVE,
                SupportStatus.DEGRADED,
                "resource-orchestration",
                InteropCapabilityLevel.LOSSY
        );
        service.recordCacheUsage(
                "req-fallback",
                selectionResult,
                GatewayUsage.empty(),
                "none",
                null,
                "/v1/files/file_123/content",
                "file",
                "file_content_get",
                ExecutionBackend.NATIVE,
                SupportStatus.DEGRADED,
                "resource-orchestration",
                InteropCapabilityLevel.LOSSY
        );

        Mockito.verify(asyncPersistenceService).enqueueRouteDecisionLogInsert(Mockito.any());
        Mockito.verify(asyncPersistenceService).enqueueCacheHitLogInsert(Mockito.any());
        Mockito.verify(routeDecisionLogRepository).save(Mockito.any(RouteDecisionLogEntity.class));
        Mockito.verify(cacheHitLogRepository).save(Mockito.any(CacheHitLogEntity.class));
    }

    @Test
    void shouldSerializeRuntimeProviderInCandidateSummary() throws Exception {
        RouteDecisionLogRepository routeDecisionLogRepository = Mockito.mock(RouteDecisionLogRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        GatewayObservabilityService service = new GatewayObservabilityService(
                routeDecisionLogRepository,
                Mockito.mock(CacheHitLogRepository.class),
                Mockito.mock(UpstreamCacheReferenceRepository.class),
                objectMapper
        );

        service.recordRouteDecision("req-mimo", providerSpecificSelectionResult(
                "https://token-plan-sgp.xiaomimimo.com/v1",
                "mimo-v2.5-pro"
        ));
        service.recordRouteDecision("req-deepseek", providerSpecificSelectionResult(
                "https://api.deepseek.com",
                "deepseek-chat"
        ));
        service.recordRouteDecision("req-xai", providerSpecificSelectionResult(
                "https://api.x.ai/v1",
                "grok-4.3"
        ));

        ArgumentCaptor<RouteDecisionLogEntity> routeCaptor = ArgumentCaptor.forClass(RouteDecisionLogEntity.class);
        Mockito.verify(routeDecisionLogRepository, Mockito.times(3)).save(routeCaptor.capture());
        var mimoCandidate = objectMapper.readTree(routeCaptor.getAllValues().get(0).getCandidateSummaryJson())
                .path("candidates")
                .get(0);
        assertEquals("XIAOMI_MIMO", mimoCandidate.path("runtimeProvider").asText());
        assertEquals("XIAOMI_MIMO", mimoCandidate.path("siteKind").asText());
        assertEquals("xiaomi_mimo.openai_compatible", mimoCandidate.path("protocolSuite").asText());
        var deepSeekCandidate = objectMapper.readTree(routeCaptor.getAllValues().get(1).getCandidateSummaryJson())
                .path("candidates")
                .get(0);
        assertEquals("DEEPSEEK", deepSeekCandidate.path("runtimeProvider").asText());
        assertEquals("DEEPSEEK", deepSeekCandidate.path("siteKind").asText());
        assertEquals("deepseek.openai_compatible", deepSeekCandidate.path("protocolSuite").asText());
        var xaiCandidate = objectMapper.readTree(routeCaptor.getAllValues().get(2).getCandidateSummaryJson())
                .path("candidates")
                .get(0);
        assertEquals("XAI", xaiCandidate.path("runtimeProvider").asText());
        assertEquals("GROK", xaiCandidate.path("siteKind").asText());
        assertEquals("grok.openai_compatible", xaiCandidate.path("protocolSuite").asText());
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

    private RouteSelectionResult providerSpecificSelectionResult(String baseUrl, String model) {
        CatalogCandidateView candidate = new CatalogCandidateView(
                101L,
                model,
                ProviderType.OPENAI_COMPATIBLE,
                baseUrl,
                model,
                model,
                List.of("openai", "responses"),
                true,
                true,
                false,
                false,
                true,
                true,
                ReasoningTransport.OPENAI_CHAT
        );
        RouteCandidateView selected = new RouteCandidateView(candidate, 11L, 10, 100);
        RouteCandidateEvaluation evaluation = new RouteCandidateEvaluation(
                selected,
                true,
                "HEALTHY",
                null,
                false,
                RouteSelectionSource.WEIGHTED_HASH,
                0D,
                List.of(),
                List.of()
        );
        return new RouteSelectionResult(
                1L,
                "sk-gw-test",
                model,
                model,
                model,
                "openai",
                "prefix",
                "fingerprint",
                model,
                GatewayClientFamily.GENERIC_OPENAI,
                null,
                List.of(),
                null,
                RouteSelectionSource.WEIGHTED_HASH,
                selected,
                List.of(selected),
                List.of(evaluation),
                List.of()
        );
    }
}
