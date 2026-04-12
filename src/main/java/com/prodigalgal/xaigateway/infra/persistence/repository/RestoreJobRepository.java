package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.RestoreJobEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestoreJobRepository extends JpaRepository<RestoreJobEntity, Long> {
    List<RestoreJobEntity> findTop100ByOrderByCreatedAtDesc();
}
