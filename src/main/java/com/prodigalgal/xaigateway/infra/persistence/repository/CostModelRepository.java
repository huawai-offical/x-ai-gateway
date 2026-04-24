package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.CostModelEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CostModelRepository extends JpaRepository<CostModelEntity, Long> {
    List<CostModelEntity> findAllByOrderByCreatedAtDesc();
    Optional<CostModelEntity> findFirstByProviderTypeAndModelNameAndActiveTrueOrderByUpdatedAtDesc(String providerType, String modelName);
}
