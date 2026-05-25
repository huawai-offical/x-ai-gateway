package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.RequestTraceDetailEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestTraceDetailRepository extends JpaRepository<RequestTraceDetailEntity, Long> {

    List<RequestTraceDetailEntity> findAllByRequestIdOrderByCreatedAtAscIdAsc(String requestId);

    List<RequestTraceDetailEntity> findAllByExpiresAtBeforeOrderByExpiresAtAscIdAsc(Instant cutoffAt, Pageable pageable);
}
