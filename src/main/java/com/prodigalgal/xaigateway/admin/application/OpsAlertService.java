package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.OpsAlertEventResponse;
import com.prodigalgal.xaigateway.admin.api.AlertSilenceRequest;
import com.prodigalgal.xaigateway.admin.api.AlertSilenceResponse;
import com.prodigalgal.xaigateway.admin.api.OpsAlertRuleRequest;
import com.prodigalgal.xaigateway.admin.api.OpsAlertRuleResponse;
import com.prodigalgal.xaigateway.admin.application.integrations.PlatformEventPublisher;
import com.prodigalgal.xaigateway.admin.application.integrations.PlatformEventType;
import com.prodigalgal.xaigateway.gateway.core.ops.OpsEventBusService;
import com.prodigalgal.xaigateway.gateway.core.ops.OpsEventType;
import com.prodigalgal.xaigateway.infra.persistence.entity.AlertSilenceEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.OpsAlertEventEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.OpsAlertRuleEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.AlertSilenceRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.OpsAlertEventRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.OpsAlertRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class OpsAlertService {

    private final OpsAlertRuleRepository opsAlertRuleRepository;
    private final OpsAlertEventRepository opsAlertEventRepository;
    private final OpsEventBusService opsEventBusService;
    private final GovernanceAutoActionService governanceAutoActionService;
    private final AlertSilenceRepository alertSilenceRepository;
    private final PlatformEventPublisher platformEventPublisher;

    public OpsAlertService(
            OpsAlertRuleRepository opsAlertRuleRepository,
            OpsAlertEventRepository opsAlertEventRepository,
            OpsEventBusService opsEventBusService,
            GovernanceAutoActionService governanceAutoActionService,
            AlertSilenceRepository alertSilenceRepository,
            PlatformEventPublisher platformEventPublisher) {
        this.opsAlertRuleRepository = opsAlertRuleRepository;
        this.opsAlertEventRepository = opsAlertEventRepository;
        this.opsEventBusService = opsEventBusService;
        this.governanceAutoActionService = governanceAutoActionService;
        this.alertSilenceRepository = alertSilenceRepository;
        this.platformEventPublisher = platformEventPublisher;
    }

    @Transactional(readOnly = true)
    public List<OpsAlertRuleResponse> listRules() {
        return opsAlertRuleRepository.findAllByOrderByPriorityAscCreatedAtAsc().stream().map(this::toRuleResponse).toList();
    }

    public OpsAlertRuleResponse saveRule(Long id, OpsAlertRuleRequest request) {
        OpsAlertRuleEntity entity = id == null
                ? new OpsAlertRuleEntity()
                : opsAlertRuleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("未找到告警规则。"));
        entity.setRuleName(request.ruleName());
        entity.setMetricKey(request.metricKey());
        entity.setComparisonOperator(request.comparisonOperator());
        entity.setThresholdValue(request.thresholdValue());
        entity.setSeverity(request.severity());
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setDescription(request.description());
        return toRuleResponse(opsAlertRuleRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<OpsAlertEventResponse> listEvents(String status) {
        if (status == null || status.isBlank()) {
            return opsAlertEventRepository.findTop100ByOrderByCreatedAtDesc().stream().map(this::toEventResponse).toList();
        }
        return opsAlertEventRepository.findTop100ByStatusOrderByCreatedAtDesc(status.toUpperCase()).stream().map(this::toEventResponse).toList();
    }

    public OpsAlertEventResponse acknowledge(Long id) {
        OpsAlertEventEntity entity = opsAlertEventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到告警事件。"));
        entity.setStatus("ACKED");
        entity.setAcknowledgedAt(java.time.Instant.now());
        OpsAlertEventEntity saved = opsAlertEventRepository.save(entity);
        opsEventBusService.publish(OpsEventType.ALERT_EVENT, toEventResponse(saved));
        publishAlertEvent(PlatformEventType.ALERT_ACKED, saved);
        governanceAutoActionService.handleAlertEvent(saved);
        return toEventResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AlertSilenceResponse> listSilences() {
        return alertSilenceRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toSilenceResponse).toList();
    }

    public AlertSilenceResponse saveSilence(Long id, AlertSilenceRequest request) {
        AlertSilenceEntity entity = id == null
                ? new AlertSilenceEntity()
                : alertSilenceRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("未找到 alert silence。"));
        entity.setSilenceName(requireText(request.silenceName(), "silenceName"));
        entity.setEventType(blankToNull(request.eventType()));
        entity.setSeverity(blankToNull(request.severity()));
        entity.setEntityType(blankToNull(request.entityType()));
        entity.setEntityRef(blankToNull(request.entityRef()));
        entity.setStartsAt(request.startsAt());
        entity.setEndsAt(request.endsAt());
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setReason(blankToNull(request.reason()));
        return toSilenceResponse(alertSilenceRepository.save(entity));
    }

    public OpsAlertEventResponse emitEvent(
            String eventType,
            String severity,
            String title,
            String message,
            String entityType,
            String entityRef,
            BigDecimal metricValue) {
        OpsAlertEventEntity entity = new OpsAlertEventEntity();
        entity.setEventType(eventType);
        entity.setSeverity(severity);
        entity.setTitle(title);
        entity.setMessage(message);
        boolean silenced = isSilenced(eventType, severity, entityType, entityRef, Instant.now());
        entity.setStatus(silenced ? "SILENCED" : "OPEN");
        entity.setEntityType(entityType);
        entity.setEntityRef(entityRef);
        entity.setMetricValue(metricValue);
        OpsAlertEventEntity saved = opsAlertEventRepository.save(entity);
        if (!silenced) {
            opsEventBusService.publish(OpsEventType.ALERT_EVENT, toEventResponse(saved));
            publishAlertEvent(PlatformEventType.ALERT_OPENED, saved);
        }
        return toEventResponse(saved);
    }

    public void evaluate(String metricKey, BigDecimal metricValue, String entityType, String entityRef) {
        for (OpsAlertRuleEntity rule : opsAlertRuleRepository.findAllByEnabledTrueOrderByPriorityAscCreatedAtAsc()) {
            if (!rule.getMetricKey().equalsIgnoreCase(metricKey)) {
                continue;
            }
            if (!matches(rule.getComparisonOperator(), metricValue, rule.getThresholdValue())) {
                continue;
            }
            OpsAlertEventEntity entity = new OpsAlertEventEntity();
            entity.setRuleId(rule.getId());
            entity.setEventType(metricKey);
            entity.setSeverity(rule.getSeverity());
            entity.setTitle(rule.getRuleName());
            entity.setMessage(metricKey + " 命中阈值 " + rule.getThresholdValue());
            boolean silenced = isSilenced(metricKey, rule.getSeverity(), entityType, entityRef, Instant.now());
            entity.setStatus(silenced ? "SILENCED" : "OPEN");
            entity.setEntityType(entityType);
            entity.setEntityRef(entityRef);
            entity.setMetricValue(metricValue);
            OpsAlertEventEntity saved = opsAlertEventRepository.save(entity);
            if (!silenced) {
                opsEventBusService.publish(OpsEventType.ALERT_EVENT, toEventResponse(saved));
                publishAlertEvent(PlatformEventType.ALERT_OPENED, saved);
                governanceAutoActionService.handleAlertEvent(saved);
            }
        }
    }

    private void publishAlertEvent(PlatformEventType eventType, OpsAlertEventEntity event) {
        platformEventPublisher.publish(
                eventType,
                event.getSeverity(),
                "OPS_ALERT",
                event.getEntityType(),
                event.getEntityRef(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                event.getTitle(),
                java.util.Map.of(
                        "alertEventId", event.getId(),
                        "message", event.getMessage(),
                        "status", event.getStatus()
                )
        );
    }

    private boolean isSilenced(String eventType, String severity, String entityType, String entityRef, Instant observedAt) {
        return alertSilenceRepository.findAllByEnabledTrueOrderByCreatedAtDesc().stream().anyMatch(silence -> {
            if (silence.getStartsAt() != null && observedAt.isBefore(silence.getStartsAt())) {
                return false;
            }
            if (silence.getEndsAt() != null && observedAt.isAfter(silence.getEndsAt())) {
                return false;
            }
            if (silence.getEventType() != null && !silence.getEventType().equalsIgnoreCase(eventType)) {
                return false;
            }
            if (silence.getSeverity() != null && !silence.getSeverity().equalsIgnoreCase(severity)) {
                return false;
            }
            if (silence.getEntityType() != null && !silence.getEntityType().equalsIgnoreCase(entityType)) {
                return false;
            }
            return silence.getEntityRef() == null || silence.getEntityRef().equalsIgnoreCase(entityRef);
        });
    }

    private boolean matches(String operator, BigDecimal left, BigDecimal right) {
        return switch (operator == null ? ">" : operator.trim()) {
            case ">" -> left.compareTo(right) > 0;
            case ">=" -> left.compareTo(right) >= 0;
            case "<" -> left.compareTo(right) < 0;
            case "<=" -> left.compareTo(right) <= 0;
            case "=" -> left.compareTo(right) == 0;
            default -> false;
        };
    }

    private OpsAlertRuleResponse toRuleResponse(OpsAlertRuleEntity entity) {
        return new OpsAlertRuleResponse(
                entity.getId(),
                entity.getRuleName(),
                entity.getMetricKey(),
                entity.getComparisonOperator(),
                entity.getThresholdValue(),
                entity.getSeverity(),
                entity.isEnabled(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private OpsAlertEventResponse toEventResponse(OpsAlertEventEntity entity) {
        return new OpsAlertEventResponse(
                entity.getId(),
                entity.getRuleId(),
                entity.getEventType(),
                entity.getSeverity(),
                entity.getTitle(),
                entity.getMessage(),
                entity.getStatus(),
                entity.getEntityType(),
                entity.getEntityRef(),
                entity.getMetricValue(),
                entity.getAcknowledgedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private AlertSilenceResponse toSilenceResponse(AlertSilenceEntity entity) {
        return new AlertSilenceResponse(
                entity.getId(),
                entity.getSilenceName(),
                entity.getEventType(),
                entity.getSeverity(),
                entity.getEntityType(),
                entity.getEntityRef(),
                entity.getStartsAt(),
                entity.getEndsAt(),
                entity.isEnabled(),
                entity.getReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String requireText(String value, String fieldName) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " 不能为空。");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
