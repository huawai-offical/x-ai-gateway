package com.prodigalgal.xaigateway.gateway.core.account;

import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyAccountPoolBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccountPoolBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.NetworkProxyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class AccountSelectionService {

    private final DistributedKeyAccountPoolBindingRepository distributedKeyAccountPoolBindingRepository;
    private final UpstreamAccountRepository upstreamAccountRepository;
    private final NetworkProxyRepository networkProxyRepository;
    private final StringRedisTemplate stringRedisTemplate;

    public AccountSelectionService(
            DistributedKeyAccountPoolBindingRepository distributedKeyAccountPoolBindingRepository,
            UpstreamAccountRepository upstreamAccountRepository,
            NetworkProxyRepository networkProxyRepository,
            StringRedisTemplate stringRedisTemplate) {
        this.distributedKeyAccountPoolBindingRepository = distributedKeyAccountPoolBindingRepository;
        this.upstreamAccountRepository = upstreamAccountRepository;
        this.networkProxyRepository = networkProxyRepository;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Transactional(readOnly = true)
    public boolean hasHealthyAccountBinding(Long distributedKeyId, ProviderType providerType, GatewayClientFamily clientFamily) {
        List<DistributedKeyAccountPoolBindingEntity> bindings = distributedKeyAccountPoolBindingRepository
                .findAllByDistributedKeyIdAndProviderTypeAndActiveTrueOrderByPriorityAscCreatedAtAsc(distributedKeyId, providerType);
        if (bindings.isEmpty()) {
            return true;
        }
        for (DistributedKeyAccountPoolBindingEntity binding : bindings) {
            List<UpstreamAccountEntity> accounts = upstreamAccountRepository
                    .findAllByPoolIdAndActiveTrueAndFrozenFalseAndHealthyTrueOrderByUpdatedAtDesc(binding.getPool().getId());
            for (UpstreamAccountEntity account : accounts) {
                if (!binding.getPool().getAllowedClientFamilies().isEmpty()
                        && !binding.getPool().getAllowedClientFamilies().contains(clientFamily.name())) {
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
        List<DistributedKeyAccountPoolBindingEntity> bindings = distributedKeyAccountPoolBindingRepository
                .findAllByDistributedKeyIdAndProviderTypeAndActiveTrueOrderByPriorityAscCreatedAtAsc(distributedKeyId, providerType);
        if (bindings.isEmpty()) {
            return Optional.empty();
        }

        String stickyKey = stickyKey(distributedKeyId, providerType, clientFamily);
        String stickyAccountId = stringRedisTemplate.opsForValue().get(stickyKey);
        if (stickyAccountId != null) {
            Optional<UpstreamAccountEntity> sticky = upstreamAccountRepository.findById(Long.parseLong(stickyAccountId))
                    .filter(this::isNetworkHealthy)
                    .filter(account -> account.isActive() && !account.isFrozen() && account.isHealthy());
            if (sticky.isPresent()) {
                sticky.get().setLastUsedAt(Instant.now());
                return sticky;
            }
        }

        for (DistributedKeyAccountPoolBindingEntity binding : bindings) {
            List<UpstreamAccountEntity> accounts = upstreamAccountRepository
                    .findAllByPoolIdAndActiveTrueAndFrozenFalseAndHealthyTrueOrderByUpdatedAtDesc(binding.getPool().getId());
            for (UpstreamAccountEntity account : accounts) {
                if (!binding.getPool().getAllowedClientFamilies().isEmpty()
                        && !binding.getPool().getAllowedClientFamilies().contains(clientFamily.name())) {
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

    private boolean isNetworkHealthy(UpstreamAccountEntity account) {
        if (account.getProxyId() == null) {
            return true;
        }
        return networkProxyRepository.findById(account.getProxyId())
                .map(proxy -> proxy.isActive() && !"FAILED".equalsIgnoreCase(proxy.getLastStatus()))
                .orElse(false);
    }

    private String stickyKey(Long distributedKeyId, ProviderType providerType, GatewayClientFamily clientFamily) {
        return "xag:account:sticky:" + distributedKeyId + ":" + providerType.name() + ":" + clientFamily.name();
    }
}
