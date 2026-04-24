package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.SubscriptionPlanEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlanEntity, Long> {

    List<SubscriptionPlanEntity> findAllByOrderByCreatedAtDesc();

    List<SubscriptionPlanEntity> findAllByActiveOrderByCreatedAtDesc(boolean active);

    boolean existsByPlanNameIgnoreCase(String planName);

    boolean existsByPlanNameIgnoreCaseAndIdNot(String planName, Long id);
}
