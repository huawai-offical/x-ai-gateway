package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OAuthSessionRefreshServiceTests {

    @Test
    void shouldRefreshDueAccountWithProviderAdapterAndSanitizeHeaderSnapshot() {
        UpstreamAccountRepository repository = Mockito.mock(UpstreamAccountRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        UpstreamAccountEntity entity = account(11L, UpstreamAccountProviderType.OPENAI_OAUTH);
        entity.setTokenExpiresAt(Instant.now().minusSeconds(30));
        Mockito.when(repository.findAll()).thenReturn(List.of(entity));
        Mockito.when(repository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(cryptoService.decrypt("enc:access-old")).thenReturn("access-old");
        Mockito.when(cryptoService.decrypt("enc:refresh-old")).thenReturn("refresh-old");
        Mockito.when(cryptoService.encrypt(Mockito.anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));

        OAuthSessionRefreshService service = service(repository, cryptoService, new OAuthSessionRefreshAdapter() {
            @Override
            public UpstreamAccountProviderType providerType() {
                return UpstreamAccountProviderType.OPENAI_OAUTH;
            }

            @Override
            public OAuthSessionRefreshResult refresh(OAuthSessionRefreshRequest request) {
                return new OAuthSessionRefreshResult(
                        "openai-test-adapter",
                        "access-new",
                        "refresh-new",
                        request.now().plusSeconds(3600),
                        request.now().plusSeconds(3300),
                        request.now(),
                        3600,
                        900L,
                        12L,
                        Map.of(
                                "authorization", "Bearer access-new",
                                "x-ratelimit-remaining-requests", "12"
                        ),
                        Map.of("source", "unit-test")
                );
            }
        });

        List<OAuthSessionRefreshOutcome> outcomes = service.refreshDueAccounts(10);

        assertEquals(1, outcomes.size());
        assertEquals("REFRESHED", outcomes.getFirst().status());
        assertEquals("REFRESHED", entity.getRefreshStatus());
        assertEquals(0, entity.getRefreshFailureCount());
        assertNull(entity.getCooldownUntil());
        assertTrue(entity.isHealthy());
        assertEquals("enc:access-new", entity.getAccessTokenCiphertext());
        assertEquals("enc:refresh-new", entity.getRefreshTokenCiphertext());
        assertEquals(900L, entity.getQuotaRemainingTokens());
        assertEquals(12L, entity.getQuotaRemainingRequests());
        assertTrue(entity.getHeaderSnapshotJson().contains("\"authorization\":\"***\""));
        assertFalse(entity.getHeaderSnapshotJson().contains("access-new"));
        assertTrue(entity.getLastRefreshResultJson().contains("openai-test-adapter"));
        Mockito.verify(repository).save(entity);
    }

    @Test
    void shouldEnterCooldownWhenProviderAdapterFails() {
        UpstreamAccountRepository repository = Mockito.mock(UpstreamAccountRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        UpstreamAccountEntity entity = account(12L, UpstreamAccountProviderType.GEMINI_OAUTH);
        entity.setRefreshFailureCount(1);
        entity.setTokenExpiresAt(Instant.now().minusSeconds(30));
        Mockito.when(repository.findAll()).thenReturn(List.of(entity));
        Mockito.when(repository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(cryptoService.decrypt(Mockito.anyString())).thenReturn("secret");

        OAuthSessionRefreshService service = service(repository, cryptoService, new OAuthSessionRefreshAdapter() {
            @Override
            public UpstreamAccountProviderType providerType() {
                return UpstreamAccountProviderType.GEMINI_OAUTH;
            }

            @Override
            public OAuthSessionRefreshResult refresh(OAuthSessionRefreshRequest request) {
                throw new IllegalStateException("provider refresh rejected");
            }
        });

        OAuthSessionRefreshOutcome outcome = service.refreshDueAccounts(10).getFirst();

        assertEquals("FAILED", outcome.status());
        assertEquals("FAILED", entity.getRefreshStatus());
        assertEquals(2, entity.getRefreshFailureCount());
        assertNotNull(entity.getCooldownUntil());
        assertFalse(entity.isHealthy());
        assertTrue(entity.getLastErrorMessage().contains("provider refresh rejected"));
        assertTrue(entity.getLastRefreshResultJson().contains("cooldownUntil"));
        Mockito.verify(repository).save(entity);
    }

    @Test
    void shouldSkipCooldownAccountDuringScheduledRefresh() {
        UpstreamAccountRepository repository = Mockito.mock(UpstreamAccountRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        UpstreamAccountEntity entity = account(13L, UpstreamAccountProviderType.CLAUDE_ACCOUNT);
        entity.setCooldownUntil(Instant.now().plusSeconds(600));
        entity.setTokenExpiresAt(Instant.now().minusSeconds(30));
        Mockito.when(repository.findAll()).thenReturn(List.of(entity));

        OAuthSessionRefreshService service = service(repository, cryptoService);

        OAuthSessionRefreshOutcome outcome = service.refreshDueAccounts(10).getFirst();

        assertEquals("SKIPPED", outcome.status());
        assertEquals("COOLDOWN", outcome.reason());
        assertEquals("READY", entity.getRefreshStatus());
        Mockito.verify(repository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void shouldRefreshCodexProgrammingAccountWithDefaultAdapter() {
        UpstreamAccountRepository repository = Mockito.mock(UpstreamAccountRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        UpstreamAccountEntity entity = account(14L, UpstreamAccountProviderType.CODEX_OAUTH);
        entity.setTokenExpiresAt(Instant.now().minusSeconds(30));
        Mockito.when(repository.findAll()).thenReturn(List.of(entity));
        Mockito.when(repository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(cryptoService.decrypt("enc:access-old")).thenReturn("access-old");
        Mockito.when(cryptoService.decrypt("enc:refresh-old")).thenReturn("refresh-old");
        Mockito.when(cryptoService.encrypt(Mockito.anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        OAuthSessionRefreshService service = service(repository, cryptoService, new CodexOAuthSessionRefreshAdapter());

        OAuthSessionRefreshOutcome outcome = service.refreshDueAccounts(10).getFirst();

        assertEquals("REFRESHED", outcome.status());
        assertEquals("codex-oauth-session", outcome.reason());
        assertEquals("REFRESHED", entity.getRefreshStatus());
        assertTrue(entity.getHeaderSnapshotJson().contains("x-codex-account"));
    }

    private OAuthSessionRefreshService service(
            UpstreamAccountRepository repository,
            CredentialCryptoService cryptoService,
            OAuthSessionRefreshAdapter... adapters) {
        return new OAuthSessionRefreshService(
                repository,
                cryptoService,
                new ObjectMapper(),
                List.of(adapters),
                Optional.empty(),
                Optional.empty()
        );
    }

    private UpstreamAccountEntity account(Long id, UpstreamAccountProviderType providerType) {
        UpstreamAccountEntity entity = new UpstreamAccountEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setAccountName("codex-session-" + id);
        entity.setProviderType(providerType);
        entity.setAccessTokenCiphertext("enc:access-old");
        entity.setRefreshTokenCiphertext("enc:refresh-old");
        entity.setActive(true);
        entity.setFrozen(false);
        entity.setHealthy(true);
        entity.setRefreshStatus("READY");
        return entity;
    }
}
