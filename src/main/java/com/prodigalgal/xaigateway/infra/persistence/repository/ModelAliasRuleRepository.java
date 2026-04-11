package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.ModelAliasRuleEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelAliasRuleRepository extends JpaRepository<ModelAliasRuleEntity, Long> {

    List<ModelAliasRuleEntity> findAllByAliasIdAndEnabledTrueOrderByPriorityAscCreatedAtAsc(Long aliasId);
}
