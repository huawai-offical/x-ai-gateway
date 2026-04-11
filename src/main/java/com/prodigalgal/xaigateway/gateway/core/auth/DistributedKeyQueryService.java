package com.prodigalgal.xaigateway.gateway.core.auth;

import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyBindingEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
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

    public DistributedKeyQueryService(
            DistributedKeyRepository distributedKeyRepository,
            DistributedKeyBindingRepository distributedKeyBindingRepository) {
        this.distributedKeyRepository = distributedKeyRepository;
        this.distributedKeyBindingRepository = distributedKeyBindingRepository;
    }

    public Optional<DistributedKeyView> findActiveByKeyPrefix(String keyPrefix) {
        return distributedKeyRepository.findByKeyPrefixAndActiveTrue(keyPrefix)
                .map(this::toView);
    }

    private DistributedKeyView toView(DistributedKeyEntity entity) {
        List<DistributedCredentialBindingView> bindings = distributedKeyBindingRepository
                .findAllByDistributedKeyIdAndActiveTrueOrderByPriorityAscCreatedAtAsc(entity.getId())
                .stream()
                .map(this::toBindingView)
                .toList();

        return new DistributedKeyView(
                entity.getId(),
                entity.getKeyName(),
                entity.getKeyPrefix(),
                entity.getMaskedKey(),
                entity.getAllowedProtocols(),
                entity.getAllowedModels(),
                entity.getAllowedProviderTypes(),
                entity.getExpiresAt(),
                entity.getBudgetLimitMicros(),
                entity.getBudgetWindowSeconds(),
                entity.getRpmLimit(),
                entity.getTpmLimit(),
                entity.getConcurrencyLimit(),
                entity.getStickySessionTtlSeconds(),
                entity.getAllowedClientFamilies(),
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
