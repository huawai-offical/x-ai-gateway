package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.gateway.core.model.ModelPolicyScopeType;
import com.prodigalgal.xaigateway.infra.persistence.entity.ModelPolicyEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelPolicyRepository extends JpaRepository<ModelPolicyEntity, Long> {

    List<ModelPolicyEntity> findAllByEnabledTrueOrderByPriorityAscCreatedAtAsc();

    List<ModelPolicyEntity> findAllByScopeTypeAndEnabledTrueOrderByPriorityAscCreatedAtAsc(ModelPolicyScopeType scopeType);

    List<ModelPolicyEntity> findAllByScopeTypeAndScopeIdInAndEnabledTrueOrderByPriorityAscCreatedAtAsc(
            ModelPolicyScopeType scopeType,
            Collection<Long> scopeIds);

    List<ModelPolicyEntity> findAllByScopeTypeAndScopeRefInAndEnabledTrueOrderByPriorityAscCreatedAtAsc(
            ModelPolicyScopeType scopeType,
            Collection<String> scopeRefs);

    List<ModelPolicyEntity> findAllByScopeTypeAndScopeIdAndEnabledTrueOrderByPriorityAscCreatedAtAsc(
            ModelPolicyScopeType scopeType,
            Long scopeId);
}
