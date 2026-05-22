package com.prodigalgal.xaigateway.gateway.core.auth;

import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccountGroupBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyBindingRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DistributedKeyQueryService {

    private final DistributedKeyRepository distributedKeyRepository;
    private final DistributedKeyBindingRepository distributedKeyBindingRepository;
    private final DistributedKeyAccountGroupBindingRepository distributedKeyAccountGroupBindingRepository;
    private final AccessGroupEntitlementService accessGroupEntitlementService;

    public DistributedKeyQueryService(
            DistributedKeyRepository distributedKeyRepository,
            DistributedKeyBindingRepository distributedKeyBindingRepository,
            DistributedKeyAccountGroupBindingRepository distributedKeyAccountGroupBindingRepository,
            AccessGroupEntitlementService accessGroupEntitlementService) {
        this.distributedKeyRepository = distributedKeyRepository;
        this.distributedKeyBindingRepository = distributedKeyBindingRepository;
        this.distributedKeyAccountGroupBindingRepository = distributedKeyAccountGroupBindingRepository;
        this.accessGroupEntitlementService = accessGroupEntitlementService;
    }

    public Optional<DistributedKeyView> findActiveByKeyPrefix(String keyPrefix) {
        return distributedKeyRepository.findByKeyPrefixAndActiveTrue(keyPrefix)
                .filter(entity -> hasActiveGroupBinding(entity.getId()))
                .map(this::toView);
    }

    public Optional<DistributedKeyView> findActiveById(Long id) {
        return distributedKeyRepository.findByIdAndActiveTrue(id)
                .filter(entity -> hasActiveGroupBinding(entity.getId()))
                .map(this::toView);
    }

    public List<DistributedKeyView> listActive() {
        return distributedKeyRepository.findAll().stream()
                .filter(DistributedKeyEntity::isActive)
                .filter(entity -> hasActiveGroupBinding(entity.getId()))
                .map(this::toView)
                .toList();
    }

    private boolean hasActiveGroupBinding(Long distributedKeyId) {
        return distributedKeyAccountGroupBindingRepository
                .countByDistributedKey_IdAndActiveTrue(distributedKeyId) > 0;
    }

    private DistributedKeyView toView(DistributedKeyEntity entity) {
        List<DistributedCredentialBindingView> bindings = distributedKeyBindingRepository
                .findAllByDistributedKeyIdAndActiveTrueOrderByPriorityAscCreatedAtAsc(entity.getId())
                .stream()
                .map(this::toBindingView)
                .toList();

        ResolvedAccessPolicy policy = accessGroupEntitlementService.resolveForDistributedKey(entity);
        return new DistributedKeyView(
                entity.getId(),
                entity.getKeyName(),
                entity.getKeyPrefix(),
                entity.getMaskedKey(),
                policy.allowedProtocolSuites(),
                policy.allowedModels(),
                policy.allowedProviderTypes(),
                entity.getExpiresAt(),
                entity.getBudgetLimitMicros(),
                entity.getBudgetWindowSeconds(),
                policy.rpmLimit(),
                policy.tpmLimit(),
                policy.concurrencyLimit(),
                entity.getStickySessionTtlSeconds(),
                policy.allowedClientFamilies(),
                entity.isRequireClientFamilyMatch(),
                bindings
        );
    }

    private DistributedCredentialBindingView toBindingView(DistributedKeyBindingEntity entity) {
        return new DistributedCredentialBindingView(
                entity.getId(),
                entity.getCredential().getId(),
                entity.getCredential().getCredentialName(),
                entity.getCredential().getProviderType(),
                entity.getCredential().getBaseUrl(),
                entity.getPriority(),
                entity.getWeight()
        );
    }
}
