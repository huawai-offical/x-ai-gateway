package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.OutboundEventSubscriptionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboundEventSubscriptionRepository extends JpaRepository<OutboundEventSubscriptionEntity, Long> {

    List<OutboundEventSubscriptionEntity> findAllByEnabledTrueOrderByCreatedAtDesc();

    List<OutboundEventSubscriptionEntity> findAllByOrderByCreatedAtDesc();

    long countByChannelId(Long channelId);
}
