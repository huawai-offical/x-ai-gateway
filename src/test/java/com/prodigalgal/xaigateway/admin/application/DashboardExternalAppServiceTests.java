package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.DashboardExternalAppRequest;
import com.prodigalgal.xaigateway.admin.api.ExternalAppVerifyRequest;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.DashboardExternalAppEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DashboardExternalAppRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardExternalAppServiceTests {

    @Test
    void shouldPreviewAndVerifySignedContext() {
        DashboardExternalAppRepository repository = Mockito.mock(DashboardExternalAppRepository.class);
        CredentialCryptoService cryptoService = new CredentialCryptoService(new GatewayProperties());
        cryptoService.init();
        OpsAuditService opsAuditService = Mockito.mock(OpsAuditService.class);
        DashboardExternalAppService service = new DashboardExternalAppService(repository, cryptoService, new ObjectMapper(), opsAuditService);

        Mockito.when(repository.existsBySlug("ops-panel")).thenReturn(false);
        Mockito.when(repository.save(Mockito.any())).thenAnswer(invocation -> {
            DashboardExternalAppEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 9L);
            return entity;
        });

        service.create(new DashboardExternalAppRequest(
                "Ops Panel",
                "ops-panel",
                "https://ops.example.com/embed",
                "https://ops.example.com",
                "allow-scripts",
                "secret",
                true,
                true,
                null
        ));
        DashboardExternalAppEntity saved = Mockito.mockingDetails(repository).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("save"))
                .map(invocation -> (DashboardExternalAppEntity) invocation.getArgument(0))
                .findFirst()
                .orElseThrow();
        Mockito.when(repository.findById(9L)).thenReturn(Optional.of(saved));
        Mockito.when(repository.findBySlug("ops-panel")).thenReturn(Optional.of(saved));

        var preview = service.preview(9L, "https://ops.example.com", "console", 300);
        var verified = service.verify("ops-panel", new ExternalAppVerifyRequest(preview.origin(), preview.context(), preview.signature()));
        var wrongOrigin = service.verify("ops-panel", new ExternalAppVerifyRequest("https://evil.example.com", preview.context(), preview.signature()));

        assertTrue(verified.valid());
        assertFalse(wrongOrigin.valid());
    }
}
