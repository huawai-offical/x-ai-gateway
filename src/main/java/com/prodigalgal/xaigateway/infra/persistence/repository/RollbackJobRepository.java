package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.RollbackJobEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RollbackJobRepository extends JpaRepository<RollbackJobEntity, Long> {
    List<RollbackJobEntity> findTop100ByOrderByCreatedAtDesc();
}
