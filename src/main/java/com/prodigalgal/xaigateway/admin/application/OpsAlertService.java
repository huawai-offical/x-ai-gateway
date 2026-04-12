package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.OpsAlertEventResponse;
import com.prodigalgal.xaigateway.admin.api.OpsAlertRuleRequest;
import com.prodigalgal.xaigateway.admin.api.OpsAlertRuleResponse;
import com.prodigalgal.xaigateway.gateway.core.ops.OpsEventBusService;
import com.prodigalgal.xaigateway.gateway.core.ops.OpsEventType;
import com.prodigalgal.xaigateway.infra.persistence.entity.OpsAlertEventEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.OpsAlertRuleEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.OpsAlertEventRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.OpsAlertRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class OpsAlertService {

    private final OpsAlertRuleRepository opsAlertRuleRepository;
    private final OpsAlertEventRepository opsAlertEventRepository;
    private final OpsEventBusService opsEventBusService;

    public OpsAlertService(
            OpsAlertRuleRepository opsAlertRuleRepository,
            OpsAlertEventRepository opsAlertEventRepository,
            OpsEventBusService opsEventBusService) {
        this.opsAlertRuleRepository = opsAlertRuleRepository;
        this.opsAlertEventRepository = opsAlertEventRepository;
        this.opsEventBusService = opsEventBusService;
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
        return toEventResponse(saved);
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
        entity.setStatus("OPEN");
        entity.setEntityType(entityType);
        entity.setEntityRef(entityRef);
        entity.setMetricValue(metricValue);
        OpsAlertEventEntity saved = opsAlertEventRepository.save(entity);
        opsEventBusService.publish(OpsEventType.ALERT_EVENT, toEventResponse(saved));
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
            entity.setStatus("OPEN");
            entity.setEntityType(entityType);
            entity.setEntityRef(entityRef);
            entity.setMetricValue(metricValue);
            OpsAlertEventEntity saved = opsAlertEventRepository.save(entity);
            opsEventBusService.publish(OpsEventType.ALERT_EVENT, toEventResponse(saved));
        }
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
}
