package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.OpsRuntimeLogSettingEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpsRuntimeLogSettingRepository extends JpaRepository<OpsRuntimeLogSettingEntity, Long> {
    List<OpsRuntimeLogSettingEntity> findAllByOrderByCreatedAtDesc();
}
