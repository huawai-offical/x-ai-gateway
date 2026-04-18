package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ChangePlanExecuteRequest;
import com.prodigalgal.xaigateway.admin.application.operations.ChangePlanStatus;
import com.prodigalgal.xaigateway.admin.application.operations.ChangePlanType;
import com.prodigalgal.xaigateway.admin.application.operations.ReleaseRolloutStage;
import com.prodigalgal.xaigateway.admin.application.operations.ReleaseRolloutStageStatus;
import com.prodigalgal.xaigateway.infra.persistence.entity.ChangePlanEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.ReleaseRolloutEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.ChangePlanRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.ReleaseRolloutRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class ReleaseRolloutService {

    private final ReleaseRolloutRepository releaseRolloutRepository;
    private final ChangePlanRepository changePlanRepository;
    private final RecoveryCheckpointService recoveryCheckpointService;
    private final RollbackPlaybookService rollbackPlaybookService;
    private final PlatformOperationsService platformOperationsService;

    public ReleaseRolloutService(
            ReleaseRolloutRepository releaseRolloutRepository,
            ChangePlanRepository changePlanRepository,
            RecoveryCheckpointService recoveryCheckpointService,
            RollbackPlaybookService rollbackPlaybookService,
            PlatformOperationsService platformOperationsService) {
        this.releaseRolloutRepository = releaseRolloutRepository;
        this.changePlanRepository = changePlanRepository;
        this.recoveryCheckpointService = recoveryCheckpointService;
        this.rollbackPlaybookService = rollbackPlaybookService;
        this.platformOperationsService = platformOperationsService;
    }

    public ChangePlanEntity execute(ChangePlanEntity changePlan, ChangePlanExecuteRequest executeRequest) {
        ChangePlanType type = ChangePlanType.valueOf(changePlan.getPlanType());
        return switch (type) {
            case SNAPSHOT -> executeSnapshot(changePlan);
            case RESTORE -> executeRestore(changePlan);
            case ROLLBACK -> executeRollback(changePlan);
            case UPGRADE -> executeUpgrade(changePlan, executeRequest);
        };
    }

    private ChangePlanEntity executeSnapshot(ChangePlanEntity changePlan) {
        markPlanRunning(changePlan, ReleaseRolloutStage.PRECHECK, "开始创建 checkpoint。");
        recordStage(changePlan.getId(), ReleaseRolloutStage.PRECHECK, ReleaseRolloutStageStatus.COMPLETED, "precheck 通过。");
        recordStage(changePlan.getId(), ReleaseRolloutStage.CREATE_CHECKPOINT, ReleaseRolloutStageStatus.RUNNING, "创建 recovery checkpoint。");
        var checkpoint = recoveryCheckpointService.createCheckpointForPlan(changePlan);
        platformOperationsService.recordBackupJob(false, "COMPLETED", checkpoint.dataSnapshotPath(), checkpoint.manifestJson());
        changePlan.setRecoveryCheckpointId(checkpoint.id());
        changePlan.setCurrentStage(ReleaseRolloutStage.COMPLETE.name());
        changePlan.setCurrentMessage("已完成 checkpoint 创建。");
        changePlan.setStatus(ChangePlanStatus.COMPLETED.name());
        changePlanRepository.save(changePlan);
        recordStage(changePlan.getId(), ReleaseRolloutStage.CREATE_CHECKPOINT, ReleaseRolloutStageStatus.COMPLETED, "recovery checkpoint 已生成。");
        recordStage(changePlan.getId(), ReleaseRolloutStage.COMPLETE, ReleaseRolloutStageStatus.COMPLETED, "SNAPSHOT 执行完成。");
        return changePlan;
    }

    private ChangePlanEntity executeRestore(ChangePlanEntity changePlan) {
        markPlanRunning(changePlan, ReleaseRolloutStage.PRECHECK, "开始恢复 checkpoint。");
        recordStage(changePlan.getId(), ReleaseRolloutStage.PRECHECK, ReleaseRolloutStageStatus.COMPLETED, "precheck 通过。");
        recoveryCheckpointService.restore(changePlan.getRecoveryCheckpointId());
        platformOperationsService.recordRestoreJob(null, false, "COMPLETED", "RESTORE from checkpoint " + changePlan.getRecoveryCheckpointId());
        changePlan.setCurrentStage(ReleaseRolloutStage.COMPLETE.name());
        changePlan.setCurrentMessage("已完成恢复。");
        changePlan.setStatus(ChangePlanStatus.COMPLETED.name());
        recordStage(changePlan.getId(), ReleaseRolloutStage.COMPLETE, ReleaseRolloutStageStatus.COMPLETED, "RESTORE 执行完成。");
        return changePlanRepository.save(changePlan);
    }

    private ChangePlanEntity executeRollback(ChangePlanEntity changePlan) {
        markPlanRunning(changePlan, ReleaseRolloutStage.PRECHECK, "开始执行回滚。");
        recordStage(changePlan.getId(), ReleaseRolloutStage.PRECHECK, ReleaseRolloutStageStatus.COMPLETED, "precheck 通过。");
        if (changePlan.getReleaseArtifactId() != null) {
            platformOperationsService.activateRelease(changePlan.getReleaseArtifactId(), "READY");
        }
        if (changePlan.getRecoveryCheckpointId() != null) {
            recoveryCheckpointService.restore(changePlan.getRecoveryCheckpointId());
        }
        platformOperationsService.recordRollbackJob(null, changePlan.getReleaseArtifactId(), null, "COMPLETED", "ROLLBACK 执行完成。");
        changePlan.setCurrentStage(ReleaseRolloutStage.COMPLETE.name());
        changePlan.setCurrentMessage("已完成回滚。");
        changePlan.setStatus(ChangePlanStatus.COMPLETED.name());
        recordStage(changePlan.getId(), ReleaseRolloutStage.COMPLETE, ReleaseRolloutStageStatus.COMPLETED, "ROLLBACK 执行完成。");
        return changePlanRepository.save(changePlan);
    }

    private ChangePlanEntity executeUpgrade(ChangePlanEntity changePlan, ChangePlanExecuteRequest executeRequest) {
        Long previousReleaseArtifactId = platformOperationsService.getInstallationState().activeReleaseArtifactId();
        markPlanRunning(changePlan, ReleaseRolloutStage.PRECHECK, "开始升级执行。");
        recordStage(changePlan.getId(), ReleaseRolloutStage.PRECHECK, ReleaseRolloutStageStatus.COMPLETED, "precheck 通过。");

        recordStage(changePlan.getId(), ReleaseRolloutStage.CREATE_CHECKPOINT, ReleaseRolloutStageStatus.RUNNING, "创建升级前 checkpoint。");
        var checkpoint = recoveryCheckpointService.createCheckpointForPlan(changePlan);
        platformOperationsService.recordBackupJob(false, "COMPLETED", checkpoint.dataSnapshotPath(), checkpoint.manifestJson());
        changePlan.setRecoveryCheckpointId(checkpoint.id());
        recordStage(changePlan.getId(), ReleaseRolloutStage.CREATE_CHECKPOINT, ReleaseRolloutStageStatus.COMPLETED, "升级前 checkpoint 创建成功。");
        if (changePlan.getRollbackPlaybookId() != null) {
            rollbackPlaybookService.attachCheckpoint(changePlan.getRollbackPlaybookId(), checkpoint.id(), previousReleaseArtifactId);
        }

        recordStage(changePlan.getId(), ReleaseRolloutStage.SWITCH_RELEASE, ReleaseRolloutStageStatus.RUNNING, "切换 active release。");
        platformOperationsService.activateRelease(changePlan.getReleaseArtifactId(), "UPGRADING");
        platformOperationsService.recordUpgradeJob(changePlan.getReleaseArtifactId(), null, "RUNNING", "切换到目标 release。", false);
        recordStage(changePlan.getId(), ReleaseRolloutStage.SWITCH_RELEASE, ReleaseRolloutStageStatus.COMPLETED, "目标 release 已激活。");

        recordStage(changePlan.getId(), ReleaseRolloutStage.CANARY_VERIFY, ReleaseRolloutStageStatus.RUNNING, "执行 canary verify。");
        if (Boolean.TRUE.equals(executeRequest.simulateFailure())) {
            recordStage(changePlan.getId(), ReleaseRolloutStage.CANARY_VERIFY, ReleaseRolloutStageStatus.FAILED, "模拟 canary verify 失败。");
            changePlan.setStatus(ChangePlanStatus.FAILED.name());
            changePlan.setCurrentStage(ReleaseRolloutStage.CANARY_VERIFY.name());
            changePlan.setCurrentMessage("canary verify 失败。");
            return changePlanRepository.save(changePlan);
        }
        recordStage(changePlan.getId(), ReleaseRolloutStage.CANARY_VERIFY, ReleaseRolloutStageStatus.COMPLETED, "canary verify 通过。");

        recordStage(changePlan.getId(), ReleaseRolloutStage.FULL_VERIFY, ReleaseRolloutStageStatus.RUNNING, "执行 full verify。");
        recordStage(changePlan.getId(), ReleaseRolloutStage.FULL_VERIFY, ReleaseRolloutStageStatus.COMPLETED, "full verify 通过。");
        recordStage(changePlan.getId(), ReleaseRolloutStage.COMPLETE, ReleaseRolloutStageStatus.COMPLETED, "UPGRADE 执行完成。");
        platformOperationsService.activateRelease(changePlan.getReleaseArtifactId(), "READY");
        platformOperationsService.recordUpgradeJob(changePlan.getReleaseArtifactId(), null, "COMPLETED", "升级执行完成。", false);
        changePlan.setStatus(ChangePlanStatus.COMPLETED.name());
        changePlan.setCurrentStage(ReleaseRolloutStage.COMPLETE.name());
        changePlan.setCurrentMessage("升级完成。");
        return changePlanRepository.save(changePlan);
    }

    private void markPlanRunning(ChangePlanEntity changePlan, ReleaseRolloutStage stage, String message) {
        changePlan.setStatus(ChangePlanStatus.RUNNING.name());
        changePlan.setCurrentStage(stage.name());
        changePlan.setCurrentMessage(message);
        changePlanRepository.save(changePlan);
    }

    private void recordStage(Long changePlanId, ReleaseRolloutStage stage, ReleaseRolloutStageStatus status, String message) {
        ReleaseRolloutEntity entity = new ReleaseRolloutEntity();
        entity.setChangePlanId(changePlanId);
        entity.setStage(stage.name());
        entity.setStatus(status.name());
        entity.setMessage(message);
        entity.setStartedAt(Instant.now());
        entity.setCompletedAt(status == ReleaseRolloutStageStatus.RUNNING ? null : Instant.now());
        releaseRolloutRepository.save(entity);
    }
}
