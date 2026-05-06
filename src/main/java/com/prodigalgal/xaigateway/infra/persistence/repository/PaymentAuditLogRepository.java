package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.PaymentAuditLogEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAuditLogRepository extends JpaRepository<PaymentAuditLogEntity, Long> {

    Optional<PaymentAuditLogEntity> findByIdempotencyKey(String idempotencyKey);
}
