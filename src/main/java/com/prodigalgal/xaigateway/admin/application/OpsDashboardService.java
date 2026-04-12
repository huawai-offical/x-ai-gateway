package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.OpsAlertEventResponse;
import com.prodigalgal.xaigateway.admin.api.OpsOperationAuditResponse;
import com.prodigalgal.xaigateway.admin.api.OpsScheduledProbeJobResponse;
import com.prodigalgal.xaigateway.admin.api.OpsSummaryResponse;
import com.prodigalgal.xaigateway.admin.api.OpsTrafficSnapshotResponse;
import com.prodigalgal.xaigateway.gateway.core.ops.OpsEventBusService;
import com.prodigalgal.xaigateway.gateway.core.ops.OpsEventType;
import com.prodigalgal.xaigateway.infra.persistence.entity.RouteDecisionLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.RouteDecisionLogRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class OpsDashboardService {

    private final RouteDecisionLogRepository routeDecisionLogRepository;
    private final OpsAlertService opsAlertService;
    private final OpsProbeJobService opsProbeJobService;
    private final OpsAuditService opsAuditService;
    private final OpsEventBusService opsEventBusService;

    public OpsDashboardService(
            RouteDecisionLogRepository routeDecisionLogRepository,
            OpsAlertService opsAlertService,
            OpsProbeJobService opsProbeJobService,
            OpsAuditService opsAuditService,
            OpsEventBusService opsEventBusService) {
        this.routeDecisionLogRepository = routeDecisionLogRepository;
        this.opsAlertService = opsAlertService;
        this.opsProbeJobService = opsProbeJobService;
        this.opsAuditService = opsAuditService;
        this.opsEventBusService = opsEventBusService;
    }

    public OpsSummaryResponse summary() {
        OpsTrafficSnapshotResponse snapshot = buildSnapshot();
        return new OpsSummaryResponse(
                snapshot,
                opsAlertService.listEvents("OPEN"),
                opsProbeJobService.list(),
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
        double qps = routeDecisions.size() / 5.0;
        long activeAlerts = opsAlertService.listEvents("OPEN").size();
        double errorRate = routeDecisions.isEmpty() ? 0D : Math.min(1D, activeAlerts / (double) routeDecisions.size());
        return new OpsTrafficSnapshotResponse(
                now,
                qps,
                errorRate,
                0D,
                activeAlerts,
                activeAlerts,
                opsAlertService.listEvents("OPEN").stream().map(OpsAlertEventResponse::entityRef).filter(item -> item != null && !item.isBlank()).distinct().toList()
        );
    }
}
