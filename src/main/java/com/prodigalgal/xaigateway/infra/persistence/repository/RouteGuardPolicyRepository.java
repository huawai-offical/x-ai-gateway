package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.RouteGuardPolicyEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteGuardPolicyRepository extends JpaRepository<RouteGuardPolicyEntity, Long> {

    List<RouteGuardPolicyEntity> findAllByOrderByPriorityAscCreatedAtAsc();

    List<RouteGuardPolicyEntity> findAllByEnabledTrueOrderByPriorityAscCreatedAtAsc();
}
