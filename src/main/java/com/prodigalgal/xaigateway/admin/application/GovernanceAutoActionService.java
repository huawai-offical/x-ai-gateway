package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.application.integrations.PlatformEventPublisher;
import com.prodigalgal.xaigateway.admin.application.integrations.PlatformEventType;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceActionType;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceRecoveryMode;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceTargetType;
import com.prodigalgal.xaigateway.gateway.core.governance.QuarantineStatus;
import com.prodigalgal.xaigateway.gateway.core.routing.HealthStateStore;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.AutoActionRuleEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.OpsAlertEventEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.QuarantineRecordEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.AutoActionRuleRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.QuarantineRecordRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class GovernanceAutoActionService {

    private final AutoActionRuleRepository autoActionRuleRepository;
    private final QuarantineRecordRepository quarantineRecordRepository;
    private final HealthStateStore healthStateStore;
    private final OpsAuditService opsAuditService;
    private final ObjectMapper objectMapper;
    private final PlatformEventPublisher platformEventPublisher;

    public GovernanceAutoActionService(
            AutoActionRuleRepository autoActionRuleRepository,
            QuarantineRecordRepository quarantineRecordRepository,
            HealthStateStore healthStateStore,
            OpsAuditService opsAuditService,
            ObjectMapper objectMapper,
            PlatformEventPublisher platformEventPublisher) {
        this.autoActionRuleRepository = autoActionRuleRepository;
        this.quarantineRecordRepository = quarantineRecordRepository;
        this.healthStateStore = healthStateStore;
        this.opsAuditService = opsAuditService;
        this.objectMapper = objectMapper;
        this.platformEventPublisher = platformEventPublisher;
    }

    public void handleAlertEvent(OpsAlertEventEntity event) {
        ResolvedTarget target = resolveTarget(event);
        if (target == null) {
            return;
        }

        for (AutoActionRuleEntity rule : autoActionRuleRepository.findAllByEnabledTrueOrderByCreatedAtAsc()) {
            if (!matchesRule(rule, event)) {
                continue;
            }
            applyRule(rule, event, target);
        }
    }

    private boolean matchesRule(AutoActionRuleEntity rule, OpsAlertEventEntity event) {
        if (!rule.getEventType().equalsIgnoreCase(event.getEventType())) {
            return false;
        }
        if (rule.getSeverity() != null && !rule.getSeverity().isBlank()
                && !rule.getSeverity().equalsIgnoreCase(event.getSeverity())) {
            return false;
        }
        return rule.getEntityType() == null
                || rule.getEntityType().isBlank()
                || Objects.equals(rule.getEntityType().trim().toUpperCase(Locale.ROOT), normalizeEntityType(event.getEntityType()));
    }

    private void applyRule(AutoActionRuleEntity rule, OpsAlertEventEntity event, ResolvedTarget target) {
        Instant now = Instant.now();
        Instant expiresAt = rule.getTtlSeconds() == null || rule.getTtlSeconds() <= 0 ? null : now.plusSeconds(rule.getTtlSeconds());
        List<QuarantineRecordEntity> activeRecords = quarantineRecordRepository.findAllByStatusOrderByStartedAtDesc(QuarantineStatus.ACTIVE);
        boolean duplicate = activeRecords.stream()
                .filter(record -> record.getExpiresAt() == null || record.getExpiresAt().isAfter(now))
                .anyMatch(record -> sameTarget(record, target) && record.getActionType() == rule.getActionType());
        if (duplicate) {
            return;
        }

        QuarantineRecordEntity record = null;
        if (rule.getActionType() != GovernanceActionType.NONE) {
            record = new QuarantineRecordEntity();
            record.setTargetType(target.targetType());
            record.setProviderType(target.providerType());
            record.setSiteProfileId(target.siteProfileId());
            record.setCredentialId(target.credentialId());
            record.setAccountId(target.accountId());
            record.setProxyId(target.proxyId());
            record.setSourceRuleId(rule.getId());
            record.setSourceEventId(event.getId());
            record.setActionType(rule.getActionType());
            record.setRecoveryMode(rule.getRecoveryMode());
            record.setReason("告警事件触发自动治理动作：" + event.getTitle());
            record.setStatus(QuarantineStatus.ACTIVE);
            record.setStartedAt(now);
            record.setExpiresAt(expiresAt);
            record = quarantineRecordRepository.save(record);
        }

        if (rule.getActionType() == GovernanceActionType.COOLDOWN && target.credentialId() != null) {
            Duration ttl = rule.getTtlSeconds() == null || rule.getTtlSeconds() <= 0
                    ? Duration.ofMinutes(5)
                    : Duration.ofSeconds(rule.getTtlSeconds());
            healthStateStore.markCooldown(target.credentialId(), "governance-auto-action", ttl);
        }

        opsAuditService.record(
                "GOVERNANCE",
                "AUTO_" + rule.getActionType().name(),
                target.targetType().name(),
                target.resourceRef(),
                writeAuditDetail(rule, event, target, record)
        );
        if (record != null) {
            Map<String, Object> eventDetail = new LinkedHashMap<>();
            eventDetail.put("quarantineId", record.getId());
            eventDetail.put("eventId", event.getId());
            eventDetail.put("actionType", rule.getActionType().name());
            eventDetail.put("reason", record.getReason());
            platformEventPublisher.publish(
                    PlatformEventType.SITE_QUARANTINED,
                    event.getSeverity(),
                    "GOVERNANCE",
                    target.targetType().name(),
                    target.resourceRef(),
                    target.providerType(),
                    target.siteProfileId(),
                    target.credentialId(),
                    target.accountId(),
                    null,
                    null,
                    null,
                    "自动治理已触发 " + rule.getActionType().name(),
                    eventDetail
            );
        }
    }

    private ResolvedTarget resolveTarget(OpsAlertEventEntity event) {
        String entityType = normalizeEntityType(event.getEntityType());
        String entityRef = event.getEntityRef();
        if (entityType == null || entityRef == null || entityRef.isBlank()) {
            return null;
        }
        try {
            return switch (entityType) {
                case "PROVIDER_TYPE" -> new ResolvedTarget(
                        GovernanceTargetType.PROVIDER_TYPE,
                        ProviderType.valueOf(entityRef.trim().toUpperCase(Locale.ROOT)),
                        null,
                        null,
                        null,
                        null,
                        entityRef.trim()
                );
                case "SITE_PROFILE", "UPSTREAM_SITE_PROFILE" -> new ResolvedTarget(
                        GovernanceTargetType.SITE_PROFILE,
                        null,
                        Long.parseLong(entityRef.trim()),
                        null,
                        null,
                        null,
                        entityRef.trim()
                );
                case "CREDENTIAL", "UPSTREAM_CREDENTIAL" -> new ResolvedTarget(
                        GovernanceTargetType.CREDENTIAL,
                        null,
                        null,
                        Long.parseLong(entityRef.trim()),
                        null,
                        null,
                        entityRef.trim()
                );
                case "ACCOUNT", "UPSTREAM_ACCOUNT" -> new ResolvedTarget(
                        GovernanceTargetType.ACCOUNT,
                        null,
                        null,
                        null,
                        Long.parseLong(entityRef.trim()),
                        null,
                        entityRef.trim()
                );
                case "PROXY", "NETWORK_PROXY" -> new ResolvedTarget(
                        GovernanceTargetType.PROXY,
                        null,
                        null,
                        null,
                        null,
                        Long.parseLong(entityRef.trim()),
                        entityRef.trim()
                );
                default -> null;
            };
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean sameTarget(QuarantineRecordEntity record, ResolvedTarget target) {
        return record.getTargetType() == target.targetType()
                && record.getProviderType() == target.providerType()
                && Objects.equals(record.getSiteProfileId(), target.siteProfileId())
                && Objects.equals(record.getCredentialId(), target.credentialId())
                && Objects.equals(record.getAccountId(), target.accountId())
                && Objects.equals(record.getProxyId(), target.proxyId());
    }

    private String writeAuditDetail(
            AutoActionRuleEntity rule,
            OpsAlertEventEntity event,
            ResolvedTarget target,
            QuarantineRecordEntity record) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("ruleId", rule.getId());
            detail.put("eventId", event.getId());
            detail.put("targetType", target.targetType().name());
            detail.put("targetRef", target.resourceRef());
            detail.put("actionType", rule.getActionType().name());
            detail.put("quarantineId", record == null ? null : record.getId());
            return objectMapper.writeValueAsString(detail);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法序列化治理审计详情。", exception);
        }
    }

    private String normalizeEntityType(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private record ResolvedTarget(
            GovernanceTargetType targetType,
            ProviderType providerType,
            Long siteProfileId,
            Long credentialId,
            Long accountId,
            Long proxyId,
            String resourceRef
    ) {
    }
}
