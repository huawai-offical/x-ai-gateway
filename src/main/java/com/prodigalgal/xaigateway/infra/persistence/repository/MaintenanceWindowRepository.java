package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.MaintenanceWindowEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface MaintenanceWindowRepository extends JpaRepository<MaintenanceWindowEntity, Long> {

    List<MaintenanceWindowEntity> findTop200ByOrderByStartsAtDesc();

    List<MaintenanceWindowEntity> findAllByEnabledTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(Instant start, Instant end);
}
