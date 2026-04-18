package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.ChangePlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ChangePlanRepository extends JpaRepository<ChangePlanEntity, Long> {

    List<ChangePlanEntity> findTop200ByOrderByCreatedAtDesc();

    boolean existsByStatusIn(Collection<String> statuses);
}
