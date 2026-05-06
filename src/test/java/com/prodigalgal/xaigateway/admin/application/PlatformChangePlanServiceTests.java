package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ChangePlanRequest;
import com.prodigalgal.xaigateway.admin.api.ChangePlanExecuteRequest;
import com.prodigalgal.xaigateway.admin.api.RollbackPlaybookResponse;
import com.prodigalgal.xaigateway.admin.application.integrations.PlatformEventPublisher;
import com.prodigalgal.xaigateway.admin.application.integrations.PlatformEventType;
import com.prodigalgal.xaigateway.admin.application.operations.ChangePlanExecutionClass;
import com.prodigalgal.xaigateway.admin.application.operations.ChangePlanStatus;
import com.prodigalgal.xaigateway.admin.application.operations.ChangePlanType;
import com.prodigalgal.xaigateway.admin.application.operations.ChangePreflightCheck;
import com.prodigalgal.xaigateway.admin.application.operations.ChangePreflightResult;
import com.prodigalgal.xaigateway.infra.persistence.entity.ChangePlanEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.ChangePlanRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.ReleaseRolloutRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

class PlatformChangePlanServiceTests {

    @Test
    void shouldCreateUpgradePlanPendingApprovalAndPlaybook() {
        ChangePlanRepository changePlanRepository = Mockito.mock(ChangePlanRepository.class);
        ReleaseRolloutRepository releaseRolloutRepository = Mockito.mock(ReleaseRolloutRepository.class);
        ChangeApprovalService changeApprovalService = Mockito.mock(ChangeApprovalService.class);
        ChangePreflightService changePreflightService = Mockito.mock(ChangePreflightService.class);
        ReleaseRolloutService releaseRolloutService = Mockito.mock(ReleaseRolloutService.class);
        RollbackPlaybookService rollbackPlaybookService = Mockito.mock(RollbackPlaybookService.class);
        RecoveryCheckpointService recoveryCheckpointService = Mockito.mock(RecoveryCheckpointService.class);
        OpsAuditService opsAuditService = Mockito.mock(OpsAuditService.class);
        PlatformEventPublisher platformEventPublisher = Mockito.mock(PlatformEventPublisher.class);
        PlatformChangePlanService service = new PlatformChangePlanService(
                changePlanRepository,
                releaseRolloutRepository,
                new ObjectMapper(),
                changeApprovalService,
                changePreflightService,
                releaseRolloutService,
                rollbackPlaybookService,
                recoveryCheckpointService,
                opsAuditService,
                platformEventPublisher
        );

        Mockito.when(changePlanRepository.save(any(ChangePlanEntity.class))).thenAnswer(invocation -> {
            ChangePlanEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                ReflectionTestUtils.setField(entity, "id", 11L);
            }
            ReflectionTestUtils.setField(entity, "createdAt", Instant.now());
            ReflectionTestUtils.setField(entity, "updatedAt", Instant.now());
            return entity;
        });
        Mockito.when(rollbackPlaybookService.ensureForUpgradePlan(11L))
                .thenReturn(new RollbackPlaybookResponse(21L, null, null, "ACTIVE", "[]", null, null, Instant.now(), Instant.now()));
        Mockito.when(releaseRolloutRepository.findAllByChangePlanIdOrderByCreatedAtAsc(11L)).thenReturn(List.of());

        var response = service.create(new ChangePlanRequest(
                "upgrade-main",
                "UPGRADE",
                "MANUAL",
                8L,
                null,
                2L,
                "ops",
                false,
                null,
                null
        ));

        assertEquals("PENDING_APPROVAL", response.status());
        assertEquals(21L, response.rollbackPlaybookId());
    }

    @Test
    void shouldCreateDryRunSnapshotReady() {
        ChangePlanRepository changePlanRepository = Mockito.mock(ChangePlanRepository.class);
        ReleaseRolloutRepository releaseRolloutRepository = Mockito.mock(ReleaseRolloutRepository.class);
        ChangeApprovalService changeApprovalService = Mockito.mock(ChangeApprovalService.class);
        ChangePreflightService changePreflightService = Mockito.mock(ChangePreflightService.class);
        ReleaseRolloutService releaseRolloutService = Mockito.mock(ReleaseRolloutService.class);
        RollbackPlaybookService rollbackPlaybookService = Mockito.mock(RollbackPlaybookService.class);
        RecoveryCheckpointService recoveryCheckpointService = Mockito.mock(RecoveryCheckpointService.class);
        OpsAuditService opsAuditService = Mockito.mock(OpsAuditService.class);
        PlatformEventPublisher platformEventPublisher = Mockito.mock(PlatformEventPublisher.class);
        PlatformChangePlanService service = new PlatformChangePlanService(
                changePlanRepository,
                releaseRolloutRepository,
                new ObjectMapper(),
                changeApprovalService,
                changePreflightService,
                releaseRolloutService,
                rollbackPlaybookService,
                recoveryCheckpointService,
                opsAuditService,
                platformEventPublisher
        );

        Mockito.when(changePlanRepository.save(any(ChangePlanEntity.class))).thenAnswer(invocation -> {
            ChangePlanEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                ReflectionTestUtils.setField(entity, "id", 15L);
            }
            ReflectionTestUtils.setField(entity, "createdAt", Instant.now());
            ReflectionTestUtils.setField(entity, "updatedAt", Instant.now());
            return entity;
        });
        Mockito.when(releaseRolloutRepository.findAllByChangePlanIdOrderByCreatedAtAsc(15L)).thenReturn(List.of());

        var response = service.create(new ChangePlanRequest(
                "snapshot-main",
                "SNAPSHOT",
                "DRY_RUN",
                null,
                null,
                null,
                "ops",
                false,
                null,
                null
        ));

        assertEquals("READY", response.status());
    }

    @Test
    void shouldAutoRollbackFailedUpgradeWithPlaybook() {
        ChangePlanRepository changePlanRepository = Mockito.mock(ChangePlanRepository.class);
        ReleaseRolloutRepository releaseRolloutRepository = Mockito.mock(ReleaseRolloutRepository.class);
        ChangeApprovalService changeApprovalService = Mockito.mock(ChangeApprovalService.class);
        ChangePreflightService changePreflightService = Mockito.mock(ChangePreflightService.class);
        ReleaseRolloutService releaseRolloutService = Mockito.mock(ReleaseRolloutService.class);
        RollbackPlaybookService rollbackPlaybookService = Mockito.mock(RollbackPlaybookService.class);
        RecoveryCheckpointService recoveryCheckpointService = Mockito.mock(RecoveryCheckpointService.class);
        OpsAuditService opsAuditService = Mockito.mock(OpsAuditService.class);
        PlatformEventPublisher platformEventPublisher = Mockito.mock(PlatformEventPublisher.class);
        PlatformChangePlanService service = new PlatformChangePlanService(
                changePlanRepository,
                releaseRolloutRepository,
                new ObjectMapper(),
                changeApprovalService,
                changePreflightService,
                releaseRolloutService,
                rollbackPlaybookService,
                recoveryCheckpointService,
                opsAuditService,
                platformEventPublisher
        );

        ChangePlanEntity upgradePlan = changePlan(
                31L,
                "upgrade-main",
                ChangePlanType.UPGRADE.name(),
                ChangePlanExecutionClass.MANUAL.name(),
                ChangePlanStatus.APPROVED.name()
        );
        upgradePlan.setReleaseArtifactId(8L);
        upgradePlan.setRollbackPlaybookId(41L);
        upgradePlan.setRequestedBy("ops");
        upgradePlan.setApprovedBy("lead");

        Mockito.when(changePlanRepository.findById(31L)).thenReturn(Optional.of(upgradePlan));
        Mockito.when(changePlanRepository.save(any(ChangePlanEntity.class))).thenAnswer(invocation -> {
            ChangePlanEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                ReflectionTestUtils.setField(entity, "id", 99L);
            }
            ReflectionTestUtils.setField(entity, "createdAt", Instant.now());
            ReflectionTestUtils.setField(entity, "updatedAt", Instant.now());
            return entity;
        });
        Mockito.when(changePreflightService.evaluate(any(ChangePlanEntity.class), any(ChangePlanExecuteRequest.class)))
                .thenReturn(new ChangePreflightResult("LOW", List.of(new ChangePreflightCheck(
                        "ops-drill",
                        "OK",
                        true,
                        "演练预检通过。"
                ))));
        Mockito.when(releaseRolloutService.execute(any(ChangePlanEntity.class), any(ChangePlanExecuteRequest.class)))
                .thenAnswer(invocation -> {
                    ChangePlanEntity entity = invocation.getArgument(0);
                    if (ChangePlanType.UPGRADE.name().equals(entity.getPlanType())) {
                        entity.setStatus(ChangePlanStatus.FAILED.name());
                        entity.setCurrentStage("CANARY_VERIFY");
                        entity.setCurrentMessage("canary verify 失败。");
                        return entity;
                    }
                    entity.setStatus(ChangePlanStatus.COMPLETED.name());
                    entity.setCurrentStage("COMPLETE");
                    entity.setCurrentMessage("自动回滚完成。");
                    return entity;
                });
        RollbackPlaybookResponse playbook = new RollbackPlaybookResponse(
                41L,
                51L,
                61L,
                "ACTIVE",
                "[]",
                null,
                null,
                Instant.now(),
                Instant.now()
        );
        Mockito.when(rollbackPlaybookService.get(41L)).thenReturn(playbook);
        Mockito.when(releaseRolloutRepository.findAllByChangePlanIdOrderByCreatedAtAsc(Mockito.anyLong())).thenReturn(List.of());
        Mockito.when(changeApprovalService.listByPlanId(Mockito.anyLong())).thenReturn(List.of());

        var response = service.execute(31L, new ChangePlanExecuteRequest("ops", false, null, null, false));

        assertEquals("ROLLED_BACK", response.status());
        assertEquals(41L, response.rollbackPlaybookId());
        assertEquals("升级失败，已自动触发回滚计划 #99。", response.currentMessage());
        Mockito.verify(changeApprovalService).recordSystemApproval(
                Mockito.argThat(plan -> Long.valueOf(99L).equals(plan.getId())
                        && ChangePlanType.ROLLBACK.name().equals(plan.getPlanType())
                        && ChangePlanExecutionClass.AUTO_TRIGGERED.name().equals(plan.getExecutionClass())),
                Mockito.contains("自动触发回滚")
        );
        Mockito.verify(rollbackPlaybookService).markTriggered(41L, 99L);
        Mockito.verify(platformEventPublisher).publish(
                Mockito.eq(PlatformEventType.UPGRADE_STARTED),
                Mockito.eq("HIGH"),
                Mockito.eq("OPERATIONS"),
                Mockito.eq("CHANGE_PLAN"),
                Mockito.eq("31"),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.eq("升级计划开始执行。"),
                Mockito.anyMap()
        );
        Mockito.verify(platformEventPublisher).publish(
                Mockito.eq(PlatformEventType.UPGRADE_FAILED),
                Mockito.eq("HIGH"),
                Mockito.eq("OPERATIONS"),
                Mockito.eq("CHANGE_PLAN"),
                Mockito.eq("31"),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.eq("canary verify 失败。"),
                Mockito.anyMap()
        );
        Mockito.verify(platformEventPublisher).publish(
                Mockito.eq(PlatformEventType.UPGRADE_ROLLED_BACK),
                Mockito.eq("HIGH"),
                Mockito.eq("OPERATIONS"),
                Mockito.eq("CHANGE_PLAN"),
                Mockito.eq("31"),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.eq("升级失败，已自动触发回滚计划 #99。"),
                Mockito.anyMap()
        );
        Mockito.verify(opsAuditService, Mockito.atLeastOnce()).record(
                Mockito.eq("OPERATIONS"),
                Mockito.eq("CHANGE_PLAN_EXECUTED"),
                Mockito.eq("change_plan"),
                Mockito.eq("31"),
                Mockito.contains("\"status\":\"ROLLED_BACK\"")
        );
    }

    private ChangePlanEntity changePlan(Long id, String name, String type, String executionClass, String status) {
        ChangePlanEntity entity = new ChangePlanEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.now());
        ReflectionTestUtils.setField(entity, "updatedAt", Instant.now());
        entity.setPlanName(name);
        entity.setPlanType(type);
        entity.setExecutionClass(executionClass);
        entity.setStatus(status);
        return entity;
    }
}
