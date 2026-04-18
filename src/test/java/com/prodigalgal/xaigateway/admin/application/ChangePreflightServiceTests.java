package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ChangePlanExecuteRequest;
import com.prodigalgal.xaigateway.admin.api.OpsSloSummaryResponse;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.ChangePlanEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.ChangePlanRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.ReleaseArtifactRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangePreflightServiceTests {

    @Test
    void shouldRequireMaintenanceWindowForUpgrade() throws Exception {
        ReleaseArtifactRepository releaseArtifactRepository = Mockito.mock(ReleaseArtifactRepository.class);
        RecoveryCheckpointService recoveryCheckpointService = Mockito.mock(RecoveryCheckpointService.class);
        ChangePlanRepository changePlanRepository = Mockito.mock(ChangePlanRepository.class);
        MaintenanceWindowService maintenanceWindowService = Mockito.mock(MaintenanceWindowService.class);
        DataSnapshotService dataSnapshotService = Mockito.mock(DataSnapshotService.class);
        OpsSloService opsSloService = Mockito.mock(OpsSloService.class);
        GatewayProperties gatewayProperties = new GatewayProperties();
        gatewayProperties.getStorage().setFileRoot(".data/test-files");
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(dataSource.getConnection()).thenReturn(connection);
        Mockito.when(releaseArtifactRepository.findById(8L)).thenReturn(java.util.Optional.of(new com.prodigalgal.xaigateway.infra.persistence.entity.ReleaseArtifactEntity()));
        Mockito.when(changePlanRepository.existsByStatusIn(List.of("RUNNING", "ROLLING_BACK"))).thenReturn(false);
        Mockito.when(dataSnapshotService.availabilityCheck()).thenReturn(new com.prodigalgal.xaigateway.admin.application.operations.DataSnapshotDriver.AvailabilityCheck(true, "ok"));
        Mockito.when(opsSloService.summary(Mockito.any())).thenReturn(new OpsSloSummaryResponse(
                Instant.now(),
                new OpsSloSummaryResponse.SummaryCards(1, 0, 0.0, 0.05, 1.0, 0.0, "LOW", 0),
                List.of(),
                List.of(),
                List.of()
        ));
        Mockito.when(maintenanceWindowService.isActive(Mockito.eq(9L), Mockito.any())).thenReturn(false);
        ChangePreflightService service = new ChangePreflightService(
                releaseArtifactRepository,
                recoveryCheckpointService,
                changePlanRepository,
                maintenanceWindowService,
                dataSnapshotService,
                opsSloService,
                gatewayProperties,
                dataSource
        );

        ChangePlanEntity entity = new ChangePlanEntity();
        entity.setPlanType("UPGRADE");
        entity.setExecutionClass("MANUAL");
        entity.setReleaseArtifactId(8L);
        entity.setMaintenanceWindowId(9L);
        entity.setStatus("APPROVED");

        var result = service.evaluate(entity, new ChangePlanExecuteRequest("ops", false, null, null, false));

        assertEquals(false, result.passed());
        assertEquals("FAILED", result.checks().stream().filter(item -> item.checkName().equals("maintenanceWindow")).findFirst().orElseThrow().status());
    }
}
