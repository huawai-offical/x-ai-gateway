package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AsyncResourceDetailResponse;
import com.prodigalgal.xaigateway.admin.api.AsyncResourceSummaryResponse;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceLifecycle;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceLineage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceTransition;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceCanonicalizer;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceType;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayAsyncResourceEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayAsyncResourceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AsyncResourceAdminService {

    private static final PageRequest DEFAULT_SAMPLE_PAGE = PageRequest.of(0, 100);
    private static final Duration DEFAULT_PARTIAL_WINDOW = Duration.ofHours(24);

    private final GatewayAsyncResourceRepository gatewayAsyncResourceRepository;
    private final GatewayAsyncResourceCanonicalizer gatewayAsyncResourceCanonicalizer;

    public AsyncResourceAdminService(
            GatewayAsyncResourceRepository gatewayAsyncResourceRepository,
            GatewayAsyncResourceCanonicalizer gatewayAsyncResourceCanonicalizer) {
        this.gatewayAsyncResourceRepository = gatewayAsyncResourceRepository;
        this.gatewayAsyncResourceCanonicalizer = gatewayAsyncResourceCanonicalizer;
    }

    public List<AsyncResourceSummaryResponse> listAsyncResources(
            Long distributedKeyId,
            GatewayAsyncResourceType resourceType,
            String status,
            Instant from,
            Instant to) {
        TimeWindow window = resolveWindow(from, to);
        String normalizedStatus = normalizeStatus(status);
        List<GatewayAsyncResourceEntity> entities;
        if (window == null) {
            entities = gatewayAsyncResourceRepository.search(distributedKeyId, resourceType, normalizedStatus, DEFAULT_SAMPLE_PAGE);
        } else {
            entities = gatewayAsyncResourceRepository.searchWithinWindow(
                    distributedKeyId,
                    resourceType,
                    normalizedStatus,
                    window.from(),
                    window.to());
        }
        return entities.stream().map(this::toSummaryResponse).toList();
    }

    public AsyncResourceDetailResponse getAsyncResource(String resourceKey) {
        GatewayAsyncResourceEntity entity = gatewayAsyncResourceRepository.findByResourceKeyAndDeletedFalse(resourceKey)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定的异步资源对象。"));
        return toDetailResponse(entity);
    }

    private AsyncResourceSummaryResponse toSummaryResponse(GatewayAsyncResourceEntity entity) {
        CanonicalResourceLifecycle lifecycle = gatewayAsyncResourceCanonicalizer.toLifecycle(entity);
        CanonicalResourceLineage lineage = gatewayAsyncResourceCanonicalizer.toLineage(entity);
        return new AsyncResourceSummaryResponse(
                entity.getResourceKey(),
                entity.getResourceType(),
                lifecycle.status(),
                lifecycle.normalizedStatus(),
                lifecycle.terminal(),
                lifecycle.deleted(),
                lineage.objectMode(),
                lineage.upstreamObjectId(),
                lifecycle.eventCount(),
                lifecycle.latestTransition(),
                lifecycle.failureReason(),
                lifecycle.cancelReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private AsyncResourceDetailResponse toDetailResponse(GatewayAsyncResourceEntity entity) {
        CanonicalResourceLifecycle lifecycle = gatewayAsyncResourceCanonicalizer.toLifecycle(entity);
        List<CanonicalResourceTransition> transitions = gatewayAsyncResourceCanonicalizer.toTransitions(entity);
        return new AsyncResourceDetailResponse(
                lifecycle,
                transitions,
                gatewayAsyncResourceCanonicalizer.toLineage(entity),
                gatewayAsyncResourceCanonicalizer.toArtifacts(entity),
                gatewayAsyncResourceCanonicalizer.readPayload(entity.getRequestPayloadJson()),
                gatewayAsyncResourceCanonicalizer.readPayload(entity.getResponsePayloadJson()),
                gatewayAsyncResourceCanonicalizer.readPayload(entity.getMetadataJson())
        );
    }

    private TimeWindow resolveWindow(Instant from, Instant to) {
        if (from == null && to == null) {
            return null;
        }
        Instant resolvedTo = to == null ? Instant.now() : to;
        Instant resolvedFrom = from == null ? resolvedTo.minus(DEFAULT_PARTIAL_WINDOW) : from;
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new IllegalArgumentException("from 不能晚于 to。");
        }
        return new TimeWindow(resolvedFrom, resolvedTo);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return status.trim().toLowerCase();
    }

    private record TimeWindow(
            Instant from,
            Instant to
    ) {
    }
}
