package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayFileEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayFileRepository extends JpaRepository<GatewayFileEntity, Long> {

    List<GatewayFileEntity> findTop100ByDistributedKeyIdAndDeletedFalseOrderByCreatedAtDesc(Long distributedKeyId);

    Optional<GatewayFileEntity> findByFileKeyAndDeletedFalse(String fileKey);
}
