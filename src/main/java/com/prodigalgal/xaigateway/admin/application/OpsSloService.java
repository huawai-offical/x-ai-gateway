package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.DashboardOverviewResponse;
import com.prodigalgal.xaigateway.admin.api.OpsSloSummaryResponse;
import com.prodigalgal.xaigateway.admin.api.SloPolicyRequest;
import com.prodigalgal.xaigateway.admin.api.SloPolicyResponse;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestStatus;
import com.prodigalgal.xaigateway.infra.persistence.entity.RequestLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SloPolicyEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.OpsAlertEventRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RequestLogRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SloPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@Transactional
public class OpsSloService {

    private static final int DEFAULT_WINDOW_MINUTES = 60;
    private static final int MIN_WINDOW_MINUTES = 5;

    private final SloPolicyRepository sloPolicyRepository;
    private final RequestLogRepository requestLogRepository;
    private final OpsAlertEventRepository opsAlertEventRepository;
    private final DashboardQueryService dashboardQueryService;

    public OpsSloService(
            SloPolicyRepository sloPolicyRepository,
            RequestLogRepository requestLogRepository,
            OpsAlertEventRepository opsAlertEventRepository,
            DashboardQueryService dashboardQueryService) {
        this.sloPolicyRepository = sloPolicyRepository;
        this.requestLogRepository = requestLogRepository;
        this.opsAlertEventRepository = opsAlertEventRepository;
        this.dashboardQueryService = dashboardQueryService;
    }

    @Transactional(readOnly = true)
    public List<SloPolicyResponse> listPolicies() {
        return sloPolicyRepository.findAllByOrderByCreatedAtAsc().stream().map(this::toResponse).toList();
    }

    public SloPolicyResponse savePolicy(Long id, SloPolicyRequest request) {
        SloPolicyEntity entity = id == null
                ? new SloPolicyEntity()
                : sloPolicyRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("未找到 SLO policy。"));
        apply(entity, request);
        return toResponse(sloPolicyRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public OpsSloSummaryResponse summary(Instant now) {
        Instant observedAt = now == null ? Instant.now() : now;
        List<SloPolicyEntity> activePolicies = sloPolicyRepository.findAllByEnabledTrueOrderByCreatedAtAsc();
        int maxWindowMinutes = activePolicies.stream()
                .map(SloPolicyEntity::getWindowMinutes)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(DEFAULT_WINDOW_MINUTES);
        Instant from = observedAt.minus(Duration.ofMinutes(maxWindowMinutes));
        List<RequestLogEntity> requestLogs = requestLogRepository.searchWithinWindow(null, null, from, observedAt);
        DashboardOverviewResponse dashboardOverview = dashboardQueryService.overview(null, null, from, observedAt, 15);

        List<RiskComputation> riskComputations = activePolicies.stream()
                .map(policy -> evaluatePolicy(policy, requestLogs))
                .sorted(Comparator
                        .comparingInt((RiskComputation item) -> riskRank(item.riskLevel()))
                        .reversed()
                        .thenComparingDouble(RiskComputation::burnRate)
                        .reversed())
                .toList();

        RiskComputation worstRisk = riskComputations.isEmpty() ? null : riskComputations.get(0);
        long totalRequests = worstRisk == null ? requestLogs.size() : worstRisk.requestCount();
        long failedRequests = worstRisk == null
                ? requestLogs.stream().filter(entity -> entity.getStatus() == GatewayRequestStatus.FAILED).count()
                : worstRisk.failedRequestCount();
        double errorRate = worstRisk == null ? safeRatio(failedRequests, totalRequests) : worstRisk.errorRate();
        double errorBudgetRatio = worstRisk == null ? 0D : worstRisk.errorBudgetRatio();
        double remainingRatio = worstRisk == null ? 1D : worstRisk.errorBudgetRemainingRatio();
        double burnRate = worstRisk == null ? 0D : worstRisk.burnRate();
        String riskLevel = worstRisk == null ? "HEALTHY" : worstRisk.riskLevel();

        LinkedHashSet<String> recommendedActions = new LinkedHashSet<>();
        if (worstRisk != null) {
            recommendedActions.addAll(worstRisk.suggestedActions());
        }
        if (dashboardOverview != null && dashboardOverview.alerts() != null) {
            dashboardOverview.alerts().stream()
                    .map(DashboardOverviewResponse.DashboardAlert::suggestedActions)
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .forEach(recommendedActions::add);
        }

        return new OpsSloSummaryResponse(
                observedAt,
                new OpsSloSummaryResponse.SummaryCards(
                        totalRequests,
                        failedRequests,
                        errorRate,
                        errorBudgetRatio,
                        remainingRatio,
                        burnRate,
                        riskLevel,
                        opsAlertEventRepository.countByStatus("SILENCED")
                ),
                activePolicies.stream().map(this::toResponse).toList(),
                riskComputations.stream().map(RiskComputation::toResponse).toList(),
                List.copyOf(recommendedActions)
        );
    }

    private void apply(SloPolicyEntity entity, SloPolicyRequest request) {
        entity.setPolicyName(requireText(request.policyName(), "policyName"));
        entity.setScopeType(normalizeUpper(request.scopeType(), "scopeType"));
        entity.setScopeRef(blankToNull(request.scopeRef()));
        entity.setWindowMinutes(normalizeWindow(request.windowMinutes()));
        entity.setErrorBudgetRatio(requirePositive(request.errorBudgetRatio(), "errorBudgetRatio"));
        entity.setWarningBurnRate(requirePositive(request.warningBurnRate(), "warningBurnRate"));
        entity.setCriticalBurnRate(requirePositive(request.criticalBurnRate(), "criticalBurnRate"));
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setDescription(blankToNull(request.description()));
    }

    private RiskComputation evaluatePolicy(SloPolicyEntity policy, List<RequestLogEntity> requestLogs) {
        List<RequestLogEntity> scopedLogs = requestLogs.stream().filter(entity -> matches(policy, entity)).toList();
        long requestCount = scopedLogs.size();
        long failedCount = scopedLogs.stream().filter(entity -> entity.getStatus() == GatewayRequestStatus.FAILED).count();
        double errorRate = safeRatio(failedCount, requestCount);
        double errorBudgetRatio = policy.getErrorBudgetRatio().doubleValue();
        double burnRate = errorBudgetRatio <= 0D ? 0D : errorRate / errorBudgetRatio;
        double allowedFailures = requestCount * errorBudgetRatio;
        double remainingRatio = requestCount <= 0 || errorBudgetRatio <= 0D
                ? 1D
                : Math.max(0D, (allowedFailures - failedCount) / allowedFailures);
        String riskLevel = riskLevel(burnRate, policy.getWarningBurnRate().doubleValue(), policy.getCriticalBurnRate().doubleValue());

        List<String> suspectedCauses = new ArrayList<>();
        if (requestCount <= 0) {
            suspectedCauses.add("当前窗口没有命中该 scope 的请求，风险判断主要依赖静态 policy。");
        } else if (failedCount > 0) {
            suspectedCauses.add("当前窗口内失败请求抬升，正在快速消耗 error budget。");
            suspectedCauses.add("对应 scope 下的 provider / credential 可能存在容量或稳定性抖动。");
        } else {
            suspectedCauses.add("当前窗口内请求稳定，error budget 仍处于健康区间。");
        }

        List<String> suggestedActions = new ArrayList<>();
        if ("CRITICAL".equals(riskLevel)) {
            suggestedActions.add("立即限制高风险 distributed key 或切换到冗余 provider。");
            suggestedActions.add("优先检查对应 scope 的路由、限额和上游错误模式。");
        } else if ("HIGH".equals(riskLevel)) {
            suggestedActions.add("提高该 scope 的监控频率，并准备限流或摘除动作。");
            suggestedActions.add("核对最近 1 小时内的 request log 与 route decision 是否出现漂移。");
        } else {
            suggestedActions.add("继续观察该 scope 的 error budget 消耗情况。");
        }

        return new RiskComputation(
                policy.getScopeType(),
                normalizeScopeRef(policy),
                policy.getPolicyName(),
                requestCount,
                failedCount,
                errorRate,
                errorBudgetRatio,
                burnRate,
                remainingRatio,
                riskLevel,
                List.copyOf(suspectedCauses),
                List.copyOf(suggestedActions)
        );
    }

    private boolean matches(SloPolicyEntity policy, RequestLogEntity entity) {
        String scopeType = normalizeUpper(policy.getScopeType(), "scopeType");
        String scopeRef = blankToNull(policy.getScopeRef());
        return switch (scopeType) {
            case "GATEWAY" -> true;
            case "DISTRIBUTED_KEY" -> scopeRef != null && scopeRef.equals(String.valueOf(entity.getDistributedKeyId()));
            case "PROVIDER" -> scopeRef != null && entity.getProviderType() != null && scopeRef.equalsIgnoreCase(entity.getProviderType().name());
            case "MODEL_GROUP" -> scopeRef != null && scopeRef.equalsIgnoreCase(entity.getModelGroup());
            case "REQUEST_PATH" -> scopeRef != null && scopeRef.equalsIgnoreCase(entity.getRequestPath());
            default -> true;
        };
    }

    private String normalizeScopeRef(SloPolicyEntity policy) {
        if ("GATEWAY".equalsIgnoreCase(policy.getScopeType())) {
            return policy.getScopeRef() == null ? "global" : policy.getScopeRef();
        }
        return policy.getScopeRef();
    }

    private String riskLevel(double burnRate, double warningBurnRate, double criticalBurnRate) {
        if (burnRate >= criticalBurnRate) {
            return "CRITICAL";
        }
        if (burnRate >= warningBurnRate) {
            return "HIGH";
        }
        return "HEALTHY";
    }

    private int riskRank(String riskLevel) {
        return switch (riskLevel == null ? "HEALTHY" : riskLevel.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 3;
            case "HIGH" -> 2;
            case "DEGRADED" -> 1;
            default -> 0;
        };
    }

    private double safeRatio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return (double) numerator / denominator;
    }

    private String requireText(String value, String fieldName) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " 不能为空。");
        }
        return normalized;
    }

    private String normalizeUpper(String value, String fieldName) {
        return requireText(value, fieldName).trim().toUpperCase(Locale.ROOT);
    }

    private int normalizeWindow(Integer windowMinutes) {
        if (windowMinutes == null || windowMinutes < MIN_WINDOW_MINUTES) {
            return DEFAULT_WINDOW_MINUTES;
        }
        return windowMinutes;
    }

    private BigDecimal requirePositive(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(fieldName + " 必须为正数。");
        }
        return value.setScale(value.scale() > 4 ? 4 : value.scale(), RoundingMode.HALF_UP);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private SloPolicyResponse toResponse(SloPolicyEntity entity) {
        return new SloPolicyResponse(
                entity.getId(),
                entity.getPolicyName(),
                entity.getScopeType(),
                entity.getScopeRef(),
                entity.getWindowMinutes(),
                entity.getErrorBudgetRatio(),
                entity.getWarningBurnRate(),
                entity.getCriticalBurnRate(),
                entity.isEnabled(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private record RiskComputation(
            String scopeType,
            String scopeRef,
            String policyName,
            long requestCount,
            long failedRequestCount,
            double errorRate,
            double errorBudgetRatio,
            double burnRate,
            double errorBudgetRemainingRatio,
            String riskLevel,
            List<String> suspectedCauses,
            List<String> suggestedActions
    ) {
        private OpsSloSummaryResponse.RiskItem toResponse() {
            return new OpsSloSummaryResponse.RiskItem(
                    scopeType,
                    scopeRef,
                    policyName,
                    burnRate,
                    errorBudgetRemainingRatio,
                    riskLevel,
                    suspectedCauses,
                    suggestedActions
            );
        }
    }
}
