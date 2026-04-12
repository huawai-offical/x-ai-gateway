package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.BackupJobEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackupJobRepository extends JpaRepository<BackupJobEntity, Long> {
    List<BackupJobEntity> findTop100ByOrderByCreatedAtDesc();
}
