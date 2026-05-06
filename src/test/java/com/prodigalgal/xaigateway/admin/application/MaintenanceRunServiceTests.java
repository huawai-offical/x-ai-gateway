package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.MaintenanceRunRequest;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.MaintenanceRunEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.MaintenanceRunRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaintenanceRunServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldRequireConfirmForRealBackupAndCreateArtifactWhenConfirmed() {
        MaintenanceRunRepository repository = Mockito.mock(MaintenanceRunRepository.class);
        OpsAuditService opsAuditService = Mockito.mock(OpsAuditService.class);
        GatewayProperties properties = new GatewayProperties();
        properties.getStorage().setFileRoot(tempDir.toString());
        MaintenanceRunService service = new MaintenanceRunService(repository, properties, new ObjectMapper(), opsAuditService);

        Mockito.when(repository.save(Mockito.any())).thenAnswer(invocation -> {
            MaintenanceRunEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 5L);
            return entity;
        });

        assertThrows(IllegalArgumentException.class, () -> service.execute(new MaintenanceRunRequest("BACKUP", false, false, "ops", null, null)));

        var response = service.execute(new MaintenanceRunRequest("BACKUP", false, true, "ops", null, null));

        assertTrue(response.confirmRequired());
        assertNotNull(response.artifactPath());
        assertNotNull(response.artifactChecksum());
        assertTrue(Files.exists(Path.of(response.artifactPath())));
        assertTrue(response.detailJson().contains("\"checks\""));
        assertTrue(response.detailJson().contains("\"summary\""));
        Mockito.verify(opsAuditService).record(
                Mockito.eq("MAINTENANCE"),
                Mockito.eq("RUN_BACKUP"),
                Mockito.eq("maintenance_run"),
                Mockito.eq("5"),
                Mockito.contains("\"runType\":\"BACKUP\"")
        );
    }

    @Test
    void shouldCreateDryRunEvidenceForMaintenanceRunTypes() {
        MaintenanceRunRepository repository = Mockito.mock(MaintenanceRunRepository.class);
        OpsAuditService opsAuditService = Mockito.mock(OpsAuditService.class);
        GatewayProperties properties = new GatewayProperties();
        properties.getStorage().setFileRoot(tempDir.toString());
        MaintenanceRunService service = new MaintenanceRunService(repository, properties, new ObjectMapper(), opsAuditService);
        final long[] ids = {10L};
        Mockito.when(repository.save(Mockito.any())).thenAnswer(invocation -> {
            MaintenanceRunEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", ids[0]++);
            return entity;
        });

        List<MaintenanceRunRequest> requests = List.of(
                new MaintenanceRunRequest("PRECHECK", true, false, "ops", "local", "{}"),
                new MaintenanceRunRequest("RESTORE_DRY_RUN", true, false, "ops", "checkpoint-1", "{\"checkpointId\":1}"),
                new MaintenanceRunRequest("UPGRADE_CHECK", true, false, "ops", "release-1", "{\"targetVersion\":\"1.2.0\"}"),
                new MaintenanceRunRequest("ROLLBACK_PLAN", true, false, "ops", "rollback-1", "{\"rollback\":\"release-0\"}")
        );

        for (MaintenanceRunRequest request : requests) {
            var response = service.execute(request);

            assertEquals("COMPLETED", response.status());
            assertTrue(response.dryRun());
            assertFalse(response.confirmRequired());
            assertFalse(response.confirmed());
            assertNull(response.artifactPath());
            assertNotNull(response.artifactChecksum());
            assertTrue(response.detailJson().contains("\"runType\":\"" + request.runType() + "\""));
            assertTrue(response.detailJson().contains("\"checks\""));
            assertTrue(response.detailJson().contains("\"summary\""));
        }

        Mockito.verify(opsAuditService).record(
                Mockito.eq("MAINTENANCE"),
                Mockito.eq("RUN_PRECHECK"),
                Mockito.eq("maintenance_run"),
                Mockito.eq("10"),
                Mockito.contains("\"mode\":\"readiness_probe\"")
        );
        Mockito.verify(opsAuditService).record(
                Mockito.eq("MAINTENANCE"),
                Mockito.eq("RUN_RESTORE_DRY_RUN"),
                Mockito.eq("maintenance_run"),
                Mockito.eq("11"),
                Mockito.contains("\"mode\":\"restore_plan_validation\"")
        );
        Mockito.verify(opsAuditService).record(
                Mockito.eq("MAINTENANCE"),
                Mockito.eq("RUN_UPGRADE_CHECK"),
                Mockito.eq("maintenance_run"),
                Mockito.eq("12"),
                Mockito.contains("\"mode\":\"release_compatibility_check\"")
        );
        Mockito.verify(opsAuditService).record(
                Mockito.eq("MAINTENANCE"),
                Mockito.eq("RUN_ROLLBACK_PLAN"),
                Mockito.eq("maintenance_run"),
                Mockito.eq("13"),
                Mockito.contains("\"mode\":\"rollback_strategy_preview\"")
        );
    }
}
