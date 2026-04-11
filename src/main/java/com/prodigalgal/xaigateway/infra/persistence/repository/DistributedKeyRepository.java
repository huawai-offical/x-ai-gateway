package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistributedKeyRepository extends JpaRepository<DistributedKeyEntity, Long> {

    Optional<DistributedKeyEntity> findByKeyPrefixAndActiveTrue(String keyPrefix);
}
