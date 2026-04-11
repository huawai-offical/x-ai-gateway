package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountPoolEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UpstreamAccountPoolRepository extends JpaRepository<UpstreamAccountPoolEntity, Long> {
    List<UpstreamAccountPoolEntity> findAllByOrderByCreatedAtDesc();
}
