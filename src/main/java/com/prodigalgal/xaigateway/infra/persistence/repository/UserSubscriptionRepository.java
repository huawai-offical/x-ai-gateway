package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.UserSubscriptionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscriptionEntity, Long> {

    List<UserSubscriptionEntity> findAllByOrderByCreatedAtDesc();

    List<UserSubscriptionEntity> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<UserSubscriptionEntity> findBySourceTypeAndSourceId(String sourceType, String sourceId);

    long countByUser_Id(Long userId);

    long countByPlan_Id(Long planId);
}
