package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.SloPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SloPolicyRepository extends JpaRepository<SloPolicyEntity, Long> {

    List<SloPolicyEntity> findAllByOrderByCreatedAtAsc();

    List<SloPolicyEntity> findAllByEnabledTrueOrderByCreatedAtAsc();
}
