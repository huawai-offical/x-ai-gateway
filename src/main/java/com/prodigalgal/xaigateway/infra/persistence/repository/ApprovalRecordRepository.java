package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.ApprovalRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecordEntity, Long> {

    List<ApprovalRecordEntity> findAllByChangePlanIdOrderByDecisionAtAsc(Long changePlanId);
}
