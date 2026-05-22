package com.prodigalgal.xaigateway.gateway.core.account;

import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceActionType;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceContext;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceDecision;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernancePolicyEngine;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyAccountGroupBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccountGroupBindingRepository;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountSelectionServiceTests {

    @Test
    void shouldScopeStickyAccountBySessionAffinityKey() {
        DistributedKeyAccountGroupBindingRepository bindingRepository = Mockito.mock(DistributedKeyAccountGroupBindingRepository.class);
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

        DistributedKeyAccountGroupBindingEntity binding = new DistributedKeyAccountGroupBindingEntity();
        UpstreamAccountGroupEntity group = new UpstreamAccountGroupEntity();
        ReflectionTestUtils.setField(group, "id", 99L);
        binding.setGroup(group);
        binding.setProviderType(ProviderType.OPENAI_DIRECT);
        binding.setActive(true);

        UpstreamAccountEntity account = new UpstreamAccountEntity();
        ReflectionTestUtils.setField(account, "id", 77L);
        account.setGroup(group);
        account.setAccountName("codex-account");
        account.setProviderType(UpstreamAccountProviderType.CODEX_OAUTH);
        account.setActive(true);
        account.setFrozen(false);
        account.setHealthy(true);

        Mockito.when(bindingRepository.findAllByDistributedKey_IdAndProviderTypeAndActiveTrueOrderByPriorityAscCreatedAtAsc(1L, ProviderType.OPENAI_DIRECT))
                .thenReturn(List.of(binding));
        Mockito.when(upstreamAccountRepository.findAllByGroup_IdAndActiveTrueAndFrozenFalseAndHealthyTrueOrderByUpdatedAtDesc(99L))
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
        DistributedKeyAccountGroupBindingRepository bindingRepository = Mockito.mock(DistributedKeyAccountGroupBindingRepository.class);
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

        DistributedKeyAccountGroupBindingEntity binding = new DistributedKeyAccountGroupBindingEntity();
        UpstreamAccountGroupEntity group = new UpstreamAccountGroupEntity();
        binding.setGroup(group);
        binding.setProviderType(ProviderType.OPENAI_DIRECT);
        binding.setActive(true);

        UpstreamAccountEntity account = new UpstreamAccountEntity();
        account.setGroup(group);
        account.setAccountName("oauth-account");
        account.setProviderType(UpstreamAccountProviderType.OPENAI_OAUTH);
        account.setActive(true);
        account.setFrozen(false);
        account.setHealthy(true);

        Mockito.when(bindingRepository.findAllByDistributedKey_IdAndProviderTypeAndActiveTrueOrderByPriorityAscCreatedAtAsc(1L, ProviderType.OPENAI_DIRECT))
                .thenReturn(List.of(binding));
        Mockito.when(upstreamAccountRepository.findAllByGroup_IdAndActiveTrueAndFrozenFalseAndHealthyTrueOrderByUpdatedAtDesc(null))
                .thenReturn(List.of(account));
        Mockito.when(valueOperations.get(Mockito.anyString())).thenReturn(null);

        Optional<UpstreamAccountEntity> resolved = service.resolveActiveAccount(1L, ProviderType.OPENAI_DIRECT, GatewayClientFamily.GENERIC_OPENAI, 120);

        assertTrue(resolved.isEmpty());
    }

    @Test
    void shouldRejectHealthyBindingWhenOnlyInactiveGroupsAreBound() {
        DistributedKeyAccountGroupBindingRepository bindingRepository = Mockito.mock(DistributedKeyAccountGroupBindingRepository.class);
        UpstreamAccountRepository upstreamAccountRepository = Mockito.mock(UpstreamAccountRepository.class);
        NetworkProxyRepository networkProxyRepository = Mockito.mock(NetworkProxyRepository.class);
        StringRedisTemplate stringRedisTemplate = Mockito.mock(StringRedisTemplate.class);
        AccountSelectionService service = new AccountSelectionService(
                bindingRepository,
                upstreamAccountRepository,
                networkProxyRepository,
                stringRedisTemplate
        );
        DistributedKeyAccountGroupBindingEntity binding = binding(99L, ProviderType.OPENAI_DIRECT, false);

        Mockito.when(bindingRepository.findAllByDistributedKey_IdAndProviderTypeAndActiveTrueOrderByPriorityAscCreatedAtAsc(1L, ProviderType.OPENAI_DIRECT))
                .thenReturn(List.of(binding));

        assertFalse(service.hasHealthyAccountBinding(1L, ProviderType.OPENAI_DIRECT, GatewayClientFamily.CODEX));
        Mockito.verifyNoInteractions(upstreamAccountRepository);
    }

    @Test
    void shouldSkipInactiveGroupWhenResolvingActiveAccount() {
        DistributedKeyAccountGroupBindingRepository bindingRepository = Mockito.mock(DistributedKeyAccountGroupBindingRepository.class);
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
        DistributedKeyAccountGroupBindingEntity binding = binding(99L, ProviderType.OPENAI_DIRECT, false);

        Mockito.when(bindingRepository.findAllByDistributedKey_IdAndProviderTypeAndActiveTrueOrderByPriorityAscCreatedAtAsc(1L, ProviderType.OPENAI_DIRECT))
                .thenReturn(List.of(binding));

        Optional<UpstreamAccountEntity> resolved = service.resolveActiveAccount(
                1L,
                ProviderType.OPENAI_DIRECT,
                GatewayClientFamily.CODEX,
                120
        );

        assertTrue(resolved.isEmpty());
        Mockito.verifyNoInteractions(upstreamAccountRepository);
        Mockito.verifyNoInteractions(valueOperations);
    }

    @Test
    void shouldNotReuseStickyAccountOutsideActiveBoundGroups() {
        DistributedKeyAccountGroupBindingRepository bindingRepository = Mockito.mock(DistributedKeyAccountGroupBindingRepository.class);
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
        DistributedKeyAccountGroupBindingEntity activeBinding = binding(100L, ProviderType.OPENAI_DIRECT, true);
        UpstreamAccountGroupEntity inactiveGroup = group(99L, false);
        UpstreamAccountEntity stickyAccount = account(77L, inactiveGroup, UpstreamAccountProviderType.CODEX_OAUTH);
        UpstreamAccountEntity fallbackAccount = account(88L, activeBinding.getGroup(), UpstreamAccountProviderType.CODEX_OAUTH);

        Mockito.when(bindingRepository.findAllByDistributedKey_IdAndProviderTypeAndActiveTrueOrderByPriorityAscCreatedAtAsc(1L, ProviderType.OPENAI_DIRECT))
                .thenReturn(List.of(activeBinding));
        Mockito.when(valueOperations.get(Mockito.anyString())).thenReturn("77");
        Mockito.when(upstreamAccountRepository.findById(77L)).thenReturn(Optional.of(stickyAccount));
        Mockito.when(upstreamAccountRepository.findAllByGroup_IdAndActiveTrueAndFrozenFalseAndHealthyTrueOrderByUpdatedAtDesc(100L))
                .thenReturn(List.of(fallbackAccount));

        Optional<UpstreamAccountEntity> resolved = service.resolveActiveAccount(
                1L,
                ProviderType.OPENAI_DIRECT,
                GatewayClientFamily.CODEX,
                120
        );

        assertTrue(resolved.isPresent());
        assertTrue(resolved.get().getId().equals(88L));
        Mockito.verify(valueOperations).set(Mockito.anyString(), Mockito.eq("88"), Mockito.any(Duration.class));
    }

    private DistributedKeyAccountGroupBindingEntity binding(Long groupId, ProviderType providerType, boolean groupActive) {
        DistributedKeyAccountGroupBindingEntity binding = new DistributedKeyAccountGroupBindingEntity();
        binding.setGroup(group(groupId, groupActive));
        binding.setProviderType(providerType);
        binding.setActive(true);
        return binding;
    }

    private UpstreamAccountGroupEntity group(Long id, boolean active) {
        UpstreamAccountGroupEntity group = new UpstreamAccountGroupEntity();
        ReflectionTestUtils.setField(group, "id", id);
        group.setActive(active);
        group.setAllowedClientFamilies(List.of("CODEX"));
        return group;
    }

    private UpstreamAccountEntity account(
            Long id,
            UpstreamAccountGroupEntity group,
            UpstreamAccountProviderType providerType) {
        UpstreamAccountEntity account = new UpstreamAccountEntity();
        ReflectionTestUtils.setField(account, "id", id);
        account.setGroup(group);
        account.setAccountName("account-" + id);
        account.setProviderType(providerType);
        account.setActive(true);
        account.setFrozen(false);
        account.setHealthy(true);
        return account;
    }
}
