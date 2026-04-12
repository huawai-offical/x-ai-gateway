package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.OpsAlertEventEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpsAlertEventRepository extends JpaRepository<OpsAlertEventEntity, Long> {
    List<OpsAlertEventEntity> findTop100ByOrderByCreatedAtDesc();
    List<OpsAlertEventEntity> findTop100ByStatusOrderByCreatedAtDesc(String status);
}
