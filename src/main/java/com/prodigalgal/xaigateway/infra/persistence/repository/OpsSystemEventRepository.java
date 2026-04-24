package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.OpsSystemEventEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpsSystemEventRepository extends JpaRepository<OpsSystemEventEntity, Long> {
    List<OpsSystemEventEntity> findTop500ByOrderByOccurredAtDesc();
}
