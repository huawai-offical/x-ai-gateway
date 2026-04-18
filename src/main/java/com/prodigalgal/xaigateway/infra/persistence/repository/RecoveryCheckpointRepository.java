package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.RecoveryCheckpointEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecoveryCheckpointRepository extends JpaRepository<RecoveryCheckpointEntity, Long> {

    List<RecoveryCheckpointEntity> findTop200ByOrderByCreatedAtDesc();
}
