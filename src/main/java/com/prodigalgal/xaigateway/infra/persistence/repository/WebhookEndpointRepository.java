package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.WebhookEndpointEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpointEntity, Long> {

    List<WebhookEndpointEntity> findAllByOrderByCreatedAtDesc();
}
