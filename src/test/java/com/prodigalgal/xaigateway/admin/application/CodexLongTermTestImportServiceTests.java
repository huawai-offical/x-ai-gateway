package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountGroupRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
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

class CodexLongTermTestImportServiceTests {

    @Test
    void shouldCreateGroupAndCodexAccountFromAuthJsonWithoutLeakingSecret() {
        UpstreamAccountRepository accountRepository = Mockito.mock(UpstreamAccountRepository.class);
        UpstreamAccountGroupRepository groupRepository = Mockito.mock(UpstreamAccountGroupRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        CodexLongTermTestImportService service = new CodexLongTermTestImportService(
                accountRepository,
                groupRepository,
                cryptoService,
                modelCatalogService,
                new ObjectMapper()
        );
        AtomicReference<UpstreamAccountGroupEntity> savedGroupRef = new AtomicReference<>();
        AtomicReference<UpstreamAccountEntity> savedAccountRef = new AtomicReference<>();
        Mockito.when(groupRepository.findByGroupNameIgnoreCase("codex-long-term-test")).thenReturn(Optional.empty());
        Mockito.when(groupRepository.save(Mockito.any())).thenAnswer(invocation -> {
            UpstreamAccountGroupEntity group = invocation.getArgument(0);
            ReflectionTestUtils.setField(group, "id", 91L);
            savedGroupRef.set(group);
            return group;
        });
        Mockito.when(accountRepository.findFirstByProviderTypeAndExternalAccountIdOrderByUpdatedAtDesc(
                Mockito.eq(UpstreamAccountProviderType.CODEX_OAUTH),
                Mockito.anyString()
        )).thenReturn(Optional.empty());
        Mockito.when(accountRepository.findAllByProviderTypeOrderByUpdatedAtDesc(UpstreamAccountProviderType.CODEX_OAUTH))
                .thenReturn(List.of());
        Mockito.when(accountRepository.save(Mockito.any())).thenAnswer(invocation -> {
            UpstreamAccountEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 151L);
            savedAccountRef.set(entity);
            return entity;
        });
        Mockito.when(cryptoService.encrypt(Mockito.anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        Mockito.when(modelCatalogService.normalize(Mockito.any())).thenAnswer(invocation -> normalize(invocation.getArgument(0)));

        CodexLongTermTestImportResult result = service.importAuthJson(authJson("codex-access-secret", "codex-refresh-secret"), null);

        UpstreamAccountGroupEntity group = savedGroupRef.get();
        UpstreamAccountEntity account = savedAccountRef.get();
        assertEquals(151L, result.accountId());
        assertEquals(91L, result.groupId());
        assertEquals("CREATED", result.status());
        assertTrue(result.routeEligible());
        assertTrue(result.externalAccountId().startsWith("codex:email:"));
        assertNotNull(result.credentialFingerprint());
        assertEquals(UpstreamAccountProviderType.CODEX_OAUTH, group.getProviderType());
        assertEquals(List.of("CODEX"), group.getAllowedClientFamilies());
        assertEquals("enc:codex-access-secret", account.getAccessTokenCiphertext());
        assertEquals("enc:codex-refresh-secret", account.getRefreshTokenCiphertext());
        assertEquals("QUOTA_READY", account.getRefreshStatus());
        assertTrue(account.getMetadataJson().contains("\"long_term_test\":true"));
        assertTrue(account.getMetadataJson().contains("\"identitySource\":\"email\""));
        assertTrue(account.getMetadataJson().contains("\"identityStrength\":\"STRONG\""));
        assertTrue(account.getLastRefreshResultJson().contains("\"responsesSmoke\""));
        assertFalse(account.getMetadataJson().contains("codex-access-secret"));
        assertFalse(account.getMetadataJson().contains("codex-refresh-secret"));
        assertFalse(account.getLastRefreshResultJson().contains("codex-access-secret"));
        assertFalse(account.getHeaderSnapshotJson().contains("codex-access-secret"));
    }

    @Test
    void shouldUpdateExistingCodexAccountByExternalAccountId() {
        UpstreamAccountRepository accountRepository = Mockito.mock(UpstreamAccountRepository.class);
        UpstreamAccountGroupRepository groupRepository = Mockito.mock(UpstreamAccountGroupRepository.class);
        CredentialCryptoService cryptoService = Mockito.mock(CredentialCryptoService.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        CodexLongTermTestImportService service = new CodexLongTermTestImportService(
                accountRepository,
                groupRepository,
                cryptoService,
                modelCatalogService,
                new ObjectMapper()
        );
        UpstreamAccountGroupEntity group = new UpstreamAccountGroupEntity();
        ReflectionTestUtils.setField(group, "id", 92L);
        group.setGroupName("codex-long-term-test");
        group.setProviderType(UpstreamAccountProviderType.CODEX_OAUTH);
        UpstreamAccountEntity existing = new UpstreamAccountEntity();
        ReflectionTestUtils.setField(existing, "id", 152L);
        existing.setExternalAccountId("legacy-account-id");
        existing.setProviderType(UpstreamAccountProviderType.CODEX_OAUTH);
        existing.setAccountName("old-name");
        existing.setAccessTokenCiphertext("enc:old");
        String identityKey = new CodexAuthJsonParser(new ObjectMapper())
                .parse(authJson("codex-new-access-secret", "codex-new-refresh-secret", "acct-long-term-v2"))
                .identityKey();
        existing.setMetadataJson("{\"codex_auth_json\":{\"identityKey\":\"" + identityKey + "\"}}");
        AtomicReference<UpstreamAccountEntity> savedAccountRef = new AtomicReference<>();
        Mockito.when(groupRepository.findByGroupNameIgnoreCase("codex-long-term-test")).thenReturn(Optional.of(group));
        Mockito.when(accountRepository.findFirstByProviderTypeAndExternalAccountIdOrderByUpdatedAtDesc(
                Mockito.eq(UpstreamAccountProviderType.CODEX_OAUTH),
                Mockito.anyString()
        )).thenReturn(Optional.empty());
        Mockito.when(accountRepository.findAllByProviderTypeOrderByUpdatedAtDesc(UpstreamAccountProviderType.CODEX_OAUTH))
                .thenReturn(List.of(existing));
        Mockito.when(accountRepository.save(Mockito.any())).thenAnswer(invocation -> {
            UpstreamAccountEntity entity = invocation.getArgument(0);
            savedAccountRef.set(entity);
            return entity;
        });
        Mockito.when(cryptoService.encrypt(Mockito.anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        Mockito.when(modelCatalogService.normalize(Mockito.any())).thenAnswer(invocation -> normalize(invocation.getArgument(0)));

        CodexLongTermTestImportResult result = service.importAuthJson(
                authJson("codex-new-access-secret", "codex-new-refresh-secret", "acct-long-term-v2"),
                "codex-long-term-test"
        );

        UpstreamAccountEntity account = savedAccountRef.get();
        assertEquals(152L, result.accountId());
        assertEquals(92L, result.groupId());
        assertEquals("UPDATED", result.status());
        assertEquals(identityKey, result.externalAccountId());
        assertEquals("enc:codex-new-access-secret", account.getAccessTokenCiphertext());
        assertEquals("enc:codex-new-refresh-secret", account.getRefreshTokenCiphertext());
        assertFalse(account.getMetadataJson().contains("codex-new-access-secret"));
        assertFalse(account.getMetadataJson().contains("codex-new-refresh-secret"));
        Mockito.verify(groupRepository, Mockito.never()).save(Mockito.any());
    }

    private String authJson(String accessToken, String refreshToken) {
        return authJson(accessToken, refreshToken, "acct-long-term");
    }

    private String authJson(String accessToken, String refreshToken, String accountId) {
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

    private List<String> normalize(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return new ArrayList<>();
    }
}
