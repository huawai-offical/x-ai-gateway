package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.DistributedKeyClientConfigResponse;
import com.prodigalgal.xaigateway.admin.api.DistributedKeyInitialAccountGroupBindingRequest;
import com.prodigalgal.xaigateway.admin.api.DistributedKeyRequest;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeySecretService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeySecrets;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyAccountGroupBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeySecretExportGrantEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccountGroupBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccessGroupGrantRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeySecretExportGrantRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountGroupRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributedKeyAdminServiceTests {

    @Test
    void shouldExportClientConfigWithMaskedKeyOnly() {
        DistributedKeyRepository keyRepository = Mockito.mock(DistributedKeyRepository.class);
        DistributedKeySecretExportGrantRepository grantRepository =
                Mockito.mock(DistributedKeySecretExportGrantRepository.class);
        DistributedKeyAdminService service = new DistributedKeyAdminService(
                keyRepository,
                Mockito.mock(DistributedKeySecretService.class),
                Mockito.mock(DistributedKeyBindingRepository.class),
                Mockito.mock(DistributedKeyAccountGroupBindingRepository.class),
                Mockito.mock(DistributedKeyAccessGroupGrantRepository.class),
                grantRepository,
                Mockito.mock(GatewayUserRepository.class),
                Mockito.mock(UpstreamAccountGroupRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                Optional.empty()
        );
        DistributedKeyEntity entity = new DistributedKeyEntity();
        entity.setKeyName("codex-key");
        entity.setKeyPrefix("sk-gw-test");
        entity.setMaskedKey("sk-gw-test...abcd");
        ReflectionTestUtils.setField(entity, "id", 3L);
        Mockito.when(keyRepository.findById(3L)).thenReturn(Optional.of(entity));

        DistributedKeyClientConfigResponse response = service.exportClientConfig(
                3L,
                "auth-json",
                "CODEX",
                "https://gateway.example.com/v1/"
        );

        assertEquals("auth_json", response.format());
        assertEquals("CODEX", response.clientFamily());
        assertTrue(response.config().contains("https://gateway.example.com/v1"));
        assertTrue(response.config().contains("sk-gw-test...abcd"));
        assertFalse(response.config().contains("full-secret"));
        assertTrue(response.warning().contains("不会返回完整 secret"));
    }

    @Test
    void shouldExportMultiCliOnboardingPackWithoutFullSecret() {
        DistributedKeyRepository keyRepository = Mockito.mock(DistributedKeyRepository.class);
        DistributedKeyAdminService service = new DistributedKeyAdminService(
                keyRepository,
                Mockito.mock(DistributedKeySecretService.class),
                Mockito.mock(DistributedKeyBindingRepository.class),
                Mockito.mock(DistributedKeyAccountGroupBindingRepository.class),
                Mockito.mock(DistributedKeyAccessGroupGrantRepository.class),
                Mockito.mock(DistributedKeySecretExportGrantRepository.class),
                Mockito.mock(GatewayUserRepository.class),
                Mockito.mock(UpstreamAccountGroupRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                Optional.empty()
        );
        DistributedKeyEntity entity = new DistributedKeyEntity();
        entity.setKeyName("cli-key");
        entity.setKeyPrefix("sk-gw-cli");
        entity.setMaskedKey("sk-gw-cli...abcd");
        ReflectionTestUtils.setField(entity, "id", 4L);
        Mockito.when(keyRepository.findById(4L)).thenReturn(Optional.of(entity));

        var response = service.exportOnboardingPack(4L, "https://gateway.example.com/v1/");

        assertEquals("cli-key", response.keyName());
        assertEquals("https://gateway.example.com/v1", response.apiBaseUrl());
        assertTrue(response.clientConfigs().stream().anyMatch(item -> "CODEX".equals(item.clientFamily())));
        assertTrue(response.clientConfigs().stream().anyMatch(item -> "CLAUDE_CODE".equals(item.clientFamily())));
        assertTrue(response.clientConfigs().stream().anyMatch(item -> "GEMINI_CLI".equals(item.clientFamily())));
        assertTrue(response.clientConfigs().stream().anyMatch(item -> "CURSOR".equals(item.clientFamily())));
        assertTrue(response.clientConfigs().stream().anyMatch(item -> "WINDSURF".equals(item.clientFamily())));
        assertTrue(response.clientConfigs().stream().anyMatch(item -> "KIRO".equals(item.clientFamily())));
        assertTrue(response.clientConfigs().stream().anyMatch(item -> "GITHUB_COPILOT".equals(item.clientFamily())));
        assertTrue(response.clientConfigs().stream().anyMatch(item -> item.content().contains("X_AI_GATEWAY_CLIENT_INSTANCE")));
        assertTrue(response.deepLinks().stream().anyMatch(item -> item.url().startsWith("xag://import")));
        assertTrue(response.deepLinks().stream().anyMatch(item -> item.warning().contains("不需要本地 proxy")));
        assertTrue(response.mcpServerConfig().contains("mcpServers"));
        assertTrue(response.troubleshooting().stream().anyMatch(item -> item.contains("401")));
        assertTrue(response.troubleshooting().stream().anyMatch(item -> item.contains("request filter")));
        assertTrue(response.clientConfigs().stream().noneMatch(item -> item.content().contains("full-secret")));
    }

    @Test
    void shouldNormalizeOpenCodeAndOpenClawClientFamilies() {
        assertEquals(
                "OPENCODE",
                com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily.from("opencode").name()
        );
        assertEquals(
                "OPENCLAW",
                com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily.from("open-claw").name()
        );
        assertEquals(
                "GITHUB_COPILOT",
                com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily.from("copilot").name()
        );
    }

    @Test
    void shouldRejectActivationWithoutActiveAccountGroupBinding() {
        DistributedKeyRepository keyRepository = Mockito.mock(DistributedKeyRepository.class);
        DistributedKeyAccountGroupBindingRepository accountGroupBindingRepository =
                Mockito.mock(DistributedKeyAccountGroupBindingRepository.class);
        DistributedKeyAdminService service = new DistributedKeyAdminService(
                keyRepository,
                Mockito.mock(DistributedKeySecretService.class),
                Mockito.mock(DistributedKeyBindingRepository.class),
                accountGroupBindingRepository,
                Mockito.mock(DistributedKeyAccessGroupGrantRepository.class),
                Mockito.mock(DistributedKeySecretExportGrantRepository.class),
                Mockito.mock(GatewayUserRepository.class),
                Mockito.mock(UpstreamAccountGroupRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                Optional.empty()
        );
        DistributedKeyEntity entity = new DistributedKeyEntity();
        ReflectionTestUtils.setField(entity, "id", 12L);
        entity.setKeyName("gateway-key");
        entity.setKeyPrefix("sk-gw-test");
        entity.setMaskedKey("sk-gw-test...cret");
        entity.setActive(false);
        Mockito.when(keyRepository.findById(12L)).thenReturn(Optional.of(entity));
        Mockito.when(accountGroupBindingRepository.countByDistributedKey_IdAndActiveTrueAndGroup_ActiveTrue(12L))
                .thenReturn(0L);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.toggle(12L, true)
        );

        assertTrue(exception.getMessage().contains("已启用的账号分组"));
        Mockito.verify(keyRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void shouldCreateOneTimeSecretExportGrantAndConsumeOnlyOnce() {
        DistributedKeyRepository keyRepository = Mockito.mock(DistributedKeyRepository.class);
        DistributedKeySecretExportGrantRepository grantRepository =
                Mockito.mock(DistributedKeySecretExportGrantRepository.class);
        DistributedKeySecretService secretService = Mockito.mock(DistributedKeySecretService.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        AtomicReference<DistributedKeySecretExportGrantEntity> persistedGrant = new AtomicReference<>();
        DistributedKeyAdminService service = new DistributedKeyAdminService(
                keyRepository,
                secretService,
                Mockito.mock(DistributedKeyBindingRepository.class),
                Mockito.mock(DistributedKeyAccountGroupBindingRepository.class),
                Mockito.mock(DistributedKeyAccessGroupGrantRepository.class),
                grantRepository,
                Mockito.mock(GatewayUserRepository.class),
                Mockito.mock(UpstreamAccountGroupRepository.class),
                cryptoService,
                Optional.empty()
        );
        Mockito.when(secretService.generate()).thenReturn(new DistributedKeySecrets(
                "sk-gw-test",
                "sk-gw-test-full-secret",
                "hash",
                "sk-gw-test...cret"
        ));
        Mockito.when(keyRepository.save(Mockito.any())).thenAnswer(invocation -> {
            DistributedKeyEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 9L);
            return entity;
        });
        Mockito.when(cryptoService.fingerprint(Mockito.anyString()))
                .thenAnswer(invocation -> "hash-" + invocation.getArgument(0, String.class));
        Mockito.when(cryptoService.encrypt("sk-gw-test-full-secret")).thenReturn("cipher-full-key");
        Mockito.when(cryptoService.decrypt("cipher-full-key")).thenReturn("sk-gw-test-full-secret");
        Mockito.when(grantRepository.save(Mockito.any())).thenAnswer(invocation -> {
            DistributedKeySecretExportGrantEntity grant = invocation.getArgument(0);
            if (grant.getId() == null) {
                ReflectionTestUtils.setField(grant, "id", 77L);
            }
            persistedGrant.set(grant);
            return grant;
        });
        Mockito.when(grantRepository.findByDistributedKey_IdAndTokenHash(Mockito.eq(9L), Mockito.anyString()))
                .thenAnswer(invocation -> {
                    DistributedKeySecretExportGrantEntity grant = persistedGrant.get();
                    String tokenHash = invocation.getArgument(1);
                    if (grant != null && tokenHash.equals(grant.getTokenHash())) {
                        return Optional.of(grant);
                    }
                    return Optional.empty();
                });
        Mockito.when(keyRepository.findById(9L)).thenAnswer(invocation -> {
            DistributedKeyEntity entity = new DistributedKeyEntity();
            ReflectionTestUtils.setField(entity, "id", 9L);
            entity.setKeyName("codex-key");
            entity.setKeyPrefix("sk-gw-test");
            entity.setMaskedKey("sk-gw-test...cret");
            return Optional.of(entity);
        });

        var created = service.create(new DistributedKeyRequest(
                "codex-key",
                null,
                null,
                false,
                java.util.List.of("openai"),
                java.util.List.of("gpt-4o-mini"),
                java.util.List.of("OPENAI_DIRECT"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                java.util.List.of("CODEX"),
                false,
                List.of()
        ));

        assertEquals("sk-gw-test-full-secret", created.fullKey());
        assertTrue(created.oneTimeExportToken() != null && !created.oneTimeExportToken().isBlank());
        assertTrue(created.oneTimeExportExpiresAt() != null);
        assertEquals("hash-" + created.oneTimeExportToken(), persistedGrant.get().getTokenHash());
        assertEquals("cipher-full-key", persistedGrant.get().getFullKeyCiphertext());

        DistributedKeyClientConfigResponse config = service.consumeOneTimeClientConfig(
                9L,
                created.oneTimeExportToken(),
                "env",
                "CODEX",
                "https://gateway.example.com"
        );

        assertTrue(config.config().contains("sk-gw-test-full-secret"));
        assertTrue(config.warning().contains("已消费"));
        assertTrue(persistedGrant.get().isConsumed());
        assertThrows(IllegalArgumentException.class, () -> service.consumeOneTimeClientConfig(
                9L,
                created.oneTimeExportToken(),
                "env",
                "CODEX",
                "https://gateway.example.com"
        ));
    }

    @Test
    void shouldRejectRevokedOneTimeSecretExportGrant() {
        DistributedKeyRepository keyRepository = Mockito.mock(DistributedKeyRepository.class);
        DistributedKeySecretExportGrantRepository grantRepository =
                Mockito.mock(DistributedKeySecretExportGrantRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        DistributedKeyAdminService service = new DistributedKeyAdminService(
                keyRepository,
                Mockito.mock(DistributedKeySecretService.class),
                Mockito.mock(DistributedKeyBindingRepository.class),
                Mockito.mock(DistributedKeyAccountGroupBindingRepository.class),
                Mockito.mock(DistributedKeyAccessGroupGrantRepository.class),
                grantRepository,
                Mockito.mock(GatewayUserRepository.class),
                Mockito.mock(UpstreamAccountGroupRepository.class),
                cryptoService,
                Optional.empty()
        );
        DistributedKeyEntity entity = new DistributedKeyEntity();
        ReflectionTestUtils.setField(entity, "id", 11L);
        entity.setKeyName("codex-key");
        entity.setKeyPrefix("sk-gw-test");
        entity.setMaskedKey("sk-gw-test...cret");
        DistributedKeySecretExportGrantEntity grant = new DistributedKeySecretExportGrantEntity();
        grant.setDistributedKey(entity);
        grant.setTokenHash("hash-token");
        grant.setFullKeyCiphertext("cipher-full-key");
        grant.setExpiresAt(Instant.now().plusSeconds(60));
        Mockito.when(keyRepository.findById(11L)).thenReturn(Optional.of(entity));
        Mockito.when(cryptoService.fingerprint("token")).thenReturn("hash-token");
        Mockito.when(grantRepository.findByDistributedKey_IdAndTokenHash(11L, "hash-token"))
                .thenReturn(Optional.of(grant));

        var revoked = service.revokeOneTimeClientConfig(11L, "token");

        assertTrue(revoked.revoked());
        assertTrue(grant.isRevoked());
        assertThrows(IllegalArgumentException.class, () -> service.consumeOneTimeClientConfig(
                11L,
                "token",
                "env",
                "CODEX",
                "https://gateway.example.com"
        ));
        Mockito.verify(cryptoService, Mockito.never()).decrypt(Mockito.anyString());
    }

    @Test
    void shouldCreateActiveKeyWithInitialAccountGroupBinding() {
        DistributedKeyRepository keyRepository = Mockito.mock(DistributedKeyRepository.class);
        DistributedKeyAccountGroupBindingRepository accountGroupBindingRepository =
                Mockito.mock(DistributedKeyAccountGroupBindingRepository.class);
        UpstreamAccountGroupRepository groupRepository = Mockito.mock(UpstreamAccountGroupRepository.class);
        DistributedKeySecretService secretService = Mockito.mock(DistributedKeySecretService.class);
        DistributedKeySecretExportGrantRepository grantRepository =
                Mockito.mock(DistributedKeySecretExportGrantRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        AtomicReference<DistributedKeyAccountGroupBindingEntity> persistedBinding = new AtomicReference<>();
        DistributedKeyAdminService service = new DistributedKeyAdminService(
                keyRepository,
                secretService,
                Mockito.mock(DistributedKeyBindingRepository.class),
                accountGroupBindingRepository,
                Mockito.mock(DistributedKeyAccessGroupGrantRepository.class),
                grantRepository,
                Mockito.mock(GatewayUserRepository.class),
                groupRepository,
                cryptoService,
                Optional.empty()
        );
        Mockito.when(secretService.generate()).thenReturn(new DistributedKeySecrets(
                "sk-gw-active",
                "sk-gw-active-full-secret",
                "hash",
                "sk-gw-active...cret"
        ));
        Mockito.when(keyRepository.save(Mockito.any())).thenAnswer(invocation -> {
            DistributedKeyEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 31L);
            return entity;
        });
        UpstreamAccountGroupEntity group = new UpstreamAccountGroupEntity();
        ReflectionTestUtils.setField(group, "id", 4L);
        group.setGroupName("MiMo Group");
        group.setActive(true);
        Mockito.when(groupRepository.findById(4L)).thenReturn(Optional.of(group));
        Mockito.when(accountGroupBindingRepository.save(Mockito.any())).thenAnswer(invocation -> {
            DistributedKeyAccountGroupBindingEntity binding = invocation.getArgument(0);
            persistedBinding.set(binding);
            return binding;
        });
        Mockito.when(accountGroupBindingRepository.countByDistributedKey_IdAndActiveTrueAndGroup_ActiveTrue(31L))
                .thenReturn(1L);
        Mockito.when(cryptoService.fingerprint(Mockito.anyString())).thenReturn("grant-token-hash");
        Mockito.when(cryptoService.encrypt("sk-gw-active-full-secret")).thenReturn("cipher-full-key");

        var created = service.create(new DistributedKeyRequest(
                "active-key",
                null,
                null,
                true,
                List.of("xiaomi_mimo.openai_compatible"),
                List.of("mimo-v2-pro"),
                List.of("OPENAI_COMPATIBLE"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of("GENERIC_OPENAI"),
                false,
                List.of(new DistributedKeyInitialAccountGroupBindingRequest(
                        4L,
                        ProviderType.OPENAI_COMPATIBLE,
                        10,
                        true
                ))
        ));

        assertTrue(created.record().active());
        assertEquals("active-key", created.record().keyName());
        assertEquals(ProviderType.OPENAI_COMPATIBLE, persistedBinding.get().getProviderType());
        assertEquals(10, persistedBinding.get().getPriority());
        assertEquals(31L, persistedBinding.get().getDistributedKey().getId());
        assertEquals(4L, persistedBinding.get().getGroup().getId());
    }

    @Test
    void shouldRejectActiveCreateWithoutInitialActiveGroupBinding() {
        DistributedKeyRepository keyRepository = Mockito.mock(DistributedKeyRepository.class);
        DistributedKeyAccountGroupBindingRepository accountGroupBindingRepository =
                Mockito.mock(DistributedKeyAccountGroupBindingRepository.class);
        DistributedKeySecretService secretService = Mockito.mock(DistributedKeySecretService.class);
        DistributedKeyAdminService service = new DistributedKeyAdminService(
                keyRepository,
                secretService,
                Mockito.mock(DistributedKeyBindingRepository.class),
                accountGroupBindingRepository,
                Mockito.mock(DistributedKeyAccessGroupGrantRepository.class),
                Mockito.mock(DistributedKeySecretExportGrantRepository.class),
                Mockito.mock(GatewayUserRepository.class),
                Mockito.mock(UpstreamAccountGroupRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                Optional.empty()
        );
        Mockito.when(secretService.generate()).thenReturn(new DistributedKeySecrets(
                "sk-gw-active",
                "sk-gw-active-full-secret",
                "hash",
                "sk-gw-active...cret"
        ));
        Mockito.when(keyRepository.save(Mockito.any())).thenAnswer(invocation -> {
            DistributedKeyEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 32L);
            return entity;
        });
        Mockito.when(accountGroupBindingRepository.countByDistributedKey_IdAndActiveTrueAndGroup_ActiveTrue(32L))
                .thenReturn(0L);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(new DistributedKeyRequest(
                        "active-key",
                        null,
                        null,
                        true,
                        List.of("openai.native"),
                        List.of("gpt-4o-mini"),
                        List.of("OPENAI_DIRECT"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of("GENERIC_OPENAI"),
                        false,
                        List.of()
                ))
        );

        assertTrue(exception.getMessage().contains("已启用的账号分组"));
        Mockito.verify(accountGroupBindingRepository, Mockito.never()).save(Mockito.any());
    }
}
