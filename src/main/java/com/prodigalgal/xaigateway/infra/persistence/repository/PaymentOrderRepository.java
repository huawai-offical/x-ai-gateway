package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.PaymentOrderEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrderEntity, Long> {

    Optional<PaymentOrderEntity> findByOrderNo(String orderNo);

    List<PaymentOrderEntity> findAllByOrderByCreatedAtDesc();

    List<PaymentOrderEntity> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

    List<PaymentOrderEntity> findAllByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);
}
