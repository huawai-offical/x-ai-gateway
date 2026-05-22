package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AccountImportAuthJsonRequest;
import com.prodigalgal.xaigateway.admin.api.UpstreamAccountResponse;
import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountGroupRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountAdminServiceTests {

    @Test
    void shouldImportOauthGovernanceFieldsFromMetadata() {
        UpstreamAccountRepository accountRepository = Mockito.mock(UpstreamAccountRepository.class);
        UpstreamAccountGroupRepository groupRepository = Mockito.mock(UpstreamAccountGroupRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        OAuthSessionRefreshService refreshService = Mockito.mock(OAuthSessionRefreshService.class);
        AccountAdminService service = new AccountAdminService(
                accountRepository,
                groupRepository,
                cryptoService,
                modelCatalogService,
                refreshService,
                new ObjectMapper()
        );
        UpstreamAccountGroupEntity group = new UpstreamAccountGroupEntity();
        ReflectionTestUtils.setField(group, "id", 5L);
        group.setGroupName("codex-group");
        group.setProviderType(UpstreamAccountProviderType.CODEX_OAUTH);
        Mockito.when(groupRepository.findById(5L)).thenReturn(Optional.of(group));
        Mockito.when(cryptoService.encrypt(Mockito.anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        Mockito.when(modelCatalogService.resolveForAccountImport(Mockito.eq(group), Mockito.anyList()))
                .thenReturn(List.of("gpt-4o"));
        Mockito.when(modelCatalogService.normalize(Mockito.anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(accountRepository.save(Mockito.any())).thenAnswer(invocation -> {
            UpstreamAccountEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 21L);
            return entity;
        });

        UpstreamAccountResponse response = service.importAuthJson(new AccountImportAuthJsonRequest(
                5L,
                "codex-session",
                "codex:user-1",
                "access-token",
                "refresh-token",
                """
                        {
                          "expires_at": "2026-04-24T10:00:00Z",
                          "refresh_status": "ready",
                          "quota": {
                            "remaining_tokens": 1200,
                            "remaining_requests": 18,
                            "window_seconds": 3600
                          },
                          "headers": {
                            "x-ratelimit-remaining-tokens": "1200"
                          }
                        }
                        """,
                null,
                null,
                null,
                true,
                null,
                null,
                null,
                List.of("gpt-4o"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertEquals("READY", response.refreshStatus());
        assertEquals(Instant.parse("2026-04-24T10:00:00Z"), response.tokenExpiresAt());
        assertEquals(1200L, response.quotaRemainingTokens());
        assertEquals(18L, response.quotaRemainingRequests());
        assertEquals(3600, response.quotaWindowSeconds());
        assertTrue(response.headerSnapshotJson().contains("x-ratelimit-remaining-tokens"));
    }

    @Test
    void shouldUpdateExistingCodexAuthJsonImportByStableIdentityAndSanitizeSnapshots() {
        UpstreamAccountRepository accountRepository = Mockito.mock(UpstreamAccountRepository.class);
        UpstreamAccountGroupRepository groupRepository = Mockito.mock(UpstreamAccountGroupRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        OAuthSessionRefreshService refreshService = Mockito.mock(OAuthSessionRefreshService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AccountAdminService service = new AccountAdminService(
                accountRepository,
                groupRepository,
                cryptoService,
                modelCatalogService,
                refreshService,
                objectMapper
        );
        UpstreamAccountGroupEntity group = new UpstreamAccountGroupEntity();
        ReflectionTestUtils.setField(group, "id", 5L);
        group.setGroupName("codex-group");
        group.setProviderType(UpstreamAccountProviderType.CODEX_OAUTH);
        UpstreamAccountEntity existing = new UpstreamAccountEntity();
        ReflectionTestUtils.setField(existing, "id", 71L);
        existing.setGroup(group);
        existing.setProviderType(UpstreamAccountProviderType.CODEX_OAUTH);
        existing.setExternalAccountId("legacy-account-id");
        existing.setAccountName("old-codex");
        String identityKey = new CodexAuthJsonParser(objectMapper).parse(codexAuthJson(
                "codex-new-access-secret",
                "codex-new-refresh-secret",
                "acct-can-change"
        )).identityKey();
        existing.setMetadataJson("{\"account_identity\":{\"identityKey\":\"" + identityKey + "\"}}");
        Mockito.when(groupRepository.findById(5L)).thenReturn(Optional.of(group));
        Mockito.when(accountRepository.findFirstByProviderTypeAndExternalAccountIdOrderByUpdatedAtDesc(
                Mockito.eq(UpstreamAccountProviderType.CODEX_OAUTH),
                Mockito.anyString()
        )).thenReturn(Optional.empty());
        Mockito.when(accountRepository.findAllByProviderTypeOrderByUpdatedAtDesc(UpstreamAccountProviderType.CODEX_OAUTH))
                .thenReturn(List.of(existing));
        Mockito.when(cryptoService.encrypt(Mockito.anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        Mockito.when(modelCatalogService.resolveForAccountImport(Mockito.eq(group), Mockito.any()))
                .thenReturn(List.of("gpt-5.4"));
        Mockito.when(modelCatalogService.normalize(Mockito.anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(accountRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpstreamAccountResponse response = service.importAuthJson(new AccountImportAuthJsonRequest(
                5L,
                null,
                "acct-can-change",
                "codex-new-access-secret",
                "codex-new-refresh-secret",
                codexAuthJson("codex-new-access-secret", "codex-new-refresh-secret", "acct-can-change"),
                null,
                null,
                null,
                true,
                null,
                null,
                null,
                List.of("gpt-5.4"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertEquals(71L, response.id());
        assertEquals(identityKey, response.externalAccountId());
        assertEquals("enc:codex-new-access-secret", existing.getAccessTokenCiphertext());
        assertEquals("enc:codex-new-refresh-secret", existing.getRefreshTokenCiphertext());
        assertFalse(existing.getMetadataJson().contains("codex-new-access-secret"));
        assertFalse(existing.getMetadataJson().contains("codex-new-refresh-secret"));
        assertFalse(existing.getHeaderSnapshotJson().contains("Bearer abcdefghijklmnopqrstuvwxyz"));
        assertTrue(existing.getHeaderSnapshotJson().contains("\"authorization\":\"***\""));
        assertTrue(existing.getMetadataJson().contains("\"identityStrength\":\"STRONG\""));
    }

    @Test
    void shouldNotMergeWeakCodexTokenIdentityByMetadataScan() {
        UpstreamAccountRepository accountRepository = Mockito.mock(UpstreamAccountRepository.class);
        UpstreamAccountGroupRepository groupRepository = Mockito.mock(UpstreamAccountGroupRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        OAuthSessionRefreshService refreshService = Mockito.mock(OAuthSessionRefreshService.class);
        AccountAdminService service = new AccountAdminService(
                accountRepository,
                groupRepository,
                cryptoService,
                modelCatalogService,
                refreshService,
                new ObjectMapper()
        );
        UpstreamAccountGroupEntity group = new UpstreamAccountGroupEntity();
        ReflectionTestUtils.setField(group, "id", 6L);
        group.setGroupName("codex-group");
        group.setProviderType(UpstreamAccountProviderType.CODEX_OAUTH);
        Mockito.when(groupRepository.findById(6L)).thenReturn(Optional.of(group));
        Mockito.when(accountRepository.findFirstByProviderTypeAndExternalAccountIdOrderByUpdatedAtDesc(
                Mockito.eq(UpstreamAccountProviderType.CODEX_OAUTH),
                Mockito.anyString()
        )).thenReturn(Optional.empty());
        Mockito.when(cryptoService.encrypt(Mockito.anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        Mockito.when(modelCatalogService.resolveForAccountImport(Mockito.eq(group), Mockito.any()))
                .thenReturn(List.of("gpt-5.4"));
        Mockito.when(modelCatalogService.normalize(Mockito.anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(accountRepository.save(Mockito.any())).thenAnswer(invocation -> {
            UpstreamAccountEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 72L);
            return entity;
        });

        UpstreamAccountResponse response = service.importAuthJson(new AccountImportAuthJsonRequest(
                6L,
                "weak-codex",
                null,
                "codex-access-only-secret",
                null,
                "{\"access_token\":\"codex-access-only-secret\"}",
                null,
                null,
                null,
                true,
                null,
                null,
                null,
                List.of("gpt-5.4"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertEquals(72L, response.id());
        assertTrue(response.externalAccountId().startsWith("codex:weak-token:"));
        Mockito.verify(accountRepository, Mockito.never()).findAllByProviderTypeOrderByUpdatedAtDesc(UpstreamAccountProviderType.CODEX_OAUTH);
    }

    @Test
    void shouldImportCodexAuthJsonFromFilePathWithoutRefreshToken() throws Exception {
        UpstreamAccountRepository accountRepository = Mockito.mock(UpstreamAccountRepository.class);
        UpstreamAccountGroupRepository groupRepository = Mockito.mock(UpstreamAccountGroupRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        OAuthSessionRefreshService refreshService = Mockito.mock(OAuthSessionRefreshService.class);
        AccountAdminService service = new AccountAdminService(
                accountRepository,
                groupRepository,
                cryptoService,
                modelCatalogService,
                refreshService,
                new ObjectMapper()
        );
        UpstreamAccountGroupEntity group = new UpstreamAccountGroupEntity();
        ReflectionTestUtils.setField(group, "id", 9L);
        group.setGroupName("codex-group");
        group.setProviderType(UpstreamAccountProviderType.CODEX_OAUTH);
        Path authJson = Files.createTempFile("codex-auth", ".json");
        Files.writeString(authJson, """
                {
                  "tokens": {
                    "access_token": "codex-access-from-file"
                  },
                  "account_id": "acct-file-only"
                }
                """);
        Mockito.when(groupRepository.findById(9L)).thenReturn(Optional.of(group));
        Mockito.when(accountRepository.findFirstByProviderTypeAndExternalAccountIdOrderByUpdatedAtDesc(
                Mockito.eq(UpstreamAccountProviderType.CODEX_OAUTH),
                Mockito.anyString()
        )).thenReturn(Optional.empty());
        Mockito.when(accountRepository.findAllByProviderTypeOrderByUpdatedAtDesc(UpstreamAccountProviderType.CODEX_OAUTH))
                .thenReturn(List.of());
        Mockito.when(cryptoService.encrypt(Mockito.anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        Mockito.when(modelCatalogService.resolveForAccountImport(Mockito.eq(group), Mockito.any()))
                .thenReturn(List.of("gpt-5.4"));
        Mockito.when(accountRepository.save(Mockito.any())).thenAnswer(invocation -> {
            UpstreamAccountEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 73L);
            return entity;
        });

        UpstreamAccountResponse response = service.importAuthJson(new AccountImportAuthJsonRequest(
                9L,
                null,
                null,
                null,
                null,
                null,
                null,
                authJson.toString(),
                null,
                true,
                null,
                null,
                null,
                List.of("gpt-5.4"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertEquals(73L, response.id());
        assertEquals("ACCESS_ONLY", response.refreshStatus());
        assertNull(response.nextRefreshAfter());
        Mockito.verify(cryptoService).encrypt("codex-access-from-file");
        Mockito.verify(cryptoService, Mockito.never()).encrypt(Mockito.eq(""));
    }

    @Test
    void shouldMarkManualRefreshResultWithoutChangingSecrets() {
        UpstreamAccountRepository accountRepository = Mockito.mock(UpstreamAccountRepository.class);
        UpstreamAccountGroupRepository groupRepository = Mockito.mock(UpstreamAccountGroupRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        OAuthSessionRefreshService refreshService = Mockito.mock(OAuthSessionRefreshService.class);
        AccountAdminService service = new AccountAdminService(
                accountRepository,
                groupRepository,
                cryptoService,
                modelCatalogService,
                refreshService,
                new ObjectMapper()
        );
        UpstreamAccountEntity entity = new UpstreamAccountEntity();
        entity.setAccountName("codex-session");
        entity.setProviderType(com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType.OPENAI_OAUTH);
        entity.setRefreshFailureCount(3);
        entity.setRefreshStatus("FAILED");
        entity.setAccessTokenCiphertext("enc:access");
        entity.setRefreshTokenCiphertext("enc:refresh");
        ReflectionTestUtils.setField(entity, "id", 7L);
        Mockito.when(accountRepository.findById(7L)).thenReturn(Optional.of(entity));
        Mockito.when(refreshService.refreshAccount(7L)).thenAnswer(invocation -> {
            entity.setRefreshStatus("REFRESHED");
            entity.setRefreshFailureCount(0);
            entity.setLastRefreshResultJson("{\"status\":\"refreshed\",\"adapter\":\"openai-oauth-session\"}");
            return new OAuthSessionRefreshOutcome(7L, "OPENAI_OAUTH", "REFRESHED", "openai-oauth-session", Instant.now(), null, null);
        });
        Mockito.when(modelCatalogService.normalize(Mockito.anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        UpstreamAccountResponse response = service.refresh(7L);

        assertEquals("REFRESHED", response.refreshStatus());
        assertEquals(0, response.refreshFailureCount());
        assertTrue(response.lastRefreshResultJson().contains("openai-oauth-session"));
        assertEquals("enc:access", entity.getAccessTokenCiphertext());
        assertEquals("enc:refresh", entity.getRefreshTokenCiphertext());
        Mockito.verify(refreshService).refreshAccount(7L);
    }

    @Test
    void shouldResetRuntimeFailureStateWithoutChangingSecrets() {
        UpstreamAccountRepository accountRepository = Mockito.mock(UpstreamAccountRepository.class);
        UpstreamAccountGroupRepository groupRepository = Mockito.mock(UpstreamAccountGroupRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        OAuthSessionRefreshService refreshService = Mockito.mock(OAuthSessionRefreshService.class);
        AccountAdminService service = new AccountAdminService(
                accountRepository,
                groupRepository,
                cryptoService,
                modelCatalogService,
                refreshService,
                new ObjectMapper()
        );
        UpstreamAccountEntity entity = new UpstreamAccountEntity();
        ReflectionTestUtils.setField(entity, "id", 17L);
        entity.setAccountName("codex-runtime");
        entity.setProviderType(UpstreamAccountProviderType.CODEX_OAUTH);
        entity.setFrozen(true);
        entity.setHealthy(false);
        entity.setLastErrorMessage("model temporary unavailable");
        entity.setRefreshFailureCount(4);
        entity.setRefreshStatus("QUOTA_FAILED");
        entity.setCooldownUntil(Instant.parse("2026-05-07T10:00:00Z"));
        entity.setNextRefreshAfter(Instant.parse("2026-05-07T10:05:00Z"));
        entity.setAccessTokenCiphertext("enc:access");
        entity.setRefreshTokenCiphertext("enc:refresh");
        Mockito.when(accountRepository.findById(17L)).thenReturn(Optional.of(entity));
        Mockito.when(accountRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(modelCatalogService.normalize(Mockito.anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        UpstreamAccountResponse response = service.resetRuntime(17L);

        assertEquals("READY", response.refreshStatus());
        assertEquals(0, response.refreshFailureCount());
        assertTrue(response.healthy());
        assertEquals(false, response.frozen());
        assertNull(entity.getCooldownUntil());
        assertNull(entity.getNextRefreshAfter());
        assertNull(entity.getLastErrorMessage());
        assertEquals("enc:access", entity.getAccessTokenCiphertext());
        assertEquals("enc:refresh", entity.getRefreshTokenCiphertext());
    }

    @Test
    void shouldRefreshCodexAccountModelsFromCatalogAndDefaults() {
        UpstreamAccountRepository accountRepository = Mockito.mock(UpstreamAccountRepository.class);
        UpstreamAccountGroupRepository groupRepository = Mockito.mock(UpstreamAccountGroupRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        OAuthSessionRefreshService refreshService = Mockito.mock(OAuthSessionRefreshService.class);
        AccountAdminService service = new AccountAdminService(
                accountRepository,
                groupRepository,
                cryptoService,
                modelCatalogService,
                refreshService,
                new ObjectMapper()
        );
        UpstreamAccountEntity entity = new UpstreamAccountEntity();
        ReflectionTestUtils.setField(entity, "id", 27L);
        entity.setAccountName("codex-runtime");
        entity.setProviderType(UpstreamAccountProviderType.CODEX_OAUTH);
        entity.setSupportedModels(List.of("old-model"));
        Mockito.when(accountRepository.findById(27L)).thenReturn(Optional.of(entity));
        Mockito.when(accountRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(modelCatalogService.listByUpstreamProvider(UpstreamAccountProviderType.CODEX_OAUTH))
                .thenReturn(List.of("gpt-5.4@low", "gpt-5.3-codex"));
        Mockito.when(modelCatalogService.normalize(Mockito.anyList())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<String> source = invocation.getArgument(0);
            return source.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        });

        var response = service.refreshModels(27L);

        assertEquals(27L, response.accountId());
        assertTrue(entity.getSupportedModels().contains("gpt-5.3-codex"));
        assertTrue(entity.getSupportedModels().contains("gpt-5.4@low"));
        assertEquals(entity.getSupportedModels().size(), response.modelCount());
    }

    @Test
    void shouldExposeProgrammingAccountIdentityAndRouteEligibility() {
        UpstreamAccountRepository accountRepository = Mockito.mock(UpstreamAccountRepository.class);
        UpstreamAccountGroupRepository groupRepository = Mockito.mock(UpstreamAccountGroupRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        OAuthSessionRefreshService refreshService = Mockito.mock(OAuthSessionRefreshService.class);
        AccountAdminService service = new AccountAdminService(
                accountRepository,
                groupRepository,
                cryptoService,
                modelCatalogService,
                refreshService,
                new ObjectMapper()
        );
        UpstreamAccountGroupEntity group = new UpstreamAccountGroupEntity();
        ReflectionTestUtils.setField(group, "id", 99L);
        group.setGroupName("codex-group");
        group.setProviderType(UpstreamAccountProviderType.CODEX_OAUTH);
        group.setAllowedClientFamilies(List.of("CODEX"));
        UpstreamAccountEntity entity = new UpstreamAccountEntity();
        ReflectionTestUtils.setField(entity, "id", 88L);
        entity.setGroup(group);
        entity.setAccountName("codex-user");
        entity.setProviderType(UpstreamAccountProviderType.CODEX_OAUTH);
        entity.setExternalAccountId("codex:user-1");
        entity.setActive(true);
        entity.setFrozen(false);
        entity.setHealthy(true);
        entity.setRefreshStatus("READY");
        entity.setQuotaRemainingTokens(10_000L);
        entity.setQuotaRemainingRequests(30L);
        entity.setQuotaWindowSeconds(3600);
        entity.setMetadataJson("""
                {
                  "identity_subject": "codex:user-1",
                  "identity_email": "coder@example.com",
                  "adoption_decision": "adopted",
                  "client_family": "CODEX"
                }
                """);
        Mockito.when(accountRepository.findById(88L)).thenReturn(Optional.of(entity));

        var identity = service.programmingIdentity(88L, "CODEX");

        assertEquals(UpstreamAccountProviderType.CODEX_OAUTH, identity.providerType());
        assertEquals("codex:user-1", identity.identitySubject());
        assertEquals("coder@example.com", identity.identityEmail());
        assertTrue(identity.routeEligible());
        assertEquals(10_000L, identity.quotaRemainingTokens());
    }

    private String codexAuthJson(String accessToken, String refreshToken, String accountId) {
        return """
                {
                  "auth_mode": "login",
                  "tokens": {
                    "access_token": "%s",
                    "refresh_token": "%s",
                    "account_id": "%s",
                    "expires_at": "2026-05-08T01:00:00Z"
                  },
                  "profile": {
                    "email": "codex-test@example.com"
                  },
                  "headers": {
                    "authorization": "Bearer abcdefghijklmnopqrstuvwxyz"
                  }
                }
                """.formatted(accessToken, refreshToken, accountId);
    }
}
