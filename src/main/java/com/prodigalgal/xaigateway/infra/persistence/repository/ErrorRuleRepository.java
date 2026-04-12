package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.ErrorRuleEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErrorRuleRepository extends JpaRepository<ErrorRuleEntity, Long> {
    List<ErrorRuleEntity> findAllByOrderByPriorityAscCreatedAtAsc();
    List<ErrorRuleEntity> findAllByEnabledTrueOrderByPriorityAscCreatedAtAsc();
}
