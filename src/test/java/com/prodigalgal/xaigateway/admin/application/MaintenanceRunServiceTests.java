package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.MaintenanceRunRequest;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.MaintenanceRunEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.MaintenanceRunRepository;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaintenanceRunServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldRequireConfirmForRealBackupAndCreateArtifactWhenConfirmed() {
        MaintenanceRunRepository repository = Mockito.mock(MaintenanceRunRepository.class);
        GatewayProperties properties = new GatewayProperties();
        properties.getStorage().setFileRoot(tempDir.toString());
        MaintenanceRunService service = new MaintenanceRunService(repository, properties, new ObjectMapper(), Mockito.mock(OpsAuditService.class));

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
        assertTrue(response.detailJson().contains("\"checks\""));
        assertTrue(response.detailJson().contains("\"summary\""));
    }
}
