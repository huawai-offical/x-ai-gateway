package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.OutboundDeliveryEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboundDeliveryRepository extends JpaRepository<OutboundDeliveryEntity, Long> {

    List<OutboundDeliveryEntity> findTop200ByOrderByOccurredAtDesc();

    List<OutboundDeliveryEntity> findTop50ByEventTypeOrderByOccurredAtDesc(String eventType);

    List<OutboundDeliveryEntity> findTop50ByEventTypeInOrderByOccurredAtDesc(List<String> eventTypes);

    List<OutboundDeliveryEntity> findAllByDeliveryStatusInAndNextRetryAtLessThanEqualOrderByOccurredAtAsc(
            List<String> deliveryStatuses,
            Instant dueAt);
}
