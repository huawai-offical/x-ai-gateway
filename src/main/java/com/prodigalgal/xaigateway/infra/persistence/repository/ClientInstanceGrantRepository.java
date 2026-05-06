package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.ClientInstanceGrantEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientInstanceGrantRepository extends JpaRepository<ClientInstanceGrantEntity, Long> {

    List<ClientInstanceGrantEntity> findAllByClientInstance_Id(Long clientInstanceId);

    Optional<ClientInstanceGrantEntity> findByClientInstance_IdAndTokenHash(Long clientInstanceId, String tokenHash);

    void deleteAllByClientInstance_Id(Long clientInstanceId);
}
