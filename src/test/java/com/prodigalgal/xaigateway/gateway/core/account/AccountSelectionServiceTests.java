package com.prodigalgal.xaigateway.gateway.core.account;

import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceActionType;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceContext;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceDecision;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernancePolicyEngine;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyAccountPoolBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountPoolEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccountPoolBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.NetworkProxyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountSelectionServiceTests {

    @Test
    void shouldScopeStickyAccountBySessionAffinityKey() {
        DistributedKeyAccountPoolBindingRepository bindingRepository = Mockito.mock(DistributedKeyAccountPoolBindingRepository.class);
        UpstreamAccountRepository upstreamAccountRepository = Mockito.mock(UpstreamAccountRepository.class);
        NetworkProxyRepository networkProxyRepository = Mockito.mock(NetworkProxyRepository.class);
        StringRedisTemplate stringRedisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        Mockito.when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        AccountSelectionService service = new AccountSelectionService(
                bindingRepository,
                upstreamAccountRepository,
                networkProxyRepository,
                stringRedisTemplate
        );

        DistributedKeyAccountPoolBindingEntity binding = new DistributedKeyAccountPoolBindingEntity();
        UpstreamAccountPoolEntity pool = new UpstreamAccountPoolEntity();
        ReflectionTestUtils.setField(pool, "id", 99L);
        binding.setPool(pool);
        binding.setProviderType(ProviderType.OPENAI_DIRECT);
        binding.setActive(true);

        UpstreamAccountEntity account = new UpstreamAccountEntity();
        ReflectionTestUtils.setField(account, "id", 77L);
        account.setPool(pool);
        account.setAccountName("codex-account");
        account.setProviderType(UpstreamAccountProviderType.CODEX_OAUTH);
        account.setActive(true);
        account.setFrozen(false);
        account.setHealthy(true);

        Mockito.when(bindingRepository.findAllByDistributedKey_IdAndProviderTypeAndActiveTrueOrderByPriorityAscCreatedAtAsc(1L, ProviderType.OPENAI_DIRECT))
                .thenReturn(List.of(binding));
        Mockito.when(upstreamAccountRepository.findAllByPool_IdAndActiveTrueAndFrozenFalseAndHealthyTrueOrderByUpdatedAtDesc(99L))
                .thenReturn(List.of(account));
        Mockito.when(valueOperations.get(Mockito.anyString())).thenReturn(null);

        Optional<UpstreamAccountEntity> resolved = service.resolveActiveAccount(
                1L,
                ProviderType.OPENAI_DIRECT,
                GatewayClientFamily.CODEX,
                120,
                "session-hash-1"
        );

        String expectedKey = "xag:account:sticky:1:OPENAI_DIRECT:CODEX:session:session-hash-1";
        assertTrue(resolved.isPresent());
        Mockito.verify(valueOperations).get(expectedKey);
        Mockito.verify(valueOperations).set(Mockito.eq(expectedKey), Mockito.eq("77"), Mockito.any(Duration.class));
    }

    @Test
    void shouldSkipGovernanceBlockedAccount() {
        DistributedKeyAccountPoolBindingRepository bindingRepository = Mockito.mock(DistributedKeyAccountPoolBindingRepository.class);
        UpstreamAccountRepository upstreamAccountRepository = Mockito.mock(UpstreamAccountRepository.class);
        NetworkProxyRepository networkProxyRepository = Mockito.mock(NetworkProxyRepository.class);
        StringRedisTemplate stringRedisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        Mockito.when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        GovernancePolicyEngine governancePolicyEngine = context -> new GovernanceDecision(
                false,
                "QUARANTINED",
                "blocked",
                GovernanceActionType.QUARANTINE,
                Instant.now().plusSeconds(300),
                List.of(1L),
                List.of(2L)
        );

        AccountSelectionService service = new AccountSelectionService(
                bindingRepository,
                upstreamAccountRepository,
                networkProxyRepository,
                stringRedisTemplate,
                governancePolicyEngine
        );

        DistributedKeyAccountPoolBindingEntity binding = new DistributedKeyAccountPoolBindingEntity();
        UpstreamAccountPoolEntity pool = new UpstreamAccountPoolEntity();
        binding.setPool(pool);
        binding.setProviderType(ProviderType.OPENAI_DIRECT);
        binding.setActive(true);

        UpstreamAccountEntity account = new UpstreamAccountEntity();
        account.setPool(pool);
        account.setAccountName("oauth-account");
        account.setProviderType(UpstreamAccountProviderType.OPENAI_OAUTH);
        account.setActive(true);
        account.setFrozen(false);
        account.setHealthy(true);

        Mockito.when(bindingRepository.findAllByDistributedKey_IdAndProviderTypeAndActiveTrueOrderByPriorityAscCreatedAtAsc(1L, ProviderType.OPENAI_DIRECT))
                .thenReturn(List.of(binding));
        Mockito.when(upstreamAccountRepository.findAllByPool_IdAndActiveTrueAndFrozenFalseAndHealthyTrueOrderByUpdatedAtDesc(null))
                .thenReturn(List.of(account));
        Mockito.when(valueOperations.get(Mockito.anyString())).thenReturn(null);

        Optional<UpstreamAccountEntity> resolved = service.resolveActiveAccount(1L, ProviderType.OPENAI_DIRECT, GatewayClientFamily.GENERIC_OPENAI, 120);

        assertTrue(resolved.isEmpty());
    }
}
