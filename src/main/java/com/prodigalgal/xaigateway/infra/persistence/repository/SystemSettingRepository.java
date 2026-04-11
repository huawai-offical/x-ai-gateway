package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.SystemSettingEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingRepository extends JpaRepository<SystemSettingEntity, Long> {

    Optional<SystemSettingEntity> findBySettingKey(String settingKey);
}
