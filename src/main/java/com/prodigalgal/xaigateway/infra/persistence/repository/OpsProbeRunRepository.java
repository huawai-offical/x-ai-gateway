package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.OpsProbeRunEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpsProbeRunRepository extends JpaRepository<OpsProbeRunEntity, Long> {
    List<OpsProbeRunEntity> findTop100ByOrderByCreatedAtDesc();
}
