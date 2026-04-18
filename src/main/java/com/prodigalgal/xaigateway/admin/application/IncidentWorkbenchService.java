package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AlertSilenceResponse;
import com.prodigalgal.xaigateway.admin.api.CredentialHealthScoreResponse;
import com.prodigalgal.xaigateway.admin.api.GovernanceHealthScoreResponse;
import com.prodigalgal.xaigateway.admin.api.IncidentEntityResponse;
import com.prodigalgal.xaigateway.admin.api.IncidentSummaryResponse;
import com.prodigalgal.xaigateway.admin.api.IncidentTimelineEventResponse;
import com.prodigalgal.xaigateway.admin.api.OpsAlertEventResponse;
import com.prodigalgal.xaigateway.admin.api.OpsCapacitySummaryResponse;
import com.prodigalgal.xaigateway.admin.api.OpsOperationAuditResponse;
import com.prodigalgal.xaigateway.admin.api.OpsSloSummaryResponse;
import com.prodigalgal.xaigateway.admin.api.OpsSummaryResponse;
import com.prodigalgal.xaigateway.admin.api.QuarantineRecordResponse;
import com.prodigalgal.xaigateway.admin.api.SiteHealthScoreResponse;
import com.prodigalgal.xaigateway.gateway.core.governance.QuarantineStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IncidentWorkbenchService {

    private final OpsDashboardService opsDashboardService;
    private final OpsAlertService opsAlertService;
    private final OpsSloService opsSloService;
    private final OpsCapacityService opsCapacityService;
    private final GovernanceAdminService governanceAdminService;

    public IncidentWorkbenchService(
            OpsDashboardService opsDashboardService,
            OpsAlertService opsAlertService,
            OpsSloService opsSloService,
            OpsCapacityService opsCapacityService,
            GovernanceAdminService governanceAdminService) {
        this.opsDashboardService = opsDashboardService;
        this.opsAlertService = opsAlertService;
        this.opsSloService = opsSloService;
        this.opsCapacityService = opsCapacityService;
        this.governanceAdminService = governanceAdminService;
    }

    public IncidentSummaryResponse summary() {
        Instant now = Instant.now();
        OpsSummaryResponse opsSummary = opsDashboardService.summary();
        List<OpsAlertEventResponse> incidents = opsAlertService.listEvents("OPEN");
        OpsSloSummaryResponse sloSummary = opsSloService.summary(now);
        OpsCapacitySummaryResponse capacitySummary = opsCapacityService.summary(now);
        GovernanceHealthScoreResponse healthScores = governanceAdminService.listHealthScores();
        List<AlertSilenceResponse> silences = opsAlertService.listSilences();
        List<QuarantineRecordResponse> quarantines = governanceAdminService.listQuarantines(null);

        return new IncidentSummaryResponse(
                opsSummary,
                sloSummary,
                capacitySummary,
                healthScores,
                incidents,
                silences,
                quarantines,
                buildAffectedEntities(incidents, quarantines, healthScores),
                buildTimeline(incidents, quarantines, opsSummary.recentLogs(), silences),
                buildRecommendedActions(incidents, sloSummary, capacitySummary, healthScores, quarantines)
        );
    }

    private List<IncidentEntityResponse> buildAffectedEntities(
            List<OpsAlertEventResponse> incidents,
            List<QuarantineRecordResponse> quarantines,
            GovernanceHealthScoreResponse healthScores) {
        Map<String, IncidentEntityResponse> entities = new LinkedHashMap<>();

        incidents.forEach(alert -> putEntity(
                entities,
                safe(alert.entityType(), "ALERT"),
                safe(alert.entityRef(), "unknown"),
                alert.title(),
                alert.message(),
                alert.severity(),
                alert.status(),
                "ALERT_EVENT"
        ));

        quarantines.stream()
                .filter(item -> item.status() == QuarantineStatus.ACTIVE)
                .forEach(item -> putEntity(
                        entities,
                        item.targetType().name(),
                        quarantineRef(item),
                        item.targetType().name() + " 已隔离",
                        item.reason(),
                        "HIGH",
                        item.status().name(),
                        "QUARANTINE"
                ));

        healthScores.sites().stream()
                .filter(site -> !"HEALTHY".equals(site.healthState()))
                .forEach(site -> putEntity(
                        entities,
                        "SITE_PROFILE",
                        String.valueOf(site.siteProfileId()),
                        site.displayName(),
                        site.reason(),
                        severityForHealth(site.healthState()),
                        site.healthState(),
                        "SITE_HEALTH"
                ));

        healthScores.credentials().stream()
                .filter(credential -> !"HEALTHY".equals(credential.healthState()))
                .forEach(credential -> putEntity(
                        entities,
                        "CREDENTIAL",
                        String.valueOf(credential.credentialId()),
                        credential.credentialName(),
                        credential.reason(),
                        severityForHealth(credential.healthState()),
                        credential.healthState(),
                        "CREDENTIAL_HEALTH"
                ));

        return entities.values().stream()
                .sorted(Comparator.comparing(IncidentEntityResponse::severity, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(IncidentEntityResponse::title, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private List<IncidentTimelineEventResponse> buildTimeline(
            List<OpsAlertEventResponse> incidents,
            List<QuarantineRecordResponse> quarantines,
            List<OpsOperationAuditResponse> recentLogs,
            List<AlertSilenceResponse> silences) {
        List<IncidentTimelineEventResponse> timeline = new ArrayList<>();

        incidents.forEach(alert -> timeline.add(new IncidentTimelineEventResponse(
                alert.eventType(),
                alert.title(),
                alert.message(),
                alert.severity(),
                alert.entityType(),
                alert.entityRef(),
                "ALERT_EVENT",
                firstNonNull(alert.updatedAt(), alert.createdAt())
        )));

        quarantines.forEach(quarantine -> {
            timeline.add(new IncidentTimelineEventResponse(
                    "QUARANTINE_" + quarantine.status().name(),
                    quarantine.targetType().name() + " " + quarantine.status().name().toLowerCase(Locale.ROOT),
                    quarantine.reason(),
                    "HIGH",
                    quarantine.targetType().name(),
                    quarantineRef(quarantine),
                    "QUARANTINE",
                    firstNonNull(quarantine.updatedAt(), quarantine.startedAt(), quarantine.createdAt())
            ));
            if (quarantine.releasedAt() != null) {
                timeline.add(new IncidentTimelineEventResponse(
                        "QUARANTINE_RELEASED",
                        quarantine.targetType().name() + " released",
                        quarantine.releaseReason(),
                        "INFO",
                        quarantine.targetType().name(),
                        quarantineRef(quarantine),
                        "QUARANTINE",
                        quarantine.releasedAt()
                ));
            }
        });

        recentLogs.forEach(log -> timeline.add(new IncidentTimelineEventResponse(
                log.action(),
                log.category() + " / " + log.action(),
                log.detailJson(),
                "INFO",
                log.resourceType(),
                log.resourceRef(),
                "AUDIT",
                log.createdAt()
        )));

        silences.stream()
                .filter(AlertSilenceResponse::enabled)
                .forEach(silence -> timeline.add(new IncidentTimelineEventResponse(
                        "ALERT_SILENCE",
                        silence.silenceName(),
                        silence.reason(),
                        "INFO",
                        silence.entityType(),
                        silence.entityRef(),
                        "SILENCE",
                        firstNonNull(silence.startsAt(), silence.createdAt())
                )));

        return timeline.stream()
                .filter(item -> item.occurredAt() != null)
                .sorted(Comparator.comparing(IncidentTimelineEventResponse::occurredAt).reversed())
                .limit(20)
                .toList();
    }

    private List<String> buildRecommendedActions(
            List<OpsAlertEventResponse> incidents,
            OpsSloSummaryResponse sloSummary,
            OpsCapacitySummaryResponse capacitySummary,
            GovernanceHealthScoreResponse healthScores,
            List<QuarantineRecordResponse> quarantines) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        actions.addAll(sloSummary.recommendedActions());
        actions.addAll(capacitySummary.recommendedActions());

        if (!incidents.isEmpty()) {
            actions.add("优先打开受影响 Incident，确认是否需要 Ack、静默或进入 Trace Workbench。");
        }
        if (quarantines.stream().anyMatch(item -> item.status() == QuarantineStatus.ACTIVE)) {
            actions.add("存在 ACTIVE quarantine，优先检查是否需要 release 或继续 drain。");
        }
        if (healthScores.sites().stream().anyMatch(item -> !"HEALTHY".equals(item.healthState()))) {
            actions.add("存在降级站点，建议进入 Site dossier 查看 blocked surfaces 与建议动作。");
        }
        if (healthScores.credentials().stream().anyMatch(item -> !"HEALTHY".equals(item.healthState()))) {
            actions.add("存在受治理影响的凭证，建议跳转 Trace Workbench 检查最近 requestId 与 route decision。");
        }
        return List.copyOf(actions);
    }

    private void putEntity(
            Map<String, IncidentEntityResponse> entities,
            String entityType,
            String entityRef,
            String title,
            String summary,
            String severity,
            String status,
            String source) {
        String key = entityType + ":" + entityRef;
        entities.putIfAbsent(key, new IncidentEntityResponse(entityType, entityRef, title, summary, severity, status, source));
    }

    private String quarantineRef(QuarantineRecordResponse item) {
        return switch (item.targetType()) {
            case PROVIDER_TYPE -> item.providerType() == null ? "-" : item.providerType().name();
            case SITE_PROFILE -> String.valueOf(item.siteProfileId());
            case CREDENTIAL -> String.valueOf(item.credentialId());
            case ACCOUNT -> String.valueOf(item.accountId());
            case PROXY -> String.valueOf(item.proxyId());
        };
    }

    private String severityForHealth(String healthState) {
        if (healthState == null) {
            return "INFO";
        }
        return switch (healthState) {
            case "QUARANTINED", "POLICY_BLOCKED", "NO_ACTIVE_CREDENTIAL" -> "CRITICAL";
            case "DEGRADED", "COOLDOWN" -> "HIGH";
            default -> "INFO";
        };
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
