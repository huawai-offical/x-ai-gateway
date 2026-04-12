package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.InstallationStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstallationStateRepository extends JpaRepository<InstallationStateEntity, Long> {
}
