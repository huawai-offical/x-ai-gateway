package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ChangePlanRequest;
import com.prodigalgal.xaigateway.admin.api.RollbackPlaybookResponse;
import com.prodigalgal.xaigateway.admin.application.integrations.PlatformEventPublisher;
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
}
