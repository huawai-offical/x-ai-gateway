package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeySecretExportGrantEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistributedKeySecretExportGrantRepository
        extends JpaRepository<DistributedKeySecretExportGrantEntity, Long> {

    Optional<DistributedKeySecretExportGrantEntity> findByDistributedKey_IdAndTokenHash(
            Long distributedKeyId,
            String tokenHash);

    void deleteAllByDistributedKey_Id(Long distributedKeyId);
}
