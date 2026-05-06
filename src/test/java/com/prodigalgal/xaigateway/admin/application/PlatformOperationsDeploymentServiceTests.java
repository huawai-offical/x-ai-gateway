package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.ops.OpsEventBusService;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.repository.BackupJobRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.InstallationStateRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.ReleaseArtifactRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RestoreJobRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.RollbackJobRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SystemSettingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpgradeJobRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformOperationsDeploymentServiceTests {

    @Test
    void shouldReturnDeploymentManifestAndPreflightWarnings() {
        GatewayProperties properties = new GatewayProperties();
        PlatformOperationsService service = new PlatformOperationsService(
                Mockito.mock(InstallationStateRepository.class),
                Mockito.mock(BackupJobRepository.class),
                Mockito.mock(RestoreJobRepository.class),
                Mockito.mock(ReleaseArtifactRepository.class),
                Mockito.mock(UpgradeJobRepository.class),
                Mockito.mock(RollbackJobRepository.class),
                Mockito.mock(SystemSettingRepository.class),
                properties,
                new ObjectMapper(),
                Mockito.mock(OpsAuditService.class),
                Mockito.mock(OpsEventBusService.class)
        );

        var manifest = service.deploymentManifest("compose");
        var preflight = service.deploymentPreflight("2026.05.06", "compose");

        assertEquals("compose", manifest.profile());
        assertTrue(manifest.requiredFiles().stream().anyMatch(file -> "deploy/docker-compose.yml".equals(file.path()) && file.present()));
        assertTrue(manifest.commands().stream().anyMatch(command -> command.contains("install.ps1")));
        assertFalse(preflight.checks().isEmpty());
        assertEquals(0, preflight.blockingCount());
        assertEquals("WARN", preflight.status());
        assertTrue(preflight.checks().stream().anyMatch(check -> "redis:runtime-store".equals(check.code())));
        assertTrue(preflight.upgradeCommands().stream().anyMatch(command -> command.contains("2026.05.06")));
    }
}
