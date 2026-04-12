package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.OpsOperationAuditEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpsOperationAuditRepository extends JpaRepository<OpsOperationAuditEntity, Long> {
    List<OpsOperationAuditEntity> findTop200ByOrderByCreatedAtDesc();
}
