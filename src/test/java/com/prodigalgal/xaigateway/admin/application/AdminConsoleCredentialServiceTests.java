package com.prodigalgal.xaigateway.admin.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.admin.api.AdminAuthSettingsResponse;
import com.prodigalgal.xaigateway.admin.api.AdminAuthSettingsUpdateRequest;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayUnauthorizedException;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.SystemSettingEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.SystemSettingRepository;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminConsoleCredentialServiceTests {

    @Mock
    private SystemSettingRepository systemSettingRepository;

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<SystemSettingEntity> persistedEntity = new AtomicReference<>();

    private GatewayProperties gatewayProperties;
    private AdminConsoleCredentialService service;

    @BeforeEach
    void setUp() {
        gatewayProperties = new GatewayProperties();
        gatewayProperties.getAdminConsole().setUsername("console-admin");
        gatewayProperties.getAdminConsole().setPassword(null);

        lenient().when(systemSettingRepository.findBySettingKey(AdminConsoleCredentialService.SETTING_KEY))
                .thenAnswer(invocation -> Optional.ofNullable(persistedEntity.get()));
        lenient().when(systemSettingRepository.save(any(SystemSettingEntity.class)))
                .thenAnswer(invocation -> {
                    SystemSettingEntity entity = invocation.getArgument(0);
                    persistedEntity.set(entity);
                    return entity;
                });

        service = new AdminConsoleCredentialService(
                systemSettingRepository,
                gatewayProperties,
                passwordEncoder,
                objectMapper
        );
    }

    @Test
    void shouldGenerateRandomPasswordAndPersistWhenNoBootstrapPasswordConfigured() throws Exception {
        AdminAuthSettingsResponse response = service.getSettings();

        assertEquals("console-admin", response.username());
        assertTrue(response.persisted());
        assertEquals("RANDOM_BOOTSTRAP", response.credentialSource());
        assertNotNull(persistedEntity.get());

        JsonNode stored = objectMapper.readTree(persistedEntity.get().getSettingValue());
        assertEquals("console-admin", stored.get("username").asText());
        assertEquals("RANDOM_BOOTSTRAP", stored.get("source").asText());
        assertTrue(stored.get("passwordHash").asText().startsWith("{"));
    }

    @Test
    void shouldPersistConfiguredBootstrapPasswordAndMatchLogin() throws Exception {
        gatewayProperties.getAdminConsole().setPassword("bootstrap-secret-123");
        service = new AdminConsoleCredentialService(
                systemSettingRepository,
                gatewayProperties,
                passwordEncoder,
                objectMapper
        );

        assertTrue(service.matches("console-admin", "bootstrap-secret-123"));
        assertFalse(service.matches("console-admin", "wrong-secret"));

        JsonNode stored = objectMapper.readTree(persistedEntity.get().getSettingValue());
        assertEquals("CONFIG_PASSWORD", stored.get("source").asText());
    }

    @Test
    void shouldUpdateUsernameAndPasswordAfterCurrentPasswordVerification() {
        gatewayProperties.getAdminConsole().setPassword("bootstrap-secret-123");
        service = new AdminConsoleCredentialService(
                systemSettingRepository,
                gatewayProperties,
                passwordEncoder,
                objectMapper
        );

        service.getSettings();

        AdminAuthSettingsResponse updated = service.updateSettings(new AdminAuthSettingsUpdateRequest(
                "rotated-admin",
                "bootstrap-secret-123",
                "rotated-secret-456"
        ));

        assertEquals("rotated-admin", updated.username());
        assertEquals("MANUAL_UPDATE", updated.credentialSource());
        assertTrue(service.matches("rotated-admin", "rotated-secret-456"));
        assertFalse(service.matches("console-admin", "bootstrap-secret-123"));
    }

    @Test
    void shouldRejectUpdateWhenCurrentPasswordIsWrong() {
        gatewayProperties.getAdminConsole().setPassword("bootstrap-secret-123");
        service = new AdminConsoleCredentialService(
                systemSettingRepository,
                gatewayProperties,
                passwordEncoder,
                objectMapper
        );

        service.getSettings();

        assertThrows(GatewayUnauthorizedException.class, () -> service.updateSettings(
                new AdminAuthSettingsUpdateRequest(
                        "rotated-admin",
                        "wrong-current-password",
                        "rotated-secret-456"
                )
        ));
    }
}
