package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountGroupEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UpstreamAccountGroupRepository extends JpaRepository<UpstreamAccountGroupEntity, Long> {
    List<UpstreamAccountGroupEntity> findAllByOrderByCreatedAtDesc();

    Optional<UpstreamAccountGroupEntity> findByGroupNameIgnoreCase(String groupName);

    boolean existsByGroupNameIgnoreCase(String groupName);
}
