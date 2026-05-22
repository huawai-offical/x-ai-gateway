package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AutoActionRuleRequest;
import com.prodigalgal.xaigateway.admin.api.AutoActionRuleResponse;
import com.prodigalgal.xaigateway.admin.api.CredentialHealthScoreResponse;
import com.prodigalgal.xaigateway.admin.api.GovernanceHealthScoreResponse;
import com.prodigalgal.xaigateway.admin.api.QuarantineRecordResponse;
import com.prodigalgal.xaigateway.admin.api.RouteGuardPolicyRequest;
import com.prodigalgal.xaigateway.admin.api.RouteGuardPolicyResponse;
import com.prodigalgal.xaigateway.admin.api.RoutingPolicyRuntimePlanResponse;
import com.prodigalgal.xaigateway.admin.api.RoutingPolicyRuntimeStateResponse;
import com.prodigalgal.xaigateway.admin.api.RoutingPolicySummaryResponse;
import com.prodigalgal.xaigateway.admin.api.SiteHealthScoreResponse;
import com.prodigalgal.xaigateway.admin.application.integrations.PlatformEventPublisher;
import com.prodigalgal.xaigateway.admin.application.integrations.PlatformEventType;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceActionType;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceContext;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceDecision;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernancePolicyEngine;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernancePolicyMode;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceRecoveryMode;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceTargetType;
import com.prodigalgal.xaigateway.gateway.core.governance.QuarantineStatus;
import com.prodigalgal.xaigateway.gateway.core.routing.CredentialHealthState;
import com.prodigalgal.xaigateway.gateway.core.routing.HealthStateStore;
import com.prodigalgal.xaigateway.gateway.core.routing.RoutingPolicyRuntimeEnforcementService;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.AutoActionRuleEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.QuarantineRecordEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RouteGuardPolicyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.AutoActionRuleRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.QuarantineRecordRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RouteGuardPolicyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamSiteProfileRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class GovernanceAdminService {

    private final RouteGuardPolicyRepository routeGuardPolicyRepository;
    private final AutoActionRuleRepository autoActionRuleRepository;
    private final QuarantineRecordRepository quarantineRecordRepository;
    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final UpstreamAccountRepository upstreamAccountRepository;
    private final UpstreamSiteProfileRepository upstreamSiteProfileRepository;
    private final HealthStateStore healthStateStore;
    private final GovernancePolicyEngine governancePolicyEngine;
    private final OpsAuditService opsAuditService;
    private final ObjectMapper objectMapper;
    private final PlatformEventPublisher platformEventPublisher;
    private final RoutingPolicyRuntimeConfigService routingPolicyRuntimeConfigService;
    private final RoutingPolicyRuntimeEnforcementService routingPolicyRuntimeEnforcementService;

    @Autowired
    public GovernanceAdminService(
            RouteGuardPolicyRepository routeGuardPolicyRepository,
            AutoActionRuleRepository autoActionRuleRepository,
            QuarantineRecordRepository quarantineRecordRepository,
            UpstreamCredentialRepository upstreamCredentialRepository,
            UpstreamAccountRepository upstreamAccountRepository,
            UpstreamSiteProfileRepository upstreamSiteProfileRepository,
            HealthStateStore healthStateStore,
            GovernancePolicyEngine governancePolicyEngine,
            OpsAuditService opsAuditService,
            ObjectMapper objectMapper,
            PlatformEventPublisher platformEventPublisher,
            RoutingPolicyRuntimeConfigService routingPolicyRuntimeConfigService,
            RoutingPolicyRuntimeEnforcementService routingPolicyRuntimeEnforcementService) {
        this.routeGuardPolicyRepository = routeGuardPolicyRepository;
        this.autoActionRuleRepository = autoActionRuleRepository;
        this.quarantineRecordRepository = quarantineRecordRepository;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.upstreamAccountRepository = upstreamAccountRepository;
        this.upstreamSiteProfileRepository = upstreamSiteProfileRepository;
        this.healthStateStore = healthStateStore;
        this.governancePolicyEngine = governancePolicyEngine;
        this.opsAuditService = opsAuditService;
        this.objectMapper = objectMapper;
        this.platformEventPublisher = platformEventPublisher;
        this.routingPolicyRuntimeConfigService = routingPolicyRuntimeConfigService;
        this.routingPolicyRuntimeEnforcementService = routingPolicyRuntimeEnforcementService;
    }

    public GovernanceAdminService(
            RouteGuardPolicyRepository routeGuardPolicyRepository,
            AutoActionRuleRepository autoActionRuleRepository,
            QuarantineRecordRepository quarantineRecordRepository,
            UpstreamCredentialRepository upstreamCredentialRepository,
            UpstreamSiteProfileRepository upstreamSiteProfileRepository,
            HealthStateStore healthStateStore,
            GovernancePolicyEngine governancePolicyEngine,
            OpsAuditService opsAuditService,
            ObjectMapper objectMapper,
            PlatformEventPublisher platformEventPublisher) {
        this(
                routeGuardPolicyRepository,
                autoActionRuleRepository,
                quarantineRecordRepository,
                upstreamCredentialRepository,
                null,
                upstreamSiteProfileRepository,
                healthStateStore,
                governancePolicyEngine,
                opsAuditService,
                objectMapper,
                platformEventPublisher,
                null,
                null
        );
    }

    @Transactional(readOnly = true)
    public List<RouteGuardPolicyResponse> listRouteGuards() {
        return routeGuardPolicyRepository.findAllByOrderByPriorityAscCreatedAtAsc().stream()
                .map(this::toRouteGuardResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoutingPolicySummaryResponse routingPolicySummary() {
        List<RouteGuardPolicyEntity> entities = routeGuardPolicyRepository.findAllByOrderByPriorityAscCreatedAtAsc();
        List<RouteGuardPolicyResponse> policies = entities.stream()
                .map(this::toRouteGuardResponse)
                .toList();
        return new RoutingPolicySummaryResponse(
                policies.size(),
                (int) entities.stream().filter(RouteGuardPolicyEntity::isEnabled).count(),
                countConfigured(entities, RouteGuardPolicyEntity::getRetryPolicy),
                countConfigured(entities, RouteGuardPolicyEntity::getFallbackPolicy),
                countConfigured(entities, RouteGuardPolicyEntity::getCircuitBreakerPolicy),
                countConfigured(entities, RouteGuardPolicyEntity::getRateLimitPolicy),
                policies
        );
    }

    @Transactional(readOnly = true)
    public RoutingPolicyRuntimePlanResponse routingRuntimePlan() {
        if (routingPolicyRuntimeConfigService == null) {
            return new RoutingPolicyRuntimePlanResponse(
                    3,
                    false,
                    List.of(),
                    false,
                    null,
                    false,
                    null,
                    List.of(),
                    List.of("RoutingPolicyRuntimeConfigService 未启用，返回默认 fallback 尝试次数。")
            );
        }
        return routingPolicyRuntimeConfigService.runtimePlan(3);
    }

    @Transactional(readOnly = true)
    public List<RoutingPolicyRuntimeStateResponse> routingRuntimeStates() {
        if (routingPolicyRuntimeEnforcementService == null) {
            return List.of();
        }
        return routingPolicyRuntimeEnforcementService.states();
    }

    public void resetRoutingRuntimeStates() {
        if (routingPolicyRuntimeEnforcementService != null) {
            routingPolicyRuntimeEnforcementService.reset();
        }
    }

    public void resetRoutingRuntimeStates(String runtimeKey, Long policyId, String targetRef) {
        if (routingPolicyRuntimeEnforcementService == null) {
            return;
        }
        String resolvedRuntimeKey = resolveRuntimeKey(runtimeKey, policyId, targetRef);
        if (resolvedRuntimeKey == null) {
            routingPolicyRuntimeEnforcementService.reset();
            return;
        }
        routingPolicyRuntimeEnforcementService.reset(resolvedRuntimeKey);
    }

    private String resolveRuntimeKey(String runtimeKey, Long policyId, String targetRef) {
        if (runtimeKey != null && !runtimeKey.isBlank()) {
            return runtimeKey.trim();
        }
        if (policyId != null && targetRef != null && !targetRef.isBlank()) {
            return "policy:" + policyId + ":" + targetRef.trim();
        }
        return null;
    }

    public RouteGuardPolicyResponse saveRouteGuard(Long id, RouteGuardPolicyRequest request) {
        validateTarget(request.targetType(), request.providerType(), request.siteProfileId(), request.credentialId(), request.accountId(), request.proxyId());
        RouteGuardPolicyEntity entity = id == null
                ? new RouteGuardPolicyEntity()
                : routeGuardPolicyRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("未找到治理规则。"));
        entity.setPolicyName(request.policyName().trim());
        entity.setTargetType(request.targetType());
        entity.setProviderType(request.providerType());
        entity.setSiteProfileId(request.siteProfileId());
        entity.setCredentialId(request.credentialId());
        entity.setAccountId(request.accountId());
        entity.setProxyId(request.proxyId());
        entity.setPolicyMode(request.policyMode());
        entity.setActionType(request.actionType());
        entity.setTtlSeconds(normalizePositive(request.ttlSeconds()));
        entity.setPriority(request.priority() == null ? 100 : request.priority());
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setDescription(blankToNull(request.description()));
        entity.setRetryPolicy(blankToNull(request.retryPolicy()));
        entity.setFallbackPolicy(blankToNull(request.fallbackPolicy()));
        entity.setCircuitBreakerPolicy(blankToNull(request.circuitBreakerPolicy()));
        entity.setRateLimitPolicy(blankToNull(request.rateLimitPolicy()));
        RouteGuardPolicyEntity saved = routeGuardPolicyRepository.save(entity);
        opsAuditService.record("GOVERNANCE", id == null ? "ROUTE_GUARD_CREATED" : "ROUTE_GUARD_UPDATED", "ROUTE_GUARD", String.valueOf(saved.getId()), writeAuditDetail(Map.of(
                "targetType", saved.getTargetType().name(),
                "policyMode", saved.getPolicyMode().name(),
                "actionType", saved.getActionType().name(),
                "retryPolicyConfigured", saved.getRetryPolicy() != null,
                "fallbackPolicyConfigured", saved.getFallbackPolicy() != null,
                "circuitBreakerPolicyConfigured", saved.getCircuitBreakerPolicy() != null,
                "rateLimitPolicyConfigured", saved.getRateLimitPolicy() != null
        )));
        return toRouteGuardResponse(saved);
    }

    public void deleteRouteGuard(Long id) {
        RouteGuardPolicyEntity entity = routeGuardPolicyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到治理规则。"));
        routeGuardPolicyRepository.delete(entity);
        opsAuditService.record("GOVERNANCE", "ROUTE_GUARD_DELETED", "ROUTE_GUARD", String.valueOf(id), null);
    }

    @Transactional(readOnly = true)
    public List<AutoActionRuleResponse> listAutoActions() {
        return autoActionRuleRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toAutoActionResponse)
                .toList();
    }

    public AutoActionRuleResponse saveAutoAction(Long id, AutoActionRuleRequest request) {
        AutoActionRuleEntity entity = id == null
                ? new AutoActionRuleEntity()
                : autoActionRuleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("未找到自动动作规则。"));
        entity.setRuleName(request.ruleName().trim());
        entity.setEventType(request.eventType().trim().toUpperCase(Locale.ROOT));
        entity.setSeverity(blankToNull(normalizeUpper(request.severity())));
        entity.setEntityType(blankToNull(normalizeUpper(request.entityType())));
        entity.setActionType(request.actionType());
        entity.setTtlSeconds(normalizePositive(request.ttlSeconds()));
        entity.setRecoveryMode(request.recoveryMode() == null ? GovernanceRecoveryMode.AUTO_RESUME : request.recoveryMode());
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setDescription(blankToNull(request.description()));
        AutoActionRuleEntity saved = autoActionRuleRepository.save(entity);
        opsAuditService.record("GOVERNANCE", id == null ? "AUTO_ACTION_CREATED" : "AUTO_ACTION_UPDATED", "AUTO_ACTION_RULE", String.valueOf(saved.getId()), writeAuditDetail(Map.of(
                "eventType", saved.getEventType(),
                "actionType", saved.getActionType().name()
        )));
        return toAutoActionResponse(saved);
    }

    public void deleteAutoAction(Long id) {
        AutoActionRuleEntity entity = autoActionRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到自动动作规则。"));
        autoActionRuleRepository.delete(entity);
        opsAuditService.record("GOVERNANCE", "AUTO_ACTION_DELETED", "AUTO_ACTION_RULE", String.valueOf(id), null);
    }

    @Transactional(readOnly = true)
    public List<QuarantineRecordResponse> listQuarantines(String status) {
        expireStaleQuarantines();
        if (status == null || status.isBlank()) {
            return quarantineRecordRepository.findAllByOrderByStartedAtDesc().stream()
                    .map(this::toQuarantineResponse)
                    .toList();
        }
        return quarantineRecordRepository.findAllByStatusOrderByStartedAtDesc(QuarantineStatus.valueOf(status.trim().toUpperCase(Locale.ROOT))).stream()
                .map(this::toQuarantineResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GovernanceHealthScoreResponse listHealthScores() {
        expireStaleQuarantines();
        Instant now = Instant.now();

        List<CredentialHealthScoreResponse> credentialScores = java.util.stream.Stream.concat(
                        upstreamCredentialRepository.findAllByDeletedFalseOrderByCreatedAtDesc().stream()
                                .map(credential -> toCredentialHealthScore(credential, now)),
                        accountHealthScores(now)
                )
                .sorted(Comparator
                        .comparingInt(CredentialHealthScoreResponse::score)
                        .thenComparing(CredentialHealthScoreResponse::credentialName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        Map<Long, List<CredentialHealthScoreResponse>> credentialScoresBySite = credentialScores.stream()
                .filter(item -> item.siteProfileId() != null)
                .collect(java.util.stream.Collectors.groupingBy(CredentialHealthScoreResponse::siteProfileId));

        List<SiteHealthScoreResponse> siteScores = upstreamSiteProfileRepository.findAll().stream()
                .sorted(Comparator.comparing(UpstreamSiteProfileEntity::getDisplayName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(site -> toSiteHealthScore(site, credentialScoresBySite.getOrDefault(site.getId(), List.of()), now))
                .toList();

        return new GovernanceHealthScoreResponse(siteScores, credentialScores);
    }

    public QuarantineRecordResponse releaseQuarantine(Long id, String releaseReason) {
        QuarantineRecordEntity entity = quarantineRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到隔离记录。"));
        entity.setStatus(QuarantineStatus.RELEASED);
        entity.setReleasedAt(Instant.now());
        entity.setReleaseReason(blankToNull(releaseReason) == null ? "manual-release" : releaseReason.trim());
        if (entity.getCredentialId() != null) {
            healthStateStore.clear(entity.getCredentialId());
        }
        QuarantineRecordEntity saved = quarantineRecordRepository.save(entity);
        opsAuditService.record("GOVERNANCE", "QUARANTINE_RELEASED", entity.getTargetType().name(), targetRef(entity), writeAuditDetail(Map.of(
                "quarantineId", saved.getId(),
                "releaseReason", saved.getReleaseReason()
        )));
        platformEventPublisher.publish(
                PlatformEventType.SITE_RESUMED,
                "INFO",
                "GOVERNANCE",
                entity.getTargetType().name(),
                targetRef(entity),
                entity.getProviderType(),
                entity.getSiteProfileId(),
                entity.getCredentialId(),
                entity.getAccountId(),
                null,
                null,
                null,
                entity.getTargetType().name() + " 已恢复",
                Map.of(
                        "quarantineId", saved.getId(),
                        "releaseReason", saved.getReleaseReason()
                )
        );
        return toQuarantineResponse(saved);
    }

    private void validateTarget(
            GovernanceTargetType targetType,
            com.prodigalgal.xaigateway.gateway.core.shared.ProviderType providerType,
            Long siteProfileId,
            Long credentialId,
            Long accountId,
            Long proxyId) {
        if (targetType == null) {
            throw new IllegalArgumentException("targetType 不能为空。");
        }
        switch (targetType) {
            case PROVIDER_TYPE -> {
                if (providerType == null) {
                    throw new IllegalArgumentException("PROVIDER_TYPE 规则必须指定 providerType。");
                }
            }
            case SITE_PROFILE -> requireId(siteProfileId, "SITE_PROFILE 规则必须指定 siteProfileId。");
            case CREDENTIAL -> requireId(credentialId, "CREDENTIAL 规则必须指定 credentialId。");
            case ACCOUNT -> requireId(accountId, "ACCOUNT 规则必须指定 accountId。");
            case PROXY -> requireId(proxyId, "PROXY 规则必须指定 proxyId。");
        }
    }

    private void requireId(Long id, String message) {
        if (id == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private void expireStaleQuarantines() {
        Instant now = Instant.now();
        quarantineRecordRepository.findAllByStatusOrderByStartedAtDesc(QuarantineStatus.ACTIVE).stream()
                .filter(record -> record.getExpiresAt() != null && !record.getExpiresAt().isAfter(now))
                .forEach(record -> {
                    record.setStatus(QuarantineStatus.EXPIRED);
                    if (record.getCredentialId() != null) {
                        healthStateStore.clear(record.getCredentialId());
                    }
                    quarantineRecordRepository.save(record);
                });
    }

    private CredentialHealthScoreResponse toCredentialHealthScore(UpstreamCredentialEntity credential, Instant now) {
        String healthState = "HEALTHY";
        String reason = null;
        int score = 100;
        Instant effectiveUntil = null;
        List<Long> matchedPolicyIds = List.of();
        List<Long> matchedQuarantineIds = List.of();

        if (!credential.isActive()) {
            healthState = "INACTIVE";
            reason = "凭证已停用。";
            score = 0;
        } else {
            Optional<CredentialHealthState> runtimeHealth = healthStateStore.getCredentialState(credential.getId())
                    .filter(state -> state.cooldownUntil() != null && state.cooldownUntil().isAfter(now));

            GovernanceDecision decision = governancePolicyEngine.evaluate(new GovernanceContext(
                    credential.getProviderType(),
                    credential.getSiteProfileId(),
                    credential.getId(),
                    null,
                    credential.getProxyId()
            ));

            if (!decision.allowed()) {
                healthState = nullToDefault(decision.healthState(), "POLICY_BLOCKED");
                reason = nullToDefault(decision.reason(), "命中治理规则。");
                effectiveUntil = decision.effectiveUntil();
                matchedPolicyIds = decision.matchedPolicyIds();
                matchedQuarantineIds = decision.matchedQuarantineIds();
                score = scoreForState(healthState);
            } else if (runtimeHealth.isPresent()) {
                healthState = nullToDefault(runtimeHealth.get().state(), "COOLDOWN");
                reason = nullToDefault(runtimeHealth.get().reason(), "凭证处于运行时冷却。");
                effectiveUntil = runtimeHealth.get().cooldownUntil();
                score = scoreForState(healthState);
            } else if (credential.getCooldownUntil() != null && credential.getCooldownUntil().isAfter(now)) {
                healthState = "COOLDOWN";
                reason = "凭证处于持久化冷却期。";
                effectiveUntil = credential.getCooldownUntil();
                score = scoreForState(healthState);
            }
        }

        return new CredentialHealthScoreResponse(
                "API_KEY",
                credential.getId(),
                credential.getId(),
                null,
                credential.getCredentialName(),
                credential.getCredentialName(),
                credential.getProviderType(),
                credential.getSiteProfileId(),
                credential.getProxyId(),
                credential.isActive(),
                null,
                score,
                healthState,
                reason,
                effectiveUntil,
                credential.getLastUsedAt(),
                matchedPolicyIds,
                matchedQuarantineIds
        );
    }

    private java.util.stream.Stream<CredentialHealthScoreResponse> accountHealthScores(Instant now) {
        if (upstreamAccountRepository == null) {
            return java.util.stream.Stream.empty();
        }
        return upstreamAccountRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(account -> toAccountHealthScore(account, now));
    }

    private CredentialHealthScoreResponse toAccountHealthScore(UpstreamAccountEntity account, Instant now) {
        String healthState = "HEALTHY";
        String reason = null;
        int score = 100;
        Instant effectiveUntil = null;
        List<Long> matchedPolicyIds = List.of();
        List<Long> matchedQuarantineIds = List.of();

        if (!account.isActive()) {
            healthState = "INACTIVE";
            reason = "账号已停用。";
            score = 0;
        } else if (account.isFrozen()) {
            healthState = "QUARANTINED";
            reason = "账号已冻结，不参与路由。";
            score = scoreForState(healthState);
        } else if (!account.isHealthy()) {
            healthState = "DEGRADED";
            reason = nullToDefault(account.getLastErrorMessage(), "账号健康状态异常。");
            score = scoreForState(healthState);
        } else {
            GovernanceDecision decision = governancePolicyEngine.evaluate(new GovernanceContext(
                    account.getProviderType() == null ? null : account.getProviderType().routeProviderType(),
                    account.getSiteProfileId(),
                    null,
                    account.getId(),
                    account.getProxyId()
            ));

            if (!decision.allowed()) {
                healthState = nullToDefault(decision.healthState(), "POLICY_BLOCKED");
                reason = nullToDefault(decision.reason(), "命中治理规则。");
                effectiveUntil = decision.effectiveUntil();
                matchedPolicyIds = decision.matchedPolicyIds();
                matchedQuarantineIds = decision.matchedQuarantineIds();
                score = scoreForState(healthState);
            } else if (account.getCooldownUntil() != null && account.getCooldownUntil().isAfter(now)) {
                healthState = "COOLDOWN";
                reason = "账号处于冷却期。";
                effectiveUntil = account.getCooldownUntil();
                score = scoreForState(healthState);
            }
        }

        return new CredentialHealthScoreResponse(
                "AUTH_JSON_ACCOUNT",
                account.getId(),
                null,
                account.getId(),
                account.getAccountName(),
                account.getAccountName(),
                account.getProviderType() == null ? null : account.getProviderType().routeProviderType(),
                account.getSiteProfileId(),
                account.getProxyId(),
                account.isActive(),
                account.isFrozen(),
                score,
                healthState,
                reason,
                effectiveUntil,
                account.getLastUsedAt(),
                matchedPolicyIds,
                matchedQuarantineIds
        );
    }

    private SiteHealthScoreResponse toSiteHealthScore(
            UpstreamSiteProfileEntity site,
            List<CredentialHealthScoreResponse> credentialScores,
            Instant now) {
        if (!site.isActive()) {
            return new SiteHealthScoreResponse(
                    site.getId(),
                    site.getProfileCode(),
                    site.getDisplayName(),
                    site.getProviderFamily(),
                    site.getSiteKind(),
                    false,
                    0,
                    "INACTIVE",
                    "站点档案已停用。",
                    0,
                    0,
                    null
            );
        }

        GovernanceDecision siteDecision = resolveSiteDecision(site, credentialScores);
        if (!siteDecision.allowed()) {
            return new SiteHealthScoreResponse(
                    site.getId(),
                    site.getProfileCode(),
                    site.getDisplayName(),
                    site.getProviderFamily(),
                    site.getSiteKind(),
                    true,
                    scoreForState(siteDecision.healthState()),
                    nullToDefault(siteDecision.healthState(), "POLICY_BLOCKED"),
                    nullToDefault(siteDecision.reason(), "命中站点治理规则。"),
                    activeCredentialCount(credentialScores),
                    blockedCredentialCount(credentialScores),
                    siteDecision.effectiveUntil()
            );
        }

        int activeCredentialCount = activeCredentialCount(credentialScores);
        int blockedCredentialCount = blockedCredentialCount(credentialScores);
        if (activeCredentialCount == 0) {
            return new SiteHealthScoreResponse(
                    site.getId(),
                    site.getProfileCode(),
                    site.getDisplayName(),
                    site.getProviderFamily(),
                    site.getSiteKind(),
                    true,
                    0,
                    "NO_ACTIVE_CREDENTIAL",
                    "站点下没有启用中的凭证。",
                    0,
                    0,
                    null
            );
        }

        int averageScore = (int) Math.round(credentialScores.stream()
                .filter(CredentialHealthScoreResponse::active)
                .mapToInt(CredentialHealthScoreResponse::score)
                .average()
                .orElse(0d));

        String healthState;
        String reason;
        if (blockedCredentialCount == 0) {
            healthState = "HEALTHY";
            reason = "站点下的启用凭证均可参与路由。";
        } else if (blockedCredentialCount >= activeCredentialCount) {
            healthState = "QUARANTINED";
            reason = "站点下所有启用凭证当前都被治理阻断或冷却。";
        } else {
            healthState = "DEGRADED";
            reason = "站点下存在被治理阻断或冷却的凭证。";
        }

        Instant effectiveUntil = credentialScores.stream()
                .filter(CredentialHealthScoreResponse::active)
                .filter(item -> !"HEALTHY".equals(item.healthState()))
                .map(CredentialHealthScoreResponse::effectiveUntil)
                .filter(Objects::nonNull)
                .filter(value -> value.isAfter(now))
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new SiteHealthScoreResponse(
                site.getId(),
                site.getProfileCode(),
                site.getDisplayName(),
                site.getProviderFamily(),
                site.getSiteKind(),
                true,
                averageScore,
                healthState,
                reason,
                activeCredentialCount,
                blockedCredentialCount,
                effectiveUntil
        );
    }

    private GovernanceDecision resolveSiteDecision(UpstreamSiteProfileEntity site, List<CredentialHealthScoreResponse> credentialScores) {
        GovernanceDecision genericDecision = governancePolicyEngine.evaluate(new GovernanceContext(null, site.getId(), null, null, null));
        if (!genericDecision.allowed()) {
            return genericDecision;
        }
        return credentialScores.stream()
                .map(CredentialHealthScoreResponse::providerType)
                .filter(Objects::nonNull)
                .distinct()
                .map(providerType -> governancePolicyEngine.evaluate(new GovernanceContext(providerType, site.getId(), null, null, null)))
                .filter(decision -> !decision.allowed())
                .findFirst()
                .orElse(GovernanceDecision.allow());
    }

    private int activeCredentialCount(List<CredentialHealthScoreResponse> credentialScores) {
        return (int) credentialScores.stream()
                .filter(CredentialHealthScoreResponse::active)
                .count();
    }

    private int blockedCredentialCount(List<CredentialHealthScoreResponse> credentialScores) {
        return (int) credentialScores.stream()
                .filter(CredentialHealthScoreResponse::active)
                .filter(item -> !"HEALTHY".equals(item.healthState()))
                .count();
    }

    private int scoreForState(String healthState) {
        if (healthState == null) {
            return 100;
        }
        return switch (healthState) {
            case "HEALTHY" -> 100;
            case "DEGRADED" -> 50;
            case "COOLDOWN" -> 25;
            case "POLICY_BLOCKED", "QUARANTINED", "INACTIVE", "NO_ACTIVE_CREDENTIAL" -> 0;
            default -> 0;
        };
    }

    private RouteGuardPolicyResponse toRouteGuardResponse(RouteGuardPolicyEntity entity) {
        Instant effectiveUntil = entity.getTtlSeconds() == null || entity.getTtlSeconds() <= 0
                ? null
                : entity.getUpdatedAt().plusSeconds(entity.getTtlSeconds());
        return new RouteGuardPolicyResponse(
                entity.getId(),
                entity.getPolicyName(),
                entity.getTargetType(),
                entity.getProviderType(),
                entity.getSiteProfileId(),
                entity.getCredentialId(),
                entity.getAccountId(),
                entity.getProxyId(),
                entity.getPolicyMode(),
                entity.getActionType(),
                entity.getTtlSeconds(),
                effectiveUntil,
                entity.getPriority(),
                entity.isEnabled(),
                entity.getDescription(),
                entity.getRetryPolicy(),
                entity.getFallbackPolicy(),
                entity.getCircuitBreakerPolicy(),
                entity.getRateLimitPolicy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private AutoActionRuleResponse toAutoActionResponse(AutoActionRuleEntity entity) {
        return new AutoActionRuleResponse(
                entity.getId(),
                entity.getRuleName(),
                entity.getEventType(),
                entity.getSeverity(),
                entity.getEntityType(),
                entity.getActionType(),
                entity.getTtlSeconds(),
                entity.getRecoveryMode(),
                entity.isEnabled(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private QuarantineRecordResponse toQuarantineResponse(QuarantineRecordEntity entity) {
        return new QuarantineRecordResponse(
                entity.getId(),
                entity.getTargetType(),
                entity.getProviderType(),
                entity.getSiteProfileId(),
                entity.getCredentialId(),
                entity.getAccountId(),
                entity.getProxyId(),
                entity.getSourceRuleId(),
                entity.getSourceEventId(),
                entity.getActionType(),
                entity.getRecoveryMode(),
                entity.getReason(),
                entity.getStatus(),
                entity.getStartedAt(),
                entity.getExpiresAt(),
                entity.getReleasedAt(),
                entity.getReleaseReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private Integer normalizePositive(Integer value) {
        if (value == null || value <= 0) {
            return null;
        }
        return value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int countConfigured(List<RouteGuardPolicyEntity> entities, java.util.function.Function<RouteGuardPolicyEntity, String> extractor) {
        return (int) entities.stream()
                .map(extractor)
                .filter(value -> value != null && !value.isBlank())
                .count();
    }

    private String normalizeUpper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String nullToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String targetRef(QuarantineRecordEntity entity) {
        return switch (entity.getTargetType()) {
            case PROVIDER_TYPE -> entity.getProviderType() == null ? "-" : entity.getProviderType().name();
            case SITE_PROFILE -> String.valueOf(entity.getSiteProfileId());
            case CREDENTIAL -> String.valueOf(entity.getCredentialId());
            case ACCOUNT -> String.valueOf(entity.getAccountId());
            case PROXY -> String.valueOf(entity.getProxyId());
        };
    }

    private String writeAuditDetail(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法序列化治理审计详情。", exception);
        }
    }
}
