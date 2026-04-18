package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.infra.persistence.entity.ChangePlanEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RecoveryCheckpointEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.RecoveryCheckpointRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecoveryCheckpointServiceTests {

    @Test
    void shouldCreateAndVerifyCheckpoint() {
        RecoveryCheckpointRepository repository = Mockito.mock(RecoveryCheckpointRepository.class);
        MetadataSnapshotService metadataSnapshotService = Mockito.mock(MetadataSnapshotService.class);
        RuntimeStateSnapshotService runtimeStateSnapshotService = Mockito.mock(RuntimeStateSnapshotService.class);
        DataSnapshotService dataSnapshotService = Mockito.mock(DataSnapshotService.class);
        RecoveryCheckpointService service = new RecoveryCheckpointService(
                repository,
                metadataSnapshotService,
                runtimeStateSnapshotService,
                dataSnapshotService,
                new ObjectMapper()
        );

        Mockito.when(metadataSnapshotService.createSnapshot(Mockito.anyString()))
                .thenReturn(new MetadataSnapshotService.SnapshotResult("meta.json", "{}"));
        Mockito.when(runtimeStateSnapshotService.createSnapshot(Mockito.anyString()))
                .thenReturn(new RuntimeStateSnapshotService.SnapshotResult("runtime.json", "{}"));
        Mockito.when(dataSnapshotService.createSnapshot(Mockito.anyString()))
                .thenReturn(new DataSnapshotService.SnapshotResult("data.json", "{}"));
        Mockito.when(metadataSnapshotService.verifySnapshot("meta.json"))
                .thenReturn(new MetadataSnapshotService.VerificationResult(true, "ok"));
        Mockito.when(runtimeStateSnapshotService.verifySnapshot("runtime.json"))
                .thenReturn(new RuntimeStateSnapshotService.VerificationResult(true, "ok"));
        Mockito.when(dataSnapshotService.verifySnapshot("data.json"))
                .thenReturn(new DataSnapshotService.VerificationResult(true, "ok"));
        Mockito.when(repository.save(Mockito.any(RecoveryCheckpointEntity.class))).thenAnswer(invocation -> {
            RecoveryCheckpointEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                ReflectionTestUtils.setField(entity, "id", 3L);
            }
            ReflectionTestUtils.setField(entity, "createdAt", Instant.now());
            ReflectionTestUtils.setField(entity, "updatedAt", Instant.now());
            return entity;
        });
        Mockito.when(repository.findById(3L)).thenAnswer(invocation -> {
            RecoveryCheckpointEntity entity = new RecoveryCheckpointEntity();
            ReflectionTestUtils.setField(entity, "id", 3L);
            ReflectionTestUtils.setField(entity, "createdAt", Instant.now());
            ReflectionTestUtils.setField(entity, "updatedAt", Instant.now());
            entity.setCheckpointName("cp-3");
            entity.setChangePlanId(1L);
            entity.setStatus("READY");
            entity.setMetadataSnapshotPath("meta.json");
            entity.setRuntimeSnapshotPath("runtime.json");
            entity.setDataSnapshotPath("data.json");
            entity.setManifestJson("{}");
            return java.util.Optional.of(entity);
        });

        ChangePlanEntity plan = new ChangePlanEntity();
        ReflectionTestUtils.setField(plan, "id", 1L);
        plan.setPlanName("snapshot-main");
        var created = service.createCheckpointForPlan(plan);
        var verified = service.verify(3L, "ops");

        assertEquals(3L, created.id());
        assertEquals("VERIFIED", verified.verificationStatus());
    }
}
