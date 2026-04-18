package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.AutoActionRuleEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutoActionRuleRepository extends JpaRepository<AutoActionRuleEntity, Long> {

    List<AutoActionRuleEntity> findAllByOrderByCreatedAtDesc();

    List<AutoActionRuleEntity> findAllByEnabledTrueOrderByCreatedAtAsc();
}
