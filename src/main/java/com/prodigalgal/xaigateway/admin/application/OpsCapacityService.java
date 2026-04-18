package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.OpsCapacitySummaryResponse;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyGovernanceService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class OpsCapacityService {

    private static final Duration DEFAULT_WINDOW = Duration.ofHours(1);

    private final DashboardQueryService dashboardQueryService;
    private final DistributedKeyQueryService distributedKeyQueryService;
    private final DistributedKeyGovernanceService distributedKeyGovernanceService;

    public OpsCapacityService(
            DashboardQueryService dashboardQueryService,
            DistributedKeyQueryService distributedKeyQueryService,
            DistributedKeyGovernanceService distributedKeyGovernanceService) {
        this.dashboardQueryService = dashboardQueryService;
        this.distributedKeyQueryService = distributedKeyQueryService;
        this.distributedKeyGovernanceService = distributedKeyGovernanceService;
    }

    public OpsCapacitySummaryResponse summary(Instant now) {
        Instant observedAt = now == null ? Instant.now() : now;
        Instant from = observedAt.minus(DEFAULT_WINDOW);
        var dashboardOverview = dashboardQueryService.overview(null, null, from, observedAt, 60);

        List<OpsCapacitySummaryResponse.DistributedKeyPressure> distributedKeyPressures = distributedKeyQueryService.listActive().stream()
                .map(this::toPressure)
                .sorted(Comparator
                        .comparingInt((OpsCapacitySummaryResponse.DistributedKeyPressure item) -> pressureRank(item.pressureLevel()))
                        .reversed()
                        .thenComparing(OpsCapacitySummaryResponse.DistributedKeyPressure::keyName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        LinkedHashSet<String> recommendedActions = new LinkedHashSet<>();
        dashboardOverview.alerts().stream()
                .map(alert -> alert.suggestedActions() == null ? List.<String>of() : alert.suggestedActions())
                .flatMap(List::stream)
                .forEach(recommendedActions::add);
        distributedKeyPressures.stream()
                .filter(item -> pressureRank(item.pressureLevel()) >= pressureRank("HIGH"))
                .map(item -> "检查 DistributedKey `" + item.keyName() + "` 的预算、并发和上游冗余。")
                .forEach(recommendedActions::add);

        return new OpsCapacitySummaryResponse(
                observedAt,
                distributedKeyPressures,
                dashboardOverview.providerRanking(),
                dashboardOverview.modelGroupRanking(),
                dashboardOverview.credentialRanking(),
                dashboardOverview.alerts(),
                List.copyOf(recommendedActions)
        );
    }

    private OpsCapacitySummaryResponse.DistributedKeyPressure toPressure(DistributedKeyView distributedKey) {
        DistributedKeyGovernanceService.GovernanceWindowSnapshot snapshot = distributedKeyGovernanceService.snapshot(distributedKey);
        return new OpsCapacitySummaryResponse.DistributedKeyPressure(
                distributedKey.id(),
                distributedKey.keyName(),
                distributedKey.maskedKey(),
                snapshot.pressureLevel(),
                distributedKey.budgetLimitMicros(),
                snapshot.currentBudgetMicros(),
                remaining(distributedKey.budgetLimitMicros(), snapshot.currentBudgetMicros()),
                distributedKey.rpmLimit(),
                snapshot.currentRpm(),
                remaining(distributedKey.rpmLimit() == null ? null : distributedKey.rpmLimit().longValue(), snapshot.currentRpm()),
                distributedKey.tpmLimit(),
                snapshot.currentTpm(),
                remaining(distributedKey.tpmLimit() == null ? null : distributedKey.tpmLimit().longValue(), snapshot.currentTpm()),
                distributedKey.concurrencyLimit(),
                snapshot.currentConcurrency(),
                remaining(distributedKey.concurrencyLimit() == null ? null : distributedKey.concurrencyLimit().longValue(), snapshot.currentConcurrency()),
                snapshot.notes()
        );
    }

    private Long remaining(Long limit, long current) {
        if (limit == null) {
            return null;
        }
        return Math.max(0L, limit - current);
    }

    private int pressureRank(String pressureLevel) {
        if (pressureLevel == null) {
            return 0;
        }
        return switch (pressureLevel) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }
}
