package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.ObservabilityQueryService;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/observability")
public class ObservabilityAdminController {

    private final ObservabilityQueryService observabilityQueryService;

    public ObservabilityAdminController(ObservabilityQueryService observabilityQueryService) {
        this.observabilityQueryService = observabilityQueryService;
    }

    @GetMapping("/route-decisions")
    public List<RouteDecisionLogResponse> listRouteDecisions(
            @RequestParam(required = false) Long distributedKeyId,
            @RequestParam(required = false) ProviderType providerType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return observabilityQueryService.listRouteDecisions(distributedKeyId, providerType, from, to);
    }

    @GetMapping("/cache-hits")
    public List<CacheHitLogResponse> listCacheHits(
            @RequestParam(required = false) Long distributedKeyId,
            @RequestParam(required = false) ProviderType providerType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return observabilityQueryService.listCacheHits(distributedKeyId, providerType, from, to);
    }

    @GetMapping("/upstream-cache-references")
    public List<UpstreamCacheReferenceResponse> listUpstreamCacheReferences(
            @RequestParam(required = false) Long distributedKeyId,
            @RequestParam(required = false) ProviderType providerType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return observabilityQueryService.listUpstreamCacheReferences(distributedKeyId, providerType, status, from, to);
    }

    @GetMapping("/summary")
    public ObservabilitySummaryResponse summary(
            @RequestParam(required = false) Long distributedKeyId,
            @RequestParam(required = false) ProviderType providerType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return observabilityQueryService.summary(distributedKeyId, providerType, from, to);
    }
}
