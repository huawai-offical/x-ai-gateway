package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountPoolEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UpstreamAccountPoolRepository extends JpaRepository<UpstreamAccountPoolEntity, Long> {
    List<UpstreamAccountPoolEntity> findAllByOrderByCreatedAtDesc();

    Optional<UpstreamAccountPoolEntity> findByPoolNameIgnoreCase(String poolName);

    boolean existsByPoolNameIgnoreCase(String poolName);
}
