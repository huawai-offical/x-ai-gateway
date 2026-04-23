package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.NotificationChannelEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationChannelRepository extends JpaRepository<NotificationChannelEntity, Long> {

    List<NotificationChannelEntity> findAllByOrderByCreatedAtDesc();

    long countByWebhookEndpointId(Long webhookEndpointId);
}
