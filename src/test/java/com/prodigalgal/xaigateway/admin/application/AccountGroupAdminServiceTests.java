package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.OpsSystemEventResponse;
import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccountGroupBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountGroupRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountGroupAdminServiceTests {

    @Test
    void shouldPreflightAndExecuteCodexRuntimeBatchRecoverySafely() {
        UpstreamAccountGroupRepository groupRepository = Mockito.mock(UpstreamAccountGroupRepository.class);
        UpstreamAccountRepository accountRepository = Mockito.mock(UpstreamAccountRepository.class);
        UpstreamCredentialRepository credentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        DistributedKeyRepository distributedKeyRepository = Mockito.mock(DistributedKeyRepository.class);
        DistributedKeyAccountGroupBindingRepository bindingRepository = Mockito.mock(DistributedKeyAccountGroupBindingRepository.class);
        SupportedModelCatalogService modelCatalogService = Mockito.mock(SupportedModelCatalogService.class);
        OpsTimelineService opsTimelineService = Mockito.mock(OpsTimelineService.class);
        AccountGroupAdminService service = new AccountGroupAdminService(
                groupRepository,
                accountRepository,
                credentialRepository,
                distributedKeyRepository,
                bindingRepository,
                modelCatalogService,
                opsTimelineService,
                new ObjectMapper()
        );

        UpstreamAccountGroupEntity group = new UpstreamAccountGroupEntity();
        ReflectionTestUtils.setField(group, "id", 1L);
        group.setGroupName("codex-group");
        group.setProviderType(UpstreamAccountProviderType.CODEX_OAUTH);
        group.setAllowedClientFamilies(List.of("CODEX"));

        UpstreamAccountEntity ready = account(7L, "codex-ready");
        UpstreamAccountEntity safe = account(8L, "codex-cooldown");
        safe.setFrozen(true);
        safe.setHealthy(false);
        safe.setRefreshStatus("FAILED");
        safe.setRefreshFailureCount(2);
        safe.setCooldownUntil(Instant.parse("2026-05-08T01:00:00Z"));
        safe.setLastErrorMessage("upstream timeout after quota refresh");

        UpstreamAccountEntity blocked = account(9L, "codex-policy-blocked");
        blocked.setHealthy(false);
        blocked.setRefreshStatus("FAILED");
        blocked.setRefreshFailureCount(1);
        blocked.setLastErrorMessage("permission denied Bearer abcdefghijklmnopqrstuvwxyz");

        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(accountRepository.findAllByGroup_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of(ready, safe, blocked));
        when(accountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(opsTimelineService.recordEvent(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(new OpsSystemEventResponse(
                91L,
                "CODEX_RUNTIME_BATCH_RECOVERY",
                "WARNING",
                "console",
                "ACCOUNT_GROUP",
                "account-group:1",
                "Codex Runtime 批量恢复",
                "{}",
                Instant.now(),
                Instant.now()
        ));

        var preflight = service.codexRuntimeBatchRecovery(1L, null, false);

        assertTrue(preflight.dryRunOnly());
        assertEquals(1, preflight.totals().safe());
        assertEquals(1, preflight.totals().blocked());
        assertEquals(1, preflight.totals().alreadyReady());
        verify(accountRepository, never()).save(any());

        var executed = service.codexRuntimeBatchRecovery(1L, null, true);

        assertFalse(executed.dryRunOnly());
        assertEquals(1, executed.totals().executed());
        assertEquals(2, executed.totals().skipped());
        assertFalse(safe.isFrozen());
        assertTrue(safe.isHealthy());
        assertEquals(0, safe.getRefreshFailureCount());
        assertNull(safe.getCooldownUntil());
        assertNull(safe.getLastErrorMessage());
        assertTrue(executed.items().stream().anyMatch(item ->
                item.accountId().equals(blocked.getId()) && "SKIPPED".equals(item.executionStatus())));
        verify(accountRepository).save(safe);
        verify(opsTimelineService, Mockito.times(2)).recordEvent(any(), any(), any(), any(), any(), any(), any(), any());
    }

    private UpstreamAccountEntity account(Long id, String name) {
        UpstreamAccountEntity account = new UpstreamAccountEntity();
        ReflectionTestUtils.setField(account, "id", id);
        account.setAccountName(name);
        account.setProviderType(UpstreamAccountProviderType.CODEX_OAUTH);
        account.setActive(true);
        account.setHealthy(true);
        account.setFrozen(false);
        account.setRefreshStatus("READY");
        return account;
    }
}
