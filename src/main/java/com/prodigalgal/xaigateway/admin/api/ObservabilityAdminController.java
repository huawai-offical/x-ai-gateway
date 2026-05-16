package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.ObservabilityQueryService;
import com.prodigalgal.xaigateway.admin.application.MonitoringBillingRollupService;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/observability")
public class ObservabilityAdminController {

    private final ObservabilityQueryService observabilityQueryService;
    private final MonitoringBillingRollupService monitoringBillingRollupService;

    public ObservabilityAdminController(
            ObservabilityQueryService observabilityQueryService,
            MonitoringBillingRollupService monitoringBillingRollupService) {
        this.observabilityQueryService = observabilityQueryService;
        this.monitoringBillingRollupService = monitoringBillingRollupService;
    }

    @GetMapping("/route-decisions")
    public List<RouteDecisionLogResponse> listRouteDecisions(
            @RequestParam(required = false) Long distributedKeyId,
            @RequestParam(required = false) ProviderType providerType,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String gatewayResourceKey,
            @RequestParam(required = false) String upstreamObjectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return observabilityQueryService.listRouteDecisions(
                distributedKeyId,
                providerType,
                from,
                to,
                requestId,
                gatewayResourceKey,
                upstreamObjectId
        );
    }

    @GetMapping("/cache-hits")
    public List<CacheHitLogResponse> listCacheHits(
            @RequestParam(required = false) Long distributedKeyId,
            @RequestParam(required = false) ProviderType providerType,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String gatewayResourceKey,
            @RequestParam(required = false) String upstreamObjectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return observabilityQueryService.listCacheHits(
                distributedKeyId,
                providerType,
                from,
                to,
                requestId,
                gatewayResourceKey,
                upstreamObjectId
        );
    }

    @GetMapping("/request-logs")
    public List<RequestLogResponse> listRequestLogs(
            @RequestParam(required = false) Long distributedKeyId,
            @RequestParam(required = false) ProviderType providerType,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String gatewayResourceKey,
            @RequestParam(required = false) String upstreamObjectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return observabilityQueryService.listRequestLogs(
                distributedKeyId,
                providerType,
                from,
                to,
                requestId,
                gatewayResourceKey,
                upstreamObjectId
        );
    }

    @GetMapping("/codex-requests")
    public List<CodexObservabilityRequestResponse> listCodexRequests(
            @RequestParam(required = false) Long distributedKeyId,
            @RequestParam(required = false) ProviderType providerType,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String clientInstance,
            @RequestParam(required = false) String sessionAffinityKey,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return observabilityQueryService.listCodexRequests(
                distributedKeyId,
                providerType,
                requestId,
                clientInstance,
                sessionAffinityKey,
                model,
                status,
                from,
                to
        );
    }

    @GetMapping("/upstream-cache-references")
    public List<UpstreamCacheReferenceResponse> listUpstreamCacheReferences(
            @RequestParam(required = false) Long distributedKeyId,
            @RequestParam(required = false) ProviderType providerType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String gatewayResourceKey,
            @RequestParam(required = false) String upstreamObjectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return observabilityQueryService.listUpstreamCacheReferences(
                distributedKeyId,
                providerType,
                status,
                from,
                to,
                requestId,
                gatewayResourceKey,
                upstreamObjectId
        );
    }

    @GetMapping("/traces/{requestId}")
    public ObservabilityTraceResponse trace(@PathVariable String requestId) {
        return observabilityQueryService.trace(requestId);
    }

    @GetMapping("/summary")
    public ObservabilitySummaryResponse summary(
            @RequestParam(required = false) Long distributedKeyId,
            @RequestParam(required = false) ProviderType providerType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return observabilityQueryService.summary(distributedKeyId, providerType, from, to);
    }

    @GetMapping("/billing-rollup")
    public MonitoringBillingRollupResponse billingRollup(
            @RequestParam(defaultValue = "day") String period,
            @RequestParam(required = false) Long distributedKeyId,
            @RequestParam(required = false) ProviderType providerType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return monitoringBillingRollupService.rollup(period, distributedKeyId, providerType, from, to);
    }

    @GetMapping(value = "/billing-rollup.csv", produces = "text/csv")
    public String billingRollupCsv(
            @RequestParam(defaultValue = "day") String period,
            @RequestParam(required = false) Long distributedKeyId,
            @RequestParam(required = false) ProviderType providerType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return monitoringBillingRollupService.exportCsv(period, distributedKeyId, providerType, from, to);
    }
}
