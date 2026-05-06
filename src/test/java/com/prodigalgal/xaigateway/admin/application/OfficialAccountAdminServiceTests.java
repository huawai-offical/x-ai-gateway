package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.OfficialAccountImportRequest;
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
}
