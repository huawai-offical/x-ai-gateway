package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserBalanceLedgerEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayUserBalanceLedgerRepository extends JpaRepository<GatewayUserBalanceLedgerEntity, Long> {

    Optional<GatewayUserBalanceLedgerEntity> findTopByUser_IdOrderByCreatedAtDescIdDesc(Long userId);

    List<GatewayUserBalanceLedgerEntity> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<GatewayUserBalanceLedgerEntity> findByReferenceTypeAndReferenceId(String referenceType, String referenceId);
}
