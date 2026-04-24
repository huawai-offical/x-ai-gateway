package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.DashboardExternalAppEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardExternalAppRepository extends JpaRepository<DashboardExternalAppEntity, Long> {
    List<DashboardExternalAppEntity> findAllByOrderByCreatedAtDesc();
    List<DashboardExternalAppEntity> findAllByEnabledTrueAndNavEnabledTrueOrderByAppNameAsc();
    Optional<DashboardExternalAppEntity> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
