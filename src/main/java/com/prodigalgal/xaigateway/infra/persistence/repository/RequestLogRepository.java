package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.RequestLogEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestLogRepository extends JpaRepository<RequestLogEntity, Long> {

    Optional<RequestLogEntity> findByRequestId(String requestId);
}
