package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.PlanAccessGroupEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanAccessGroupRepository extends JpaRepository<PlanAccessGroupEntity, Long> {

    List<PlanAccessGroupEntity> findAllByAccessGroup_IdOrderByPriorityAscCreatedAtAsc(Long accessGroupId);

    List<PlanAccessGroupEntity> findAllByPlan_IdAndActiveTrueOrderByPriorityAscCreatedAtAsc(Long planId);

    List<PlanAccessGroupEntity> findAllByPlan_IdInAndActiveTrueOrderByPriorityAscCreatedAtAsc(List<Long> planIds);

    Optional<PlanAccessGroupEntity> findByPlan_IdAndAccessGroup_Id(Long planId, Long accessGroupId);

    long countByAccessGroup_Id(Long accessGroupId);

    void deleteAllByAccessGroup_Id(Long accessGroupId);

    void deleteByPlan_IdAndAccessGroup_Id(Long planId, Long accessGroupId);
}
