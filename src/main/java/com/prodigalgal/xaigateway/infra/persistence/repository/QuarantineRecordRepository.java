package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.gateway.core.governance.QuarantineStatus;
import com.prodigalgal.xaigateway.infra.persistence.entity.QuarantineRecordEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuarantineRecordRepository extends JpaRepository<QuarantineRecordEntity, Long> {

    List<QuarantineRecordEntity> findAllByOrderByStartedAtDesc();

    List<QuarantineRecordEntity> findAllByStatusOrderByStartedAtDesc(QuarantineStatus status);
}
