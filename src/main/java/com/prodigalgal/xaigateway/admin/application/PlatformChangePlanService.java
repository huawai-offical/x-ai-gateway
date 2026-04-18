package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.*;
import com.prodigalgal.xaigateway.admin.application.integrations.PlatformEventPublisher;
import com.prodigalgal.xaigateway.admin.application.integrations.PlatformEventType;
import com.prodigalgal.xaigateway.admin.application.operations.ChangePlanExecutionClass;
import com.prodigalgal.xaigateway.admin.application.operations.ChangePlanStatus;
import com.prodigalgal.xaigateway.admin.application.operations.ChangePlanType;
import com.prodigalgal.xaigateway.admin.application.operations.ChangePreflightCheck;
import com.prodigalgal.xaigateway.admin.application.operations.ChangePreflightResult;
import com.prodigalgal.xaigateway.infra.persistence.entity.ChangePlanEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.ReleaseRolloutEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.ChangePlanRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.ReleaseRolloutRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
@Transactional
public class PlatformChangePlanService {

    private final ChangePlanRepository changePlanRepository;
    private final ReleaseRolloutRepository releaseRolloutRepository;
    private final ObjectMapper objectMapper;
    private final ChangeApprovalService changeApprovalService;
    private final ChangePreflightService changePreflightService;
    private final ReleaseRolloutService releaseRolloutService;
    private final RollbackPlaybookService rollbackPlaybookService;
    private final RecoveryCheckpointService recoveryCheckpointService;
    private final OpsAuditService opsAuditService;
    private final PlatformEventPublisher platformEventPublisher;

    public PlatformChangePlanService(
            ChangePlanRepository changePlanRepository,
            ReleaseRolloutRepository releaseRolloutRepository,
            ObjectMapper objectMapper,
            ChangeApprovalService changeApprovalService,
            ChangePreflightService changePreflightService,
            ReleaseRolloutService releaseRolloutService,
            RollbackPlaybookService rollbackPlaybookService,
            RecoveryCheckpointService recoveryCheckpointService,
            OpsAuditService opsAuditService,
            PlatformEventPublisher platformEventPublisher) {
        this.changePlanRepository = changePlanRepository;
        this.releaseRolloutRepository = releaseRolloutRepository;
        this.objectMapper = objectMapper;
        this.changeApprovalService = changeApprovalService;
        this.changePreflightService = changePreflightService;
        this.releaseRolloutService = releaseRolloutService;
        this.rollbackPlaybookService = rollbackPlaybookService;
        this.recoveryCheckpointService = recoveryCheckpointService;
        this.opsAuditService = opsAuditService;
        this.platformEventPublisher = platformEventPublisher;
    }

    @Transactional(readOnly = true)
    public List<ChangePlanResponse> list() {
        return changePlanRepository.findTop200ByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ChangePlanResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    public ChangePlanResponse create(ChangePlanRequest request) {
        ChangePlanEntity entity = new ChangePlanEntity();
        entity.setPlanName(request.planName());
        entity.setPlanType(ChangePlanType.valueOf(request.planType()).name());
        entity.setExecutionClass(ChangePlanExecutionClass.valueOf(request.executionClass()).name());
        entity.setReleaseArtifactId(request.releaseArtifactId());
        entity.setRecoveryCheckpointId(request.recoveryCheckpointId());
        entity.setMaintenanceWindowId(request.maintenanceWindowId());
        entity.setRequestedBy(defaultActor(request.requestedBy()));
        entity.setManualOverride(Boolean.TRUE.equals(request.manualOverride()));
        entity.setOverrideReason(request.overrideReason());
        entity.setEmergencyReason(request.emergencyReason());
        entity.setStatus(initialStatus(entity));
        entity = changePlanRepository.save(entity);
        if (ChangePlanType.UPGRADE.name().equals(entity.getPlanType())) {
            RollbackPlaybookResponse playbook = rollbackPlaybookService.ensureForUpgradePlan(entity.getId());
            entity.setRollbackPlaybookId(playbook.id());
            entity = changePlanRepository.save(entity);
        }
        if (ChangePlanExecutionClass.AUTO_TRIGGERED.name().equals(entity.getExecutionClass())) {
            entity.setApprovedBy("SYSTEM");
            changeApprovalService.recordSystemApproval(entity, "自动触发变更，按系统审批记录执行。");
            entity = changePlanRepository.save(entity);
        }
        opsAuditService.record("OPERATIONS", "CHANGE_PLAN_CREATED", "change_plan", String.valueOf(entity.getId()), writeJson(entity));
        return toResponse(entity);
    }

    public ChangePlanResponse approve(Long id, ChangePlanApproveRequest request) {
        ChangePlanEntity entity = getEntity(id);
        entity.setStatus(ChangePlanStatus.APPROVED.name());
        entity.setApprovedBy(defaultActor(request.approvedBy()));
        changeApprovalService.approve(entity, entity.getApprovedBy(), request.reason());
        opsAuditService.record("OPERATIONS", "CHANGE_PLAN_APPROVED", "change_plan", String.valueOf(entity.getId()), request.reason());
        return toResponse(changePlanRepository.save(entity));
    }

    public ChangePlanResponse reject(Long id, ChangePlanRejectRequest request) {
        ChangePlanEntity entity = getEntity(id);
        entity.setStatus(ChangePlanStatus.REJECTED.name());
        changeApprovalService.reject(entity, defaultActor(request.rejectedBy()), request.reason());
        opsAuditService.record("OPERATIONS", "CHANGE_PLAN_REJECTED", "change_plan", String.valueOf(entity.getId()), request.reason());
        return toResponse(changePlanRepository.save(entity));
    }

    public ChangePlanResponse cancel(Long id, ChangePlanCancelRequest request) {
        ChangePlanEntity entity = getEntity(id);
        entity.setStatus(ChangePlanStatus.CANCELED.name());
        entity.setCurrentMessage(request.reason());
        opsAuditService.record("OPERATIONS", "CHANGE_PLAN_CANCELED", "change_plan", String.valueOf(entity.getId()), request.reason());
        return toResponse(changePlanRepository.save(entity));
    }

    public ChangePlanResponse execute(Long id, ChangePlanExecuteRequest request) {
        return executeInternal(getEntity(id), request, true);
    }

    private ChangePlanResponse executeInternal(ChangePlanEntity entity, ChangePlanExecuteRequest request, boolean allowAutoRollback) {
        ChangePreflightResult preflight = changePreflightService.evaluate(entity, request);
        entity.setRiskLevel(preflight.riskLevel());
        entity.setPreflightJson(writeJson(preflight.checks()));
        if (!preflight.passed()) {
            entity.setStatus(ChangePlanStatus.FAILED.name());
            entity.setCurrentStage("PRECHECK");
            entity.setCurrentMessage("preflight 失败。");
            opsAuditService.record("OPERATIONS", "CHANGE_PLAN_PREFLIGHT_FAILED", "change_plan", String.valueOf(entity.getId()), entity.getPreflightJson());
            return toResponse(changePlanRepository.save(entity));
        }

        entity = changePlanRepository.save(entity);
        if (ChangePlanType.UPGRADE.name().equals(entity.getPlanType())) {
            publishChangeEvent(PlatformEventType.UPGRADE_STARTED, entity, "升级计划开始执行。");
        }
        ChangePlanEntity executed = releaseRolloutService.execute(entity, request);
        if (allowAutoRollback
                && ChangePlanType.UPGRADE.name().equals(executed.getPlanType())
                && ChangePlanStatus.FAILED.name().equals(executed.getStatus())
                && executed.getRollbackPlaybookId() != null) {
            publishChangeEvent(PlatformEventType.UPGRADE_FAILED, executed, executed.getCurrentMessage());
            ChangePlanResponse rollback = triggerAutoRollback(executed, executed.getCurrentMessage());
            executed.setStatus(ChangePlanStatus.ROLLED_BACK.name());
            executed.setCurrentMessage("升级失败，已自动触发回滚计划 #" + rollback.id() + "。");
            changePlanRepository.save(executed);
            publishChangeEvent(PlatformEventType.UPGRADE_ROLLED_BACK, executed, executed.getCurrentMessage());
        } else if (ChangePlanType.UPGRADE.name().equals(executed.getPlanType())
                && ChangePlanStatus.FAILED.name().equals(executed.getStatus())) {
            publishChangeEvent(PlatformEventType.UPGRADE_FAILED, executed, executed.getCurrentMessage());
        }
        opsAuditService.record("OPERATIONS", "CHANGE_PLAN_EXECUTED", "change_plan", String.valueOf(executed.getId()), writeJson(executed));
        return toResponse(changePlanRepository.save(executed));
    }

    private ChangePlanResponse triggerAutoRollback(ChangePlanEntity sourcePlan, String reason) {
        RollbackPlaybookResponse playbook = rollbackPlaybookService.get(sourcePlan.getRollbackPlaybookId());
        ChangePlanEntity rollbackPlan = new ChangePlanEntity();
        rollbackPlan.setPlanName("auto-rollback-for-" + sourcePlan.getId());
        rollbackPlan.setPlanType(ChangePlanType.ROLLBACK.name());
        rollbackPlan.setExecutionClass(ChangePlanExecutionClass.AUTO_TRIGGERED.name());
        rollbackPlan.setStatus(ChangePlanStatus.APPROVED.name());
        rollbackPlan.setReleaseArtifactId(playbook.rollbackReleaseArtifactId());
        rollbackPlan.setRecoveryCheckpointId(playbook.recoveryCheckpointId());
        rollbackPlan.setRequestedBy("SYSTEM");
        rollbackPlan.setApprovedBy("SYSTEM");
        rollbackPlan.setManualOverride(true);
        rollbackPlan.setOverrideReason(reason);
        rollbackPlan.setEmergencyReason(reason);
        rollbackPlan = changePlanRepository.save(rollbackPlan);
        changeApprovalService.recordSystemApproval(rollbackPlan, "由升级计划 #" + sourcePlan.getId() + " 自动触发回滚。");
        rollbackPlaybookService.markTriggered(playbook.id(), rollbackPlan.getId());
        return executeInternal(rollbackPlan, new ChangePlanExecuteRequest("SYSTEM", true, reason, reason, false), false);
    }

    private String initialStatus(ChangePlanEntity entity) {
        ChangePlanType planType = ChangePlanType.valueOf(entity.getPlanType());
        ChangePlanExecutionClass executionClass = ChangePlanExecutionClass.valueOf(entity.getExecutionClass());
        if (executionClass == ChangePlanExecutionClass.AUTO_TRIGGERED) {
            return ChangePlanStatus.APPROVED.name();
        }
        if (executionClass == ChangePlanExecutionClass.DRY_RUN) {
            return ChangePlanStatus.READY.name();
        }
        if (planType == ChangePlanType.UPGRADE || planType == ChangePlanType.RESTORE || planType == ChangePlanType.ROLLBACK) {
            return ChangePlanStatus.PENDING_APPROVAL.name();
        }
        return ChangePlanStatus.READY.name();
    }

    private ChangePlanEntity getEntity(Long id) {
        return changePlanRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("未找到 change plan。"));
    }

    private ChangePlanResponse toResponse(ChangePlanEntity entity) {
        List<ChangePlanPreflightCheckResponse> preflightChecks = readPreflight(entity.getPreflightJson());
        List<ReleaseRolloutStageResponse> rolloutStages = releaseRolloutRepository.findAllByChangePlanIdOrderByCreatedAtAsc(entity.getId()).stream()
                .map(this::toStageResponse)
                .toList();
        return new ChangePlanResponse(
                entity.getId(),
                entity.getPlanName(),
                entity.getPlanType(),
                entity.getExecutionClass(),
                entity.getStatus(),
                entity.getReleaseArtifactId(),
                entity.getRecoveryCheckpointId(),
                entity.getMaintenanceWindowId(),
                entity.getRollbackPlaybookId(),
                entity.getRequestedBy(),
                entity.getApprovedBy(),
                entity.isManualOverride(),
                entity.getOverrideReason(),
                entity.getEmergencyReason(),
                entity.getRiskLevel(),
                entity.getCurrentStage(),
                entity.getCurrentMessage(),
                preflightChecks,
                changeApprovalService.listByPlanId(entity.getId()),
                rolloutStages,
                entity.getRollbackPlaybookId() == null ? null : rollbackPlaybookService.get(entity.getRollbackPlaybookId()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private List<ChangePlanPreflightCheckResponse> readPreflight(String preflightJson) {
        if (preflightJson == null || preflightJson.isBlank()) {
            return List.of();
        }
        try {
            List<ChangePreflightCheck> checks = objectMapper.readValue(preflightJson, new TypeReference<List<ChangePreflightCheck>>() {
            });
            return checks.stream()
                    .map(item -> new ChangePlanPreflightCheckResponse(item.checkName(), item.status(), item.blocking(), item.message()))
                    .toList();
        } catch (JacksonException exception) {
            throw new IllegalStateException("解析 preflight 结果失败。", exception);
        }
    }

    private ReleaseRolloutStageResponse toStageResponse(ReleaseRolloutEntity entity) {
        return new ReleaseRolloutStageResponse(
                entity.getId(),
                entity.getStage(),
                entity.getStatus(),
                entity.getMessage(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化 change plan 失败。", exception);
        }
    }

    private String defaultActor(String actor) {
        return actor == null || actor.isBlank() ? "console" : actor;
    }

    private void publishChangeEvent(PlatformEventType eventType, ChangePlanEntity entity, String summary) {
        platformEventPublisher.publish(
                eventType,
                "HIGH",
                "OPERATIONS",
                "CHANGE_PLAN",
                String.valueOf(entity.getId()),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                summary == null || summary.isBlank() ? entity.getPlanName() : summary,
                java.util.Map.of(
                        "changePlanId", entity.getId(),
                        "planType", entity.getPlanType(),
                        "status", entity.getStatus(),
                        "executionClass", entity.getExecutionClass()
                )
        );
    }
}
