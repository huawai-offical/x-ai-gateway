package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.ReleaseRolloutEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReleaseRolloutRepository extends JpaRepository<ReleaseRolloutEntity, Long> {

    List<ReleaseRolloutEntity> findAllByChangePlanIdOrderByCreatedAtAsc(Long changePlanId);
}
