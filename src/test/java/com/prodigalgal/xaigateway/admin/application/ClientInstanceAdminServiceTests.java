package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ClientInstanceAuthorizationRequest;
import com.prodigalgal.xaigateway.admin.api.ClientInstanceConfigResponse;
import com.prodigalgal.xaigateway.admin.api.ClientInstanceRequest;
import com.prodigalgal.xaigateway.infra.persistence.entity.ClientInstanceEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.ClientInstanceGrantEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeySecretExportGrantEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.ClientInstanceGrantRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.ClientInstanceRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeySecretExportGrantRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientInstanceAdminServiceTests {

    @Test
    void shouldRegisterIssueAndConsumeClientInstanceAuthorizationOnlyOnce() {
        ClientInstanceRepository instanceRepository = Mockito.mock(ClientInstanceRepository.class);
        ClientInstanceGrantRepository grantRepository = Mockito.mock(ClientInstanceGrantRepository.class);
        DistributedKeyRepository keyRepository = Mockito.mock(DistributedKeyRepository.class);
        DistributedKeySecretExportGrantRepository sourceGrantRepository = Mockito.mock(DistributedKeySecretExportGrantRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        ClientInstanceAdminService service = new ClientInstanceAdminService(
                instanceRepository,
                grantRepository,
                keyRepository,
                sourceGrantRepository,
                cryptoService,
                new ObjectMapper(),
                Optional.empty()
        );
        DistributedKeyEntity key = distributedKey(9L);
        AtomicReference<ClientInstanceEntity> savedInstance = new AtomicReference<>();
        AtomicReference<ClientInstanceGrantEntity> savedGrant = new AtomicReference<>();
        Mockito.when(keyRepository.findById(9L)).thenReturn(Optional.of(key));
        Mockito.when(instanceRepository.findByDistributedKey_IdAndInstanceId(9L, "codex-main"))
                .thenReturn(Optional.empty());
        Mockito.when(instanceRepository.save(Mockito.any())).thenAnswer(invocation -> {
            ClientInstanceEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                ReflectionTestUtils.setField(entity, "id", 21L);
            }
            savedInstance.set(entity);
            return entity;
        });
        Mockito.when(instanceRepository.findById(21L)).thenAnswer(invocation -> Optional.of(savedInstance.get()));
        Mockito.when(grantRepository.save(Mockito.any())).thenAnswer(invocation -> {
            ClientInstanceGrantEntity grant = invocation.getArgument(0);
            if (grant.getId() == null) {
                ReflectionTestUtils.setField(grant, "id", 31L);
            }
            savedGrant.set(grant);
            return grant;
        });
        Mockito.when(grantRepository.findByClientInstance_IdAndTokenHash(Mockito.eq(21L), Mockito.anyString()))
                .thenAnswer(invocation -> {
                    ClientInstanceGrantEntity grant = savedGrant.get();
                    if (grant != null && grant.getTokenHash().equals(invocation.getArgument(1))) {
                        return Optional.of(grant);
                    }
                    return Optional.empty();
                });
        Mockito.when(cryptoService.fingerprint(Mockito.anyString())).thenAnswer(invocation -> "hash-" + invocation.getArgument(0));
        Mockito.when(cryptoService.encrypt("sk-gw-full-secret")).thenReturn("cipher-full-secret");
        Mockito.when(cryptoService.decrypt("cipher-full-secret")).thenReturn("sk-gw-full-secret");

        var registered = service.register(new ClientInstanceRequest(
                9L,
                "codex",
                "Codex Main",
                "Codex on Laptop",
                "repo-a",
                "x-ai-gateway-vscode",
                "1.0.0",
                "xag",
                "{\"api_key\":\"should-not-store\"}",
                true
        ));

        assertEquals("codex-main", registered.instanceId());
        assertEquals("CODEX", registered.clientFamily());
        assertTrue(registered.metadataJson().contains("\"api_key\":\"***\""));

        var grant = service.issueAuthorization(21L, new ClientInstanceAuthorizationRequest(
                "env",
                "https://gateway.example.com/v1/",
                "PLUGIN",
                600,
                null,
                "sk-gw-full-secret",
                null,
                "x-ai-gateway-vscode",
                "1.0.0"
        ));

        assertNotNull(grant.grantToken());
        assertTrue(grant.deepLinkUrl().startsWith("xag://authorize/client-instance"));
        assertTrue(grant.deepLinkUrl().contains("clientInstance=codex-main"));
        assertFalse(grant.deepLinkUrl().contains("sk-gw-full-secret"));
        assertFalse(grant.pluginMessageJson().contains("sk-gw-full-secret"));
        assertEquals("cipher-full-secret", savedGrant.get().getFullKeyCiphertext());

        ClientInstanceConfigResponse config = service.consumeAuthorization(21L, grant.grantToken());

        assertTrue(config.config().contains("sk-gw-full-secret"));
        assertTrue(config.config().contains("X_AI_GATEWAY_CLIENT_INSTANCE=\"codex-main\""));
        assertTrue(config.warning().contains("不能再次使用"));
        assertTrue(savedGrant.get().isConsumed());
        assertThrows(IllegalArgumentException.class, () -> service.consumeAuthorization(21L, grant.grantToken()));
    }

    @Test
    void shouldRejectRevokedClientInstanceGrantWithoutDecryptingSecret() {
        ClientInstanceRepository instanceRepository = Mockito.mock(ClientInstanceRepository.class);
        ClientInstanceGrantRepository grantRepository = Mockito.mock(ClientInstanceGrantRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        ClientInstanceAdminService service = new ClientInstanceAdminService(
                instanceRepository,
                grantRepository,
                Mockito.mock(DistributedKeyRepository.class),
                Mockito.mock(DistributedKeySecretExportGrantRepository.class),
                cryptoService,
                new ObjectMapper(),
                Optional.empty()
        );
        ClientInstanceEntity instance = clientInstance(21L, distributedKey(9L));
        ClientInstanceGrantEntity grant = grant(instance, "hash-token", "cipher-full-secret", Instant.now().plusSeconds(60));
        Mockito.when(instanceRepository.findById(21L)).thenReturn(Optional.of(instance));
        Mockito.when(cryptoService.fingerprint("token")).thenReturn("hash-token");
        Mockito.when(grantRepository.findByClientInstance_IdAndTokenHash(21L, "hash-token")).thenReturn(Optional.of(grant));
        Mockito.when(grantRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

        var revoked = service.revokeAuthorization(21L, "token");

        assertTrue(revoked.revoked());
        assertThrows(IllegalArgumentException.class, () -> service.consumeAuthorization(21L, "token"));
        Mockito.verify(cryptoService, Mockito.never()).decrypt(Mockito.anyString());
    }

    @Test
    void shouldIssueClientInstanceGrantFromDistributedKeySecretExportToken() {
        ClientInstanceRepository instanceRepository = Mockito.mock(ClientInstanceRepository.class);
        ClientInstanceGrantRepository grantRepository = Mockito.mock(ClientInstanceGrantRepository.class);
        DistributedKeySecretExportGrantRepository sourceGrantRepository = Mockito.mock(DistributedKeySecretExportGrantRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        ClientInstanceAdminService service = new ClientInstanceAdminService(
                instanceRepository,
                grantRepository,
                Mockito.mock(DistributedKeyRepository.class),
                sourceGrantRepository,
                cryptoService,
                new ObjectMapper(),
                Optional.empty()
        );
        ClientInstanceEntity instance = clientInstance(21L, distributedKey(9L));
        DistributedKeySecretExportGrantEntity sourceGrant = new DistributedKeySecretExportGrantEntity();
        sourceGrant.setDistributedKey(instance.getDistributedKey());
        sourceGrant.setTokenHash("hash-source-token");
        sourceGrant.setFullKeyCiphertext("cipher-source-full-key");
        sourceGrant.setSourceAction("ROTATE");
        sourceGrant.setExpiresAt(Instant.now().plusSeconds(60));
        AtomicReference<ClientInstanceGrantEntity> savedGrant = new AtomicReference<>();
        Mockito.when(instanceRepository.findById(21L)).thenReturn(Optional.of(instance));
        Mockito.when(instanceRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(cryptoService.fingerprint("source-token")).thenReturn("hash-source-token");
        Mockito.when(sourceGrantRepository.findByDistributedKey_IdAndTokenHash(9L, "hash-source-token"))
                .thenReturn(Optional.of(sourceGrant));
        Mockito.when(cryptoService.decrypt("cipher-source-full-key")).thenReturn("sk-gw-source-secret");
        Mockito.when(cryptoService.encrypt("sk-gw-source-secret")).thenReturn("cipher-client-grant");
        Mockito.when(cryptoService.fingerprint(Mockito.argThat(value -> value != null && !value.equals("source-token"))))
                .thenAnswer(invocation -> "hash-" + invocation.getArgument(0));
        Mockito.when(sourceGrantRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(grantRepository.save(Mockito.any())).thenAnswer(invocation -> {
            ClientInstanceGrantEntity grant = invocation.getArgument(0);
            savedGrant.set(grant);
            return grant;
        });

        var grant = service.issueAuthorization(21L, new ClientInstanceAuthorizationRequest(
                "auth_json",
                "https://gateway.example.com",
                "DEEPLINK",
                null,
                null,
                null,
                "source-token",
                null,
                null
        ));

        assertNotNull(grant.grantToken());
        assertTrue(sourceGrant.isConsumed());
        assertEquals("cipher-client-grant", savedGrant.get().getFullKeyCiphertext());
        assertEquals("auth_json", savedGrant.get().getConfigFormat());
    }

    @Test
    void shouldRevokeClientInstanceAndOutstandingGrants() {
        ClientInstanceRepository instanceRepository = Mockito.mock(ClientInstanceRepository.class);
        ClientInstanceGrantRepository grantRepository = Mockito.mock(ClientInstanceGrantRepository.class);
        ClientInstanceAdminService service = new ClientInstanceAdminService(
                instanceRepository,
                grantRepository,
                Mockito.mock(DistributedKeyRepository.class),
                Mockito.mock(DistributedKeySecretExportGrantRepository.class),
                Mockito.mock(CredentialCryptoService.class),
                new ObjectMapper(),
                Optional.empty()
        );
        ClientInstanceEntity instance = clientInstance(21L, distributedKey(9L));
        ClientInstanceGrantEntity grant = grant(instance, "hash-token", "cipher-full-secret", Instant.now().plusSeconds(60));
        Mockito.when(instanceRepository.findById(21L)).thenReturn(Optional.of(instance));
        Mockito.when(grantRepository.findAllByClientInstance_Id(21L)).thenReturn(List.of(grant));
        Mockito.when(instanceRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(grantRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

        var revoked = service.revoke(21L, "lost device");

        assertEquals("REVOKED", revoked.status());
        assertNotNull(revoked.revokedAt());
        assertTrue(grant.isRevoked());
    }

    private DistributedKeyEntity distributedKey(Long id) {
        DistributedKeyEntity key = new DistributedKeyEntity();
        ReflectionTestUtils.setField(key, "id", id);
        key.setKeyName("codex-key");
        key.setKeyPrefix("sk-gw-test");
        key.setMaskedKey("sk-gw-test...cret");
        return key;
    }

    private ClientInstanceEntity clientInstance(Long id, DistributedKeyEntity key) {
        ClientInstanceEntity instance = new ClientInstanceEntity();
        ReflectionTestUtils.setField(instance, "id", id);
        instance.setDistributedKey(key);
        instance.setInstanceId("codex-main");
        instance.setDisplayName("Codex Main");
        instance.setClientFamily("CODEX");
        instance.setWorkspaceHint("repo-a");
        instance.setPluginName("x-ai-gateway-vscode");
        instance.setPluginVersion("1.0.0");
        instance.setDeepLinkScheme("xag");
        instance.setStatus("ACTIVE");
        return instance;
    }

    private ClientInstanceGrantEntity grant(
            ClientInstanceEntity instance,
            String tokenHash,
            String fullKeyCiphertext,
            Instant expiresAt) {
        ClientInstanceGrantEntity grant = new ClientInstanceGrantEntity();
        grant.setClientInstance(instance);
        grant.setTokenHash(tokenHash);
        grant.setFullKeyCiphertext(fullKeyCiphertext);
        grant.setSource("PLUGIN");
        grant.setConfigFormat("env");
        grant.setBaseUrl("https://gateway.example.com");
        grant.setExpiresAt(expiresAt);
        return grant;
    }
}
