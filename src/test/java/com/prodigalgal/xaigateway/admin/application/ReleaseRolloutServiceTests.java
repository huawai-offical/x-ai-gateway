package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ChangePlanExecuteRequest;
import com.prodigalgal.xaigateway.admin.api.RecoveryCheckpointResponse;
import com.prodigalgal.xaigateway.infra.persistence.entity.ChangePlanEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.ReleaseRolloutEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.ChangePlanRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.ReleaseRolloutRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReleaseRolloutServiceTests {

    @Test
    void shouldFailUpgradeWhenCanaryVerificationFails() {
        ReleaseRolloutRepository releaseRolloutRepository = Mockito.mock(ReleaseRolloutRepository.class);
        ChangePlanRepository changePlanRepository = Mockito.mock(ChangePlanRepository.class);
        RecoveryCheckpointService recoveryCheckpointService = Mockito.mock(RecoveryCheckpointService.class);
        RollbackPlaybookService rollbackPlaybookService = Mockito.mock(RollbackPlaybookService.class);
        PlatformOperationsService platformOperationsService = Mockito.mock(PlatformOperationsService.class);
        ReleaseRolloutService service = new ReleaseRolloutService(
                releaseRolloutRepository,
                changePlanRepository,
                recoveryCheckpointService,
                rollbackPlaybookService,
                platformOperationsService
        );

        ChangePlanEntity entity = new ChangePlanEntity();
        ReflectionTestUtils.setField(entity, "id", 7L);
        entity.setPlanType("UPGRADE");
        entity.setReleaseArtifactId(8L);
        entity.setRollbackPlaybookId(12L);
        entity.setStatus("APPROVED");
        Mockito.when(platformOperationsService.getInstallationState())
                .thenReturn(new com.prodigalgal.xaigateway.admin.api.InstallationStateResponse(1L, "READY", 5L, true, Instant.now(), "{}", Instant.now(), Instant.now()));
        Mockito.when(recoveryCheckpointService.createCheckpointForPlan(Mockito.any()))
                .thenReturn(new RecoveryCheckpointResponse(3L, "cp-3", 7L, "READY", "meta", "runtime", "data", "{}", null, null, null, null, Instant.now(), Instant.now()));
        Mockito.when(changePlanRepository.save(Mockito.any(ChangePlanEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(releaseRolloutRepository.save(Mockito.any(ReleaseRolloutEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChangePlanEntity result = service.execute(entity, new ChangePlanExecuteRequest("ops", false, null, null, true));

        assertEquals("FAILED", result.getStatus());
        assertEquals("CANARY_VERIFY", result.getCurrentStage());
    }
}
