package com.prodigalgal.xaigateway.gateway.core.account;

import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceContext;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernancePolicyEngine;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyAccountGroupBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccountGroupBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.NetworkProxyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class AccountSelectionService {

    private final DistributedKeyAccountGroupBindingRepository distributedKeyAccountGroupBindingRepository;
    private final UpstreamAccountRepository upstreamAccountRepository;
    private final NetworkProxyRepository networkProxyRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final GovernancePolicyEngine governancePolicyEngine;

    @Autowired
    public AccountSelectionService(
            DistributedKeyAccountGroupBindingRepository distributedKeyAccountGroupBindingRepository,
            UpstreamAccountRepository upstreamAccountRepository,
            NetworkProxyRepository networkProxyRepository,
            StringRedisTemplate stringRedisTemplate,
            GovernancePolicyEngine governancePolicyEngine) {
        this.distributedKeyAccountGroupBindingRepository = distributedKeyAccountGroupBindingRepository;
        this.upstreamAccountRepository = upstreamAccountRepository;
        this.networkProxyRepository = networkProxyRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.governancePolicyEngine = governancePolicyEngine;
    }

    public AccountSelectionService(
            DistributedKeyAccountGroupBindingRepository distributedKeyAccountGroupBindingRepository,
            UpstreamAccountRepository upstreamAccountRepository,
            NetworkProxyRepository networkProxyRepository,
            StringRedisTemplate stringRedisTemplate) {
        this(distributedKeyAccountGroupBindingRepository, upstreamAccountRepository, networkProxyRepository, stringRedisTemplate, GovernancePolicyEngine.allowAll());
    }

    @Transactional(readOnly = true)
    public boolean hasHealthyAccountBinding(Long distributedKeyId, ProviderType providerType, GatewayClientFamily clientFamily) {
        List<DistributedKeyAccountGroupBindingEntity> rawBindings = distributedKeyAccountGroupBindingRepository
                .findAllByDistributedKey_IdAndProviderTypeAndActiveTrueOrderByPriorityAscCreatedAtAsc(distributedKeyId, providerType);
        if (rawBindings.isEmpty()) {
            return true;
        }
        List<DistributedKeyAccountGroupBindingEntity> bindings = rawBindings.stream()
                .filter(this::hasActiveGroup)
                .toList();
        if (bindings.isEmpty()) {
            return false;
        }
        for (DistributedKeyAccountGroupBindingEntity binding : bindings) {
            List<UpstreamAccountEntity> accounts = upstreamAccountRepository
                    .findAllByGroup_IdAndActiveTrueAndFrozenFalseAndHealthyTrueOrderByUpdatedAtDesc(binding.getGroup().getId());
            for (UpstreamAccountEntity account : accounts) {
                if (!isClientFamilyAllowed(binding.getGroup(), clientFamily)) {
                    continue;
                }
                if (!isGovernanceHealthy(account)) {
                    continue;
                }
                if (isNetworkHealthy(account)) {
                    return true;
                }
            }
        }
        return false;
    }

    public Optional<UpstreamAccountEntity> resolveActiveAccount(Long distributedKeyId, ProviderType providerType, GatewayClientFamily clientFamily, int stickyTtlSeconds) {
        return resolveActiveAccount(distributedKeyId, providerType, clientFamily, stickyTtlSeconds, null);
    }

    public Optional<UpstreamAccountEntity> resolveActiveAccount(
            Long distributedKeyId,
            ProviderType providerType,
            GatewayClientFamily clientFamily,
            int stickyTtlSeconds,
            String sessionAffinityKey) {
        List<DistributedKeyAccountGroupBindingEntity> bindings = distributedKeyAccountGroupBindingRepository
                .findAllByDistributedKey_IdAndProviderTypeAndActiveTrueOrderByPriorityAscCreatedAtAsc(distributedKeyId, providerType)
                .stream()
                .filter(this::hasActiveGroup)
                .toList();
        if (bindings.isEmpty()) {
            return Optional.empty();
        }

        String stickyKey = stickyKey(distributedKeyId, providerType, clientFamily, sessionAffinityKey);
        Set<Long> activeGroupIds = activeGroupIds(bindings);
        String stickyAccountId = stringRedisTemplate.opsForValue().get(stickyKey);
        if (stickyAccountId != null) {
            Optional<UpstreamAccountEntity> sticky = upstreamAccountRepository.findById(Long.parseLong(stickyAccountId))
                    .filter(this::isNetworkHealthy)
                    .filter(account -> account.isActive() && !account.isFrozen() && account.isHealthy())
                    .filter(account -> account.getGroup() != null && activeGroupIds.contains(account.getGroup().getId()))
                    .filter(account -> isClientFamilyAllowed(account.getGroup(), clientFamily))
                    .filter(this::isGovernanceHealthy);
            if (sticky.isPresent()) {
                sticky.get().setLastUsedAt(Instant.now());
                return sticky;
            }
        }

        for (DistributedKeyAccountGroupBindingEntity binding : bindings) {
            List<UpstreamAccountEntity> accounts = upstreamAccountRepository
                    .findAllByGroup_IdAndActiveTrueAndFrozenFalseAndHealthyTrueOrderByUpdatedAtDesc(binding.getGroup().getId());
            for (UpstreamAccountEntity account : accounts) {
                if (!isClientFamilyAllowed(binding.getGroup(), clientFamily)) {
                    continue;
                }
                if (!isGovernanceHealthy(account)) {
                    continue;
                }
                if (!isNetworkHealthy(account)) {
                    continue;
                }
                stringRedisTemplate.opsForValue().set(stickyKey, String.valueOf(account.getId()), Duration.ofSeconds(Math.max(stickyTtlSeconds, 60)));
                account.setLastUsedAt(Instant.now());
                return Optional.of(account);
            }
        }
        return Optional.empty();
    }

    private boolean hasActiveGroup(DistributedKeyAccountGroupBindingEntity binding) {
        return binding.getGroup() != null && binding.getGroup().isActive();
    }

    private Set<Long> activeGroupIds(List<DistributedKeyAccountGroupBindingEntity> bindings) {
        return bindings.stream()
                .map(DistributedKeyAccountGroupBindingEntity::getGroup)
                .filter(group -> group != null && group.getId() != null && group.isActive())
                .map(com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountGroupEntity::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isClientFamilyAllowed(
            com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountGroupEntity group,
            GatewayClientFamily clientFamily) {
        return group.getAllowedClientFamilies().isEmpty()
                || group.getAllowedClientFamilies().contains(clientFamily.name());
    }

    private boolean isNetworkHealthy(UpstreamAccountEntity account) {
        if (account.getProxyId() == null) {
            return true;
        }
        return networkProxyRepository.findById(account.getProxyId())
                .map(com.prodigalgal.xaigateway.infra.persistence.entity.NetworkProxyEntity::isActive)
                .orElse(false);
    }

    private boolean isGovernanceHealthy(UpstreamAccountEntity account) {
        GovernanceContext context = new GovernanceContext(
                account.getProviderType().routeProviderType(),
                account.getSiteProfileId(),
                null,
                account.getId(),
                account.getProxyId()
        );
        return governancePolicyEngine.evaluate(context).allowed();
    }

    private String stickyKey(
            Long distributedKeyId,
            ProviderType providerType,
            GatewayClientFamily clientFamily,
            String sessionAffinityKey) {
        String base = "xag:account:sticky:" + distributedKeyId + ":" + providerType.name() + ":" + clientFamily.name();
        if (sessionAffinityKey == null || sessionAffinityKey.isBlank()) {
            return base;
        }
        return base + ":session:" + sessionAffinityKey.trim();
    }
}
