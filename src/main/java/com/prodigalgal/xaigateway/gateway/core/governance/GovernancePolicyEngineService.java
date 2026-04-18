package com.prodigalgal.xaigateway.gateway.core.governance;

import com.prodigalgal.xaigateway.infra.persistence.entity.QuarantineRecordEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RouteGuardPolicyEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.QuarantineRecordRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RouteGuardPolicyRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GovernancePolicyEngineService implements GovernancePolicyEngine {

    private final RouteGuardPolicyRepository routeGuardPolicyRepository;
    private final QuarantineRecordRepository quarantineRecordRepository;

    public GovernancePolicyEngineService(
            RouteGuardPolicyRepository routeGuardPolicyRepository,
            QuarantineRecordRepository quarantineRecordRepository) {
        this.routeGuardPolicyRepository = routeGuardPolicyRepository;
        this.quarantineRecordRepository = quarantineRecordRepository;
    }

    @Override
    public GovernanceDecision evaluate(GovernanceContext context) {
        if (context == null) {
            return GovernanceDecision.allow();
        }

        Instant now = Instant.now();
        List<RouteGuardPolicyEntity> matchingPolicies = routeGuardPolicyRepository
                .findAllByEnabledTrueOrderByPriorityAscCreatedAtAsc()
                .stream()
                .filter(policy -> matchesPolicy(policy, context))
                .filter(policy -> isPolicyActive(policy, now))
                .sorted(Comparator.comparingInt(RouteGuardPolicyEntity::getPriority).thenComparing(RouteGuardPolicyEntity::getCreatedAt))
                .toList();

        RouteGuardPolicyEntity allowOverride = matchingPolicies.stream()
                .filter(policy -> policy.getPolicyMode() == GovernancePolicyMode.OVERRIDE_ALLOW)
                .findFirst()
                .orElse(null);
        if (allowOverride != null) {
            return new GovernanceDecision(
                    true,
                    "OVERRIDE_ALLOWED",
                    "命中人工放行治理规则。",
                    GovernanceActionType.NONE,
                    policyEffectiveUntil(allowOverride),
                    List.of(allowOverride.getId()),
                    List.of()
            );
        }

        List<QuarantineRecordEntity> activeQuarantines = quarantineRecordRepository
                .findAllByStatusOrderByStartedAtDesc(QuarantineStatus.ACTIVE)
                .stream()
                .filter(record -> isQuarantineActive(record, now))
                .filter(record -> matchesQuarantine(record, context))
                .toList();
        if (!activeQuarantines.isEmpty()) {
            QuarantineRecordEntity record = activeQuarantines.getFirst();
            return new GovernanceDecision(
                    false,
                    healthState(record.getActionType(), true),
                    record.getReason(),
                    record.getActionType(),
                    record.getExpiresAt(),
                    matchingPolicies.stream().map(RouteGuardPolicyEntity::getId).toList(),
                    activeQuarantines.stream().map(QuarantineRecordEntity::getId).toList()
            );
        }

        RouteGuardPolicyEntity blockingPolicy = matchingPolicies.stream()
                .filter(policy -> policy.getPolicyMode() != GovernancePolicyMode.OVERRIDE_ALLOW)
                .findFirst()
                .orElse(null);
        if (blockingPolicy == null) {
            return GovernanceDecision.allow();
        }

        return new GovernanceDecision(
                false,
                healthState(blockingPolicy.getActionType(), blockingPolicy.getPolicyMode() == GovernancePolicyMode.OVERRIDE_BLOCK),
                blockingReason(blockingPolicy),
                blockingPolicy.getActionType(),
                policyEffectiveUntil(blockingPolicy),
                matchingPolicies.stream().map(RouteGuardPolicyEntity::getId).toList(),
                List.of()
        );
    }

    private boolean matchesPolicy(RouteGuardPolicyEntity policy, GovernanceContext context) {
        if (policy.getProviderType() != null && policy.getProviderType() != context.providerType()) {
            return false;
        }
        return switch (policy.getTargetType()) {
            case PROVIDER_TYPE -> policy.getProviderType() != null && policy.getProviderType() == context.providerType();
            case SITE_PROFILE -> Objects.equals(policy.getSiteProfileId(), context.siteProfileId());
            case CREDENTIAL -> Objects.equals(policy.getCredentialId(), context.credentialId());
            case ACCOUNT -> Objects.equals(policy.getAccountId(), context.accountId());
            case PROXY -> Objects.equals(policy.getProxyId(), context.proxyId());
        };
    }

    private boolean matchesQuarantine(QuarantineRecordEntity record, GovernanceContext context) {
        if (record.getProviderType() != null && record.getProviderType() != context.providerType()) {
            return false;
        }
        return switch (record.getTargetType()) {
            case PROVIDER_TYPE -> record.getProviderType() != null && record.getProviderType() == context.providerType();
            case SITE_PROFILE -> Objects.equals(record.getSiteProfileId(), context.siteProfileId());
            case CREDENTIAL -> Objects.equals(record.getCredentialId(), context.credentialId());
            case ACCOUNT -> Objects.equals(record.getAccountId(), context.accountId());
            case PROXY -> Objects.equals(record.getProxyId(), context.proxyId());
        };
    }

    private boolean isPolicyActive(RouteGuardPolicyEntity policy, Instant now) {
        Instant effectiveUntil = policyEffectiveUntil(policy);
        return effectiveUntil == null || effectiveUntil.isAfter(now);
    }

    private boolean isQuarantineActive(QuarantineRecordEntity record, Instant now) {
        return record.getExpiresAt() == null || record.getExpiresAt().isAfter(now);
    }

    private Instant policyEffectiveUntil(RouteGuardPolicyEntity policy) {
        if (policy.getTtlSeconds() == null || policy.getTtlSeconds() <= 0 || policy.getUpdatedAt() == null) {
            return null;
        }
        return policy.getUpdatedAt().plusSeconds(policy.getTtlSeconds());
    }

    private String healthState(GovernanceActionType actionType, boolean policyBlocked) {
        if (policyBlocked && actionType == GovernanceActionType.NONE) {
            return "POLICY_BLOCKED";
        }
        return switch (actionType == null ? GovernanceActionType.NONE : actionType) {
            case COOLDOWN -> "COOLDOWN";
            case QUARANTINE, DRAIN -> "QUARANTINED";
            case NONE -> "POLICY_BLOCKED";
        };
    }

    private String blockingReason(RouteGuardPolicyEntity policy) {
        return switch (policy.getPolicyMode()) {
            case OVERRIDE_BLOCK -> "命中人工阻断治理规则。";
            case OVERRIDE_ALLOW -> "命中人工放行治理规则。";
            case ENFORCE -> switch (policy.getActionType()) {
                case COOLDOWN -> "命中临时冷却治理规则。";
                case QUARANTINE, DRAIN -> "命中隔离治理规则。";
                case NONE -> "命中治理阻断规则。";
            };
        };
    }
}
