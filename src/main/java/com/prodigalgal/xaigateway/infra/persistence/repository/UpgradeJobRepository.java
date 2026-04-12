package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.UpgradeJobEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UpgradeJobRepository extends JpaRepository<UpgradeJobEntity, Long> {
    List<UpgradeJobEntity> findTop100ByOrderByCreatedAtDesc();
}
