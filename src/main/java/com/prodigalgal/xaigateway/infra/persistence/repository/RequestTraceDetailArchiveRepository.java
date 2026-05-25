package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.RequestTraceDetailArchiveEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestTraceDetailArchiveRepository extends JpaRepository<RequestTraceDetailArchiveEntity, Long> {
}
