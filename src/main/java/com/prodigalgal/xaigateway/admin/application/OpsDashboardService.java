package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.OpsAlertEventResponse;
import com.prodigalgal.xaigateway.admin.api.OpsOperationAuditResponse;
import com.prodigalgal.xaigateway.admin.api.OpsSummaryResponse;
import com.prodigalgal.xaigateway.admin.api.OpsTrafficSnapshotResponse;
import com.prodigalgal.xaigateway.gateway.core.ops.OpsEventBusService;
import com.prodigalgal.xaigateway.gateway.core.ops.OpsEventType;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestStatus;
import com.prodigalgal.xaigateway.infra.persistence.entity.RouteDecisionLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RequestLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.RouteDecisionLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RequestLogRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class OpsDashboardService {

    private final RouteDecisionLogRepository routeDecisionLogRepository;
    private final RequestLogRepository requestLogRepository;
    private final OpsAlertService opsAlertService;
    private final OpsAuditService opsAuditService;
    private final OpsEventBusService opsEventBusService;

    public OpsDashboardService(
            RouteDecisionLogRepository routeDecisionLogRepository,
            RequestLogRepository requestLogRepository,
            OpsAlertService opsAlertService,
            OpsAuditService opsAuditService,
            OpsEventBusService opsEventBusService) {
        this.routeDecisionLogRepository = routeDecisionLogRepository;
        this.requestLogRepository = requestLogRepository;
        this.opsAlertService = opsAlertService;
        this.opsAuditService = opsAuditService;
        this.opsEventBusService = opsEventBusService;
    }

    public OpsSummaryResponse summary() {
        OpsTrafficSnapshotResponse snapshot = buildSnapshot();
        return new OpsSummaryResponse(
                snapshot,
                opsAlertService.listEvents("OPEN"),
                opsAuditService.listRecent().stream().limit(50).toList()
        );
    }

    @Scheduled(fixedDelay = 5000)
    public void emitSnapshot() {
        OpsTrafficSnapshotResponse snapshot = buildSnapshot();
        opsAlertService.evaluate("qps", BigDecimal.valueOf(snapshot.qps()), "GATEWAY", "global");
        opsAlertService.evaluate("error_rate", BigDecimal.valueOf(snapshot.errorRate()), "GATEWAY", "global");
        opsEventBusService.publish(OpsEventType.TRAFFIC_SNAPSHOT, snapshot);
    }

    private OpsTrafficSnapshotResponse buildSnapshot() {
        Instant now = Instant.now();
        Instant from = now.minusSeconds(5);
        List<RouteDecisionLogEntity> routeDecisions = routeDecisionLogRepository.searchWithinWindow(null, null, from, now);
        List<RequestLogEntity> requestLogs = requestLogRepository.searchWithinWindow(null, null, from, now);
        List<OpsAlertEventResponse> openAlerts = opsAlertService.listEvents("OPEN");
        double qps = routeDecisions.size() / 5.0;
        long activeAlerts = openAlerts.size();
        long providerFailures = requestLogs.stream()
                .filter(entity -> entity.getStatus() == GatewayRequestStatus.FAILED)
                .count();
        double errorRate = requestLogs.isEmpty() ? 0D : Math.min(1D, providerFailures / (double) requestLogs.size());
        return new OpsTrafficSnapshotResponse(
                now,
                qps,
                errorRate,
                percentile95(requestLogs.stream()
                        .map(RequestLogEntity::getDurationMs)
                        .filter(duration -> duration != null && duration > 0)
                        .sorted(Comparator.naturalOrder())
                        .toList()),
                providerFailures,
                activeAlerts,
                openAlerts.stream().map(OpsAlertEventResponse::entityRef).filter(item -> item != null && !item.isBlank()).distinct().toList()
        );
    }

    private double percentile95(List<Long> sortedValues) {
        if (sortedValues.isEmpty()) {
            return 0D;
        }
        int index = (int) Math.ceil(sortedValues.size() * 0.95D) - 1;
        int boundedIndex = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(boundedIndex);
    }
}
