package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyBindingEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistributedKeyBindingRepository extends JpaRepository<DistributedKeyBindingEntity, Long> {

    List<DistributedKeyBindingEntity> findAllByDistributedKeyIdAndActiveTrueOrderByPriorityAscCreatedAtAsc(Long distributedKeyId);
}
