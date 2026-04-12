package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.OpsAlertRuleEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpsAlertRuleRepository extends JpaRepository<OpsAlertRuleEntity, Long> {
    List<OpsAlertRuleEntity> findAllByOrderByPriorityAscCreatedAtAsc();
    List<OpsAlertRuleEntity> findAllByEnabledTrueOrderByPriorityAscCreatedAtAsc();
}
