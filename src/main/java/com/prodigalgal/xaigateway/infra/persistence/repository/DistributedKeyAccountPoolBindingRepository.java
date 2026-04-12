package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyAccountPoolBindingEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistributedKeyAccountPoolBindingRepository extends JpaRepository<DistributedKeyAccountPoolBindingEntity, Long> {
    List<DistributedKeyAccountPoolBindingEntity> findAllByDistributedKey_IdAndActiveTrueOrderByPriorityAscCreatedAtAsc(Long distributedKeyId);
    List<DistributedKeyAccountPoolBindingEntity> findAllByDistributedKey_IdAndProviderTypeAndActiveTrueOrderByPriorityAscCreatedAtAsc(Long distributedKeyId, ProviderType providerType);
}
