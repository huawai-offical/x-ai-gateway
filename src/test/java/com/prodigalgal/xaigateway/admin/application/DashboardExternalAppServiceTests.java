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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void shouldReturnRuntimeBySlugWithSignedContext() {
        DashboardExternalAppRepository repository = Mockito.mock(DashboardExternalAppRepository.class);
        CredentialCryptoService cryptoService = new CredentialCryptoService(new GatewayProperties());
        cryptoService.init();
        OpsAuditService opsAuditService = Mockito.mock(OpsAuditService.class);
        DashboardExternalAppService service = new DashboardExternalAppService(repository, cryptoService, new ObjectMapper(), opsAuditService);
        DashboardExternalAppEntity entity = appEntity(11L, true, true, "https://grafana.example.com/d/live", "https://grafana.example.com");
        Mockito.when(repository.findBySlug("grafana-panel")).thenReturn(Optional.of(entity));

        var runtime = service.runtime("grafana-panel", null, "console-extension-runtime", 300);

        assertTrue(runtime.runnable());
        assertEquals("READY", runtime.runtimeStatus());
        assertEquals("https://grafana.example.com", runtime.actualOrigin());
        assertNotNull(runtime.signedContext());
        assertEquals("grafana-panel", runtime.app().slug());
        assertTrue(runtime.signedContext().launchUrl().contains("x_context="));
    }

    @Test
    void shouldBlockRuntimeWhenNavigationIsDisabled() {
        DashboardExternalAppRepository repository = Mockito.mock(DashboardExternalAppRepository.class);
        CredentialCryptoService cryptoService = new CredentialCryptoService(new GatewayProperties());
        cryptoService.init();
        OpsAuditService opsAuditService = Mockito.mock(OpsAuditService.class);
        DashboardExternalAppService service = new DashboardExternalAppService(repository, cryptoService, new ObjectMapper(), opsAuditService);
        DashboardExternalAppEntity entity = appEntity(12L, true, false, "https://hidden.example.com/app", "https://hidden.example.com");
        Mockito.when(repository.findBySlug("hidden-app")).thenReturn(Optional.of(entity));

        var runtime = service.runtime("hidden-app", null, "console-extension-runtime", 300);

        assertFalse(runtime.runnable());
        assertEquals("NAV_DISABLED", runtime.runtimeStatus());
        assertNull(runtime.signedContext());
    }

    @Test
    void shouldBlockRuntimeWhenIframeOriginMismatchesAllowedOrigin() {
        DashboardExternalAppRepository repository = Mockito.mock(DashboardExternalAppRepository.class);
        CredentialCryptoService cryptoService = new CredentialCryptoService(new GatewayProperties());
        cryptoService.init();
        OpsAuditService opsAuditService = Mockito.mock(OpsAuditService.class);
        DashboardExternalAppService service = new DashboardExternalAppService(repository, cryptoService, new ObjectMapper(), opsAuditService);
        DashboardExternalAppEntity entity = appEntity(13L, true, true, "https://evil.example.com/app", "https://trusted.example.com");
        Mockito.when(repository.findBySlug("mismatched-app")).thenReturn(Optional.of(entity));

        var runtime = service.runtime("mismatched-app", null, "console-extension-runtime", 300);

        assertFalse(runtime.runnable());
        assertEquals("ORIGIN_MISMATCH", runtime.runtimeStatus());
        assertEquals("https://evil.example.com", runtime.actualOrigin());
        assertNull(runtime.signedContext());
    }

    private DashboardExternalAppEntity appEntity(
            Long id,
            boolean enabled,
            boolean navEnabled,
            String iframeUrl,
            String allowedOrigin) {
        CredentialCryptoService cryptoService = new CredentialCryptoService(new GatewayProperties());
        cryptoService.init();
        DashboardExternalAppEntity entity = new DashboardExternalAppEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setAppName("Grafana 面板");
        entity.setSlug(id == 12L ? "hidden-app" : id == 13L ? "mismatched-app" : "grafana-panel");
        entity.setIframeUrl(iframeUrl);
        entity.setAllowedOrigin(allowedOrigin);
        entity.setSandboxPermissions("allow-scripts allow-forms");
        entity.setEnabled(enabled);
        entity.setNavEnabled(navEnabled);
        entity.setSigningSecretCiphertext(cryptoService.encrypt("secret"));
        entity.setSigningSecretFingerprint(cryptoService.fingerprint("secret"));
        return entity;
    }
}
