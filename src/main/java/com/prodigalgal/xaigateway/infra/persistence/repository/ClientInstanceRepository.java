package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.ClientInstanceEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientInstanceRepository extends JpaRepository<ClientInstanceEntity, Long> {

    List<ClientInstanceEntity> findAllByOrderByUpdatedAtDesc();

    List<ClientInstanceEntity> findAllByDistributedKey_IdOrderByUpdatedAtDesc(Long distributedKeyId);

    Optional<ClientInstanceEntity> findByDistributedKey_IdAndInstanceId(Long distributedKeyId, String instanceId);
}
