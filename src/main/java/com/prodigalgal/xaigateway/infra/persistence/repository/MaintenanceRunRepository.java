package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.MaintenanceRunEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceRunRepository extends JpaRepository<MaintenanceRunEntity, Long> {
    List<MaintenanceRunEntity> findTop100ByOrderByCreatedAtDesc();
}
