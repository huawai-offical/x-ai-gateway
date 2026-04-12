package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.OpsScheduledProbeJobEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpsScheduledProbeJobRepository extends JpaRepository<OpsScheduledProbeJobEntity, Long> {
    List<OpsScheduledProbeJobEntity> findAllByEnabledTrueOrderByCreatedAtAsc();
    List<OpsScheduledProbeJobEntity> findAllByOrderByCreatedAtDesc();
}
