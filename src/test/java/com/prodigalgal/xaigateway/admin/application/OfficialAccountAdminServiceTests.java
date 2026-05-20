package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.OfficialAccountImportRequest;
import com.prodigalgal.xaigateway.admin.api.OfficialCodexResponsesSmokeRequest;
import com.prodigalgal.xaigateway.admin.api.OfficialAccountQuotaRefreshRequest;
import com.prodigalgal.xaigateway.admin.api.OfficialAccountQuotaResponse;
import com.prodigalgal.xaigateway.admin.api.OfficialAccountType;
import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountPoolRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import java.time.Instant;
import java.util.ArrayList;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficialAccountAdminServiceTests {

    @Test
    void shouldImportOfficialAccountAndRefreshQuotaWithoutPersistingPlainSecret() {
        UpstreamAccountRepository accountRepository = Mockito.mock(UpstreamAccountRepository.class);
        UpstreamAccountPoolRepository poolRepository = Mockito.mock(UpstreamAccountPoolRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        OfficialAccountAdminService service = new OfficialAccountAdminService(
                accountRepository,
                poolRepository,
                cryptoService,
                modelCatalogService,
                new ObjectMapper()
        );
        AtomicReference<UpstreamAccountEntity> savedRef = new AtomicReference<>();
        Mockito.when(cryptoService.encrypt(Mockito.anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        Mockito.when(modelCatalogService.normalize(Mockito.any())).thenAnswer(invocation -> normalize(invocation.getArgument(0)));
        Mockito.when(accountRepository.save(Mockito.any())).thenAnswer(invocation -> {
            UpstreamAccountEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 31L);
            savedRef.set(entity);
            return entity;
        });

        OfficialAccountQuotaResponse response = service.importOfficialAccount(new OfficialAccountImportRequest(
                OfficialAccountType.CODEX,
                null,
                "codex-official",
                "codex:user-1",
                "codex-access-secret",
                "codex-refresh-secret",
                """
                        {
                          "access_token": "codex-access-secret",
                          "profile": {
                            "refresh_token": "codex-refresh-secret",
                            "email": "coder@example.com"
                          }
                        }
                        """,
                true,
                null,
                null,
                null,
                List.of("gpt-4.1"),
                Instant.parse("2026-05-06T10:00:00Z"),
                "TEAM",
                "PRO",
                3600,
                1200L,
                18L,
                Instant.parse("2026-05-06T11:00:00Z"),
                true
        ));

        UpstreamAccountEntity saved = savedRef.get();
        assertEquals(31L, response.accountId());
        assertEquals(OfficialAccountType.CODEX, response.accountType());
        assertEquals(UpstreamAccountProviderType.CODEX_OAUTH, response.providerType());
        assertEquals("READY", response.quotaStatus());
        assertEquals("TEAM", response.planTier());
        assertEquals(1200L, response.quotaRemainingTokens());
        assertTrue(response.routeEligible());
        assertEquals("enc:codex-access-secret", saved.getAccessTokenCiphertext());
        assertEquals("enc:codex-refresh-secret", saved.getRefreshTokenCiphertext());
        assertFalse(saved.getMetadataJson().contains("codex-access-secret"));
        assertFalse(saved.getMetadataJson().contains("codex-refresh-secret"));
        assertFalse(saved.getLastRefreshResultJson().contains("codex-access-secret"));
        assertTrue(saved.getMetadataJson().contains("\"access_token\":\"***\""));
        assertTrue(saved.getMetadataJson().contains("\"refresh_token\":\"***\""));
        assertTrue(saved.getMetadataJson().contains("\"codex_auth_json\""));
        assertFalse(saved.getMetadataJson().contains("\"accessTokenFingerprint\":\"codex-access-secret\""));
    }

    @Test
    void shouldImportCodexOfficialAccountFromRawAuthJsonMetadata() {
        UpstreamAccountRepository accountRepository = Mockito.mock(UpstreamAccountRepository.class);
        UpstreamAccountPoolRepository poolRepository = Mockito.mock(UpstreamAccountPoolRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        OfficialAccountAdminService service = new OfficialAccountAdminService(
                accountRepository,
                poolRepository,
                cryptoService,
                modelCatalogService,
                new ObjectMapper()
        );
        AtomicReference<UpstreamAccountEntity> savedRef = new AtomicReference<>();
        Mockito.when(cryptoService.encrypt(Mockito.anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        Mockito.when(modelCatalogService.normalize(Mockito.any())).thenAnswer(invocation -> normalize(invocation.getArgument(0)));
        Mockito.when(accountRepository.save(Mockito.any())).thenAnswer(invocation -> {
            UpstreamAccountEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 32L);
            savedRef.set(entity);
            return entity;
        });

        OfficialAccountQuotaResponse response = service.importOfficialAccount(new OfficialAccountImportRequest(
                OfficialAccountType.CODEX,
                null,
                null,
                null,
                null,
                null,
                """
                        {
                          "auth_mode": "login",
                          "tokens": {
                            "access_token": "codex-access-secret",
                            "refresh_token": "codex-refresh-secret",
                            "account_id": "acct_real_shape"
                          }
                        }
                        """,
                true,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true
        ));

        UpstreamAccountEntity saved = savedRef.get();
        assertEquals(32L, response.accountId());
        assertTrue(response.externalAccountId().startsWith("codex:account:"));
        assertEquals("enc:codex-access-secret", saved.getAccessTokenCiphertext());
        assertEquals("enc:codex-refresh-secret", saved.getRefreshTokenCiphertext());
        assertTrue(saved.getMetadataJson().contains("\"tokens\""));
        assertTrue(saved.getMetadataJson().contains("***"));
        assertTrue(saved.getMetadataJson().contains("\"codex_auth_json\""));
        assertFalse(saved.getMetadataJson().contains("codex-access-secret"));
        assertFalse(saved.getLastRefreshResultJson().contains("codex-access-secret"));
    }

    @Test
    void shouldUpdateExistingCodexOfficialAccountByCanonicalIdentity() {
        UpstreamAccountRepository accountRepository = Mockito.mock(UpstreamAccountRepository.class);
        UpstreamAccountPoolRepository poolRepository = Mockito.mock(UpstreamAccountPoolRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        OfficialAccountAdminService service = new OfficialAccountAdminService(
                accountRepository,
                poolRepository,
                cryptoService,
                modelCatalogService,
                objectMapper
        );
        String identityKey = new CodexAuthJsonParser(objectMapper).parse(codexAuthJson(
                "codex-new-access-secret",
                "codex-new-refresh-secret",
                "acct-login-v2"
        )).identityKey();
        UpstreamAccountEntity existing = codexEntity(33L);
        existing.setExternalAccountId("legacy-account-id");
        existing.setMetadataJson("{\"account_identity\":{\"identityKey\":\"" + identityKey + "\"},\"official_account_type\":\"CODEX\"}");
        AtomicReference<UpstreamAccountEntity> savedRef = new AtomicReference<>();
        Mockito.when(accountRepository.findFirstByProviderTypeAndExternalAccountIdOrderByUpdatedAtDesc(
                Mockito.eq(UpstreamAccountProviderType.CODEX_OAUTH),
                Mockito.anyString()
        )).thenReturn(Optional.empty());
        Mockito.when(accountRepository.findAllByProviderTypeOrderByUpdatedAtDesc(UpstreamAccountProviderType.CODEX_OAUTH))
                .thenReturn(List.of(existing));
        Mockito.when(cryptoService.encrypt(Mockito.anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        Mockito.when(modelCatalogService.normalize(Mockito.any())).thenAnswer(invocation -> normalize(invocation.getArgument(0)));
        Mockito.when(accountRepository.save(Mockito.any())).thenAnswer(invocation -> {
            UpstreamAccountEntity entity = invocation.getArgument(0);
            savedRef.set(entity);
            return entity;
        });

        OfficialAccountQuotaResponse response = service.importOfficialAccount(new OfficialAccountImportRequest(
                OfficialAccountType.CODEX,
                null,
                null,
                "acct-login-v2",
                null,
                null,
                codexAuthJson("codex-new-access-secret", "codex-new-refresh-secret", "acct-login-v2"),
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
                true
        ));

        UpstreamAccountEntity saved = savedRef.get();
        assertEquals(33L, response.accountId());
        assertEquals(identityKey, response.externalAccountId());
        assertEquals("enc:codex-new-access-secret", saved.getAccessTokenCiphertext());
        assertEquals("enc:codex-new-refresh-secret", saved.getRefreshTokenCiphertext());
        assertTrue(saved.getMetadataJson().contains("\"import_status\":\"UPDATED\""));
        assertFalse(saved.getMetadataJson().contains("codex-new-access-secret"));
        assertFalse(saved.getLastRefreshResultJson().contains("codex-new-access-secret"));
    }

    @Test
    void shouldMarkOfficialQuotaRefreshFailureWithRetryAndRouteBlockReason() {
        UpstreamAccountRepository accountRepository = Mockito.mock(UpstreamAccountRepository.class);
        UpstreamAccountPoolRepository poolRepository = Mockito.mock(UpstreamAccountPoolRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        OfficialAccountAdminService service = new OfficialAccountAdminService(
                accountRepository,
                poolRepository,
                cryptoService,
                modelCatalogService,
                new ObjectMapper()
        );
        Mockito.when(modelCatalogService.normalize(Mockito.any())).thenAnswer(invocation -> normalize(invocation.getArgument(0)));
        UpstreamAccountEntity entity = new UpstreamAccountEntity();
        ReflectionTestUtils.setField(entity, "id", 7L);
        entity.setAccountName("codex-official");
        entity.setProviderType(UpstreamAccountProviderType.CODEX_OAUTH);
        entity.setExternalAccountId("codex:user-1");
        entity.setActive(true);
        entity.setFrozen(false);
        entity.setHealthy(true);
        entity.setRefreshStatus("QUOTA_READY");
        entity.setMetadataJson("{\"official_account_type\":\"CODEX\",\"quota_status\":\"READY\"}");
        Mockito.when(accountRepository.findById(7L)).thenReturn(Optional.of(entity));
        Mockito.when(accountRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

        OfficialAccountQuotaResponse response = service.refreshQuota(7L, new OfficialAccountQuotaRefreshRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                "quota endpoint timeout",
                true
        ));

        assertEquals("ERROR", response.quotaStatus());
        assertEquals("QUOTA_FAILED", response.refreshStatus());
        assertEquals("quota endpoint timeout", response.quotaError());
        assertNotNull(response.nextRefreshAfter());
        assertFalse(response.routeEligible());
        assertEquals("ACCOUNT_UNHEALTHY", response.routeBlockReason());
        assertTrue(entity.getLastRefreshResultJson().contains("\"status\":\"failed\""));
    }

    @Test
    void shouldExposeOfficialQuotaSummaryForScheduler() {
        UpstreamAccountRepository accountRepository = Mockito.mock(UpstreamAccountRepository.class);
        UpstreamAccountPoolRepository poolRepository = Mockito.mock(UpstreamAccountPoolRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        OfficialAccountAdminService service = new OfficialAccountAdminService(
                accountRepository,
                poolRepository,
                cryptoService,
                modelCatalogService,
                new ObjectMapper()
        );
        Mockito.when(modelCatalogService.normalize(Mockito.any())).thenAnswer(invocation -> normalize(invocation.getArgument(0)));
        UpstreamAccountEntity entity = new UpstreamAccountEntity();
        ReflectionTestUtils.setField(entity, "id", 8L);
        entity.setAccountName("gemini-cli");
        entity.setProviderType(UpstreamAccountProviderType.GEMINI_OAUTH);
        entity.setExternalAccountId("gemini_cli:user-1");
        entity.setActive(true);
        entity.setFrozen(false);
        entity.setHealthy(true);
        entity.setRefreshStatus("QUOTA_READY");
        entity.setQuotaRemainingTokens(100L);
        entity.setQuotaRemainingRequests(5L);
        entity.setQuotaWindowStartedAt(Instant.parse("2026-05-06T00:00:00Z"));
        entity.setQuotaWindowSeconds(3600);
        entity.setMetadataJson("""
                {
                  "official_account_type": "GEMINI_CLI",
                  "plan_tier": "FREE",
                  "subscription_tier": "FREE",
                  "quota_status": "READY",
                  "quota_reset_at": "2026-05-06T01:00:00Z"
                }
                """);
        Mockito.when(accountRepository.findById(8L)).thenReturn(Optional.of(entity));

        OfficialAccountQuotaResponse response = service.quota(8L);

        assertEquals(OfficialAccountType.GEMINI_CLI, response.accountType());
        assertEquals("FREE", response.planTier());
        assertEquals(Instant.parse("2026-05-06T01:00:00Z"), response.quotaResetAt());
        assertTrue(response.routeEligible());
        assertEquals(5L, response.quotaRemainingRequests());
    }

    @Test
    void shouldRefreshCodexQuotaWithAuthJsonAdapterSnapshotWhenRequestIsEmpty() {
        UpstreamAccountRepository accountRepository = Mockito.mock(UpstreamAccountRepository.class);
        UpstreamAccountPoolRepository poolRepository = Mockito.mock(UpstreamAccountPoolRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        OfficialAccountAdminService service = new OfficialAccountAdminService(
                accountRepository,
                poolRepository,
                cryptoService,
                modelCatalogService,
                new ObjectMapper()
        );
        Mockito.when(modelCatalogService.normalize(Mockito.any())).thenAnswer(invocation -> normalize(invocation.getArgument(0)));
        UpstreamAccountEntity entity = codexEntity(9L);
        entity.setAccessTokenCiphertext("enc:access");
        entity.setRefreshTokenCiphertext("enc:refresh");
        entity.setTokenExpiresAt(Instant.parse("2026-05-07T03:00:00Z"));
        Mockito.when(accountRepository.findById(9L)).thenReturn(Optional.of(entity));
        Mockito.when(accountRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

        OfficialAccountQuotaResponse response = service.refreshQuota(9L, null);

        assertEquals("READY", response.quotaStatus());
        assertTrue(response.routeEligible());
        assertTrue(entity.getMetadataJson().contains("\"codex_adapter_status\":\"LOCAL_INSPECTION_READY\""));
        assertTrue(entity.getLastRefreshResultJson().contains("codex-auth-json-local-inspection"));
        assertTrue(entity.getLastRefreshResultJson().contains("\"path\":\"/backend-api/codex/responses\""));
        assertTrue(entity.getLastRefreshResultJson().contains("\"keepalive\""));
        assertFalse(entity.getLastRefreshResultJson().contains("enc:access"));
    }

    @Test
    void shouldBuildCodexResponsesSmokeWithoutReturningCredential() {
        UpstreamAccountRepository accountRepository = Mockito.mock(UpstreamAccountRepository.class);
        UpstreamAccountPoolRepository poolRepository = Mockito.mock(UpstreamAccountPoolRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        OfficialAccountAdminService service = new OfficialAccountAdminService(
                accountRepository,
                poolRepository,
                cryptoService,
                modelCatalogService,
                new ObjectMapper()
        );
        UpstreamAccountEntity entity = codexEntity(10L);
        entity.setAccessTokenCiphertext("enc:codex-access-secret");
        entity.setSupportedModels(List.of("gpt-4.1"));
        Mockito.when(accountRepository.findById(10L)).thenReturn(Optional.of(entity));
        Mockito.when(accountRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(cryptoService.decrypt("enc:codex-access-secret")).thenReturn("codex-access-secret");

        var response = service.codexResponsesSmoke(10L, new OfficialCodexResponsesSmokeRequest(
                "gpt-4.1",
                "hello",
                true
        ));

        assertEquals("DRY_RUN_READY", response.status());
        assertEquals("SKIPPED", response.classification());
        assertEquals("DRY_RUN", response.skippedReason());
        assertEquals("/backend-api/codex/responses", response.path());
        assertEquals("https://chatgpt.com/backend-api/codex", response.baseUrl());
        assertTrue(response.codexAppApi());
        assertEquals("gpt-4.1", response.model());
        assertTrue(response.routeEligible());
        assertNotNull(response.credentialFingerprint());
        String preview = response.requestPreview().toString();
        assertTrue(preview.contains("Bearer ***"));
        assertTrue(preview.contains("responses=experimental"));
        assertTrue(preview.contains("prompt_cache_key"));
        assertFalse(preview.contains("codex-access-secret"));
        assertEquals("2026-05-19.codex-responses-smoke-record-replay.v1",
                response.recordReplayFixture().get("schemaVersion"));
        assertEquals("record_replay", response.recordReplayFixture().get("replayMode"));
        assertEquals("DRY_RUN", response.recordReplayFixture().get("certificationStatus"));
        assertTrue(response.recordReplayFixture().toString().contains("codex_responses"));
        assertTrue(response.recordReplayFixture().toString().contains("disabled_by_default"));
        assertFalse(entity.getLastRefreshResultJson().contains("codex-access-secret"));
        assertTrue(entity.getLastRefreshResultJson().contains("\"recordReplayFixture\""));
        assertTrue(entity.getLastRefreshResultJson().contains("\"classification\":\"SKIPPED\""));
        assertTrue(entity.getLastRefreshResultJson().contains("\"skippedReason\":\"DRY_RUN\""));
    }

    @Test
    void shouldClassifyCodexResponsesSmokeAsBudgetBlockedWhenRouteQuotaIsExhausted() {
        UpstreamAccountRepository accountRepository = Mockito.mock(UpstreamAccountRepository.class);
        UpstreamAccountPoolRepository poolRepository = Mockito.mock(UpstreamAccountPoolRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        OfficialAccountAdminService service = new OfficialAccountAdminService(
                accountRepository,
                poolRepository,
                cryptoService,
                modelCatalogService,
                new ObjectMapper()
        );
        UpstreamAccountEntity entity = codexEntity(11L);
        entity.setAccessTokenCiphertext("enc:codex-access-secret");
        entity.setQuotaRemainingRequests(0L);
        Mockito.when(accountRepository.findById(11L)).thenReturn(Optional.of(entity));
        Mockito.when(accountRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.codexResponsesSmoke(11L, new OfficialCodexResponsesSmokeRequest(
                "gpt-5.4@low",
                "hello",
                false
        ));

        assertEquals("ROUTE_BLOCKED", response.status());
        assertEquals("BUDGET_BLOCKED", response.classification());
        assertEquals("QUOTA_REQUESTS_EXHAUSTED", response.skippedReason());
        assertEquals("QUOTA_REQUESTS_EXHAUSTED", response.routeBlockReason());
        assertEquals(null, response.httpStatus());
        assertEquals("BUDGET_BLOCKED", response.recordReplayFixture().get("certificationStatus"));
        assertTrue(response.recordReplayFixture().toString().contains("QUOTA_REQUESTS_EXHAUSTED"));
        Mockito.verify(cryptoService, Mockito.never()).decrypt(Mockito.anyString());
        assertTrue(entity.getLastRefreshResultJson().contains("\"recordReplayFixture\""));
        assertTrue(entity.getLastRefreshResultJson().contains("\"classification\":\"BUDGET_BLOCKED\""));
        assertTrue(entity.getLastRefreshResultJson().contains("\"skippedReason\":\"QUOTA_REQUESTS_EXHAUSTED\""));
    }

    private static List<String> normalize(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String value : source) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.trim());
            }
        }
        normalized.sort(String.CASE_INSENSITIVE_ORDER);
        return normalized;
    }

    private static UpstreamAccountEntity codexEntity(Long id) {
        UpstreamAccountEntity entity = new UpstreamAccountEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setAccountName("codex-official");
        entity.setProviderType(UpstreamAccountProviderType.CODEX_OAUTH);
        entity.setExternalAccountId("codex:user-1");
        entity.setActive(true);
        entity.setFrozen(false);
        entity.setHealthy(true);
        entity.setRefreshStatus("QUOTA_READY");
        entity.setQuotaRemainingTokens(100L);
        entity.setQuotaRemainingRequests(5L);
        entity.setMetadataJson("{\"official_account_type\":\"CODEX\",\"quota_status\":\"READY\"}");
        return entity;
    }

    private static String codexAuthJson(String accessToken, String refreshToken, String accountId) {
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
                  }
                }
                """.formatted(accessToken, refreshToken, accountId);
    }
}
