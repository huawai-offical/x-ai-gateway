package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.MaintenanceRunRequest;
import com.prodigalgal.xaigateway.admin.api.MaintenanceRunResponse;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.MaintenanceRunEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.MaintenanceRunRepository;
import com.prodigalgal.xaigateway.smoke.SmokeHarnessSupport;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

class OpsMaintenanceSmokeHarnessTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldRunMaintenanceDryRunWhenSmokeEnabled() {
        Assumptions.assumeTrue(
                SmokeHarnessSupport.enabled("XAG_SMOKE_OPS_DRY_RUN"),
                "设置 XAG_SMOKE_OPS_DRY_RUN=true 后才执行 Ops dry-run smoke。"
        );
        MaintenanceRunRepository repository = Mockito.mock(MaintenanceRunRepository.class);
        OpsAuditService opsAuditService = Mockito.mock(OpsAuditService.class);
        GatewayProperties properties = new GatewayProperties();
        properties.getStorage().setFileRoot(tempDir.resolve("files").toString());
        Mockito.when(repository.save(any())).thenAnswer(invocation -> {
            MaintenanceRunEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 100L + entity.getRunType().length());
            return entity;
        });
        MaintenanceRunService service = new MaintenanceRunService(repository, properties, new ObjectMapper(), opsAuditService);

        List<MaintenanceRunResponse> responses = List.of(
                service.execute(new MaintenanceRunRequest("PRECHECK", true, false, "smoke", "smoke-precheck", "{}")),
                service.execute(new MaintenanceRunRequest("UPGRADE_CHECK", true, false, "smoke", "smoke-upgrade", "{\"targetVersion\":\"2026.05.05\"}")),
                service.execute(new MaintenanceRunRequest("ROLLBACK_PLAN", true, false, "smoke", "smoke-rollback", "{\"rollback\":\"release-previous\"}"))
        );

        StringBuilder report = new StringBuilder();
        for (MaintenanceRunResponse response : responses) {
            assertEquals("COMPLETED", response.status());
            assertTrue(response.dryRun());
            assertTrue(response.detailJson().contains("\"summary\""));
            assertTrue(response.detailJson().contains("\"checks\""));
            report.append("- ")
                    .append(response.runType())
                    .append(": ")
                    .append(response.status())
                    .append(", checksum=")
                    .append(response.artifactChecksum())
                    .append("\n");
        }
        Mockito.verify(opsAuditService, Mockito.times(3))
                .record(Mockito.eq("MAINTENANCE"), Mockito.startsWith("RUN_"), Mockito.eq("maintenance_run"), Mockito.anyString(), Mockito.anyString());
        SmokeHarnessSupport.writeReport("ops-maintenance-dry-run", report.toString());
    }
}
