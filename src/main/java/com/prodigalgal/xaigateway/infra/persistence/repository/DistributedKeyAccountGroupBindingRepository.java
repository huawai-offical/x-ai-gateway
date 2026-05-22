package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyAccountGroupBindingEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistributedKeyAccountGroupBindingRepository extends JpaRepository<DistributedKeyAccountGroupBindingEntity, Long> {
    List<DistributedKeyAccountGroupBindingEntity> findAllByDistributedKey_IdAndActiveTrueOrderByPriorityAscCreatedAtAsc(Long distributedKeyId);
    List<DistributedKeyAccountGroupBindingEntity> findAllByDistributedKey_IdAndProviderTypeAndActiveTrueOrderByPriorityAscCreatedAtAsc(Long distributedKeyId, ProviderType providerType);
    List<DistributedKeyAccountGroupBindingEntity> findAllByGroup_Id(Long groupId);
    long countByDistributedKey_IdAndActiveTrue(Long distributedKeyId);
    void deleteAllByGroup_Id(Long groupId);
    void deleteAllByDistributedKey_Id(Long distributedKeyId);
}
