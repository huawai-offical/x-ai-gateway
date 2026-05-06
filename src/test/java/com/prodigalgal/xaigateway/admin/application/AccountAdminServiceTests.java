package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AccountImportAuthJsonRequest;
import com.prodigalgal.xaigateway.admin.api.UpstreamAccountResponse;
import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountPoolEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountPoolRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountAdminServiceTests {

    @Test
    void shouldImportOauthGovernanceFieldsFromMetadata() {
        UpstreamAccountRepository accountRepository = Mockito.mock(UpstreamAccountRepository.class);
        UpstreamAccountPoolRepository poolRepository = Mockito.mock(UpstreamAccountPoolRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        OAuthSessionRefreshService refreshService = Mockito.mock(OAuthSessionRefreshService.class);
        AccountAdminService service = new AccountAdminService(
                accountRepository,
                poolRepository,
                cryptoService,
                modelCatalogService,
                refreshService,
                new ObjectMapper()
        );
        Mockito.when(cryptoService.encrypt(Mockito.anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        Mockito.when(modelCatalogService.resolveForAccountImport(Mockito.isNull(), Mockito.anyList()))
                .thenReturn(List.of("gpt-4o"));
        Mockito.when(modelCatalogService.normalize(Mockito.anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(accountRepository.save(Mockito.any())).thenAnswer(invocation -> {
            UpstreamAccountEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 21L);
            return entity;
        });

        UpstreamAccountResponse response = service.importAuthJson(new AccountImportAuthJsonRequest(
                null,
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
    void shouldMarkManualRefreshResultWithoutChangingSecrets() {
        UpstreamAccountRepository accountRepository = Mockito.mock(UpstreamAccountRepository.class);
        UpstreamAccountPoolRepository poolRepository = Mockito.mock(UpstreamAccountPoolRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        OAuthSessionRefreshService refreshService = Mockito.mock(OAuthSessionRefreshService.class);
        AccountAdminService service = new AccountAdminService(
                accountRepository,
                poolRepository,
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
    void shouldExposeProgrammingAccountIdentityAndRouteEligibility() {
        UpstreamAccountRepository accountRepository = Mockito.mock(UpstreamAccountRepository.class);
        UpstreamAccountPoolRepository poolRepository = Mockito.mock(UpstreamAccountPoolRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        OAuthSessionRefreshService refreshService = Mockito.mock(OAuthSessionRefreshService.class);
        AccountAdminService service = new AccountAdminService(
                accountRepository,
                poolRepository,
                cryptoService,
                modelCatalogService,
                refreshService,
                new ObjectMapper()
        );
        UpstreamAccountPoolEntity pool = new UpstreamAccountPoolEntity();
        ReflectionTestUtils.setField(pool, "id", 99L);
        pool.setPoolName("codex-pool");
        pool.setProviderType(UpstreamAccountProviderType.CODEX_OAUTH);
        pool.setAllowedClientFamilies(List.of("CODEX"));
        UpstreamAccountEntity entity = new UpstreamAccountEntity();
        ReflectionTestUtils.setField(entity, "id", 88L);
        entity.setPool(pool);
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
}
