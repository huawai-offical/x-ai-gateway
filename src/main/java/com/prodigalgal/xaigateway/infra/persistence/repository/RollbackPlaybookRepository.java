package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.RollbackPlaybookEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RollbackPlaybookRepository extends JpaRepository<RollbackPlaybookEntity, Long> {

    Optional<RollbackPlaybookEntity> findByChangePlanId(Long changePlanId);
}
