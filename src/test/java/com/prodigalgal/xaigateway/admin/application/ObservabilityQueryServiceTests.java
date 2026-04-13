package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.CacheHitLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RouteDecisionLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCacheReferenceEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UsageRecordEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.CacheHitLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RouteDecisionLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCacheReferenceRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UsageRecordRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObservabilityQueryServiceTests {

    @Test
    void shouldCombineDistributedKeyAndProviderFiltersForCacheHits() {
        RouteDecisionLogRepository routeDecisionLogRepository = Mockito.mock(RouteDecisionLogRepository.class);
        CacheHitLogRepository cacheHitLogRepository = Mockito.mock(CacheHitLogRepository.class);
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
}
