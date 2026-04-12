package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.SiteCapabilitySnapshotEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteCapabilitySnapshotRepository extends JpaRepository<SiteCapabilitySnapshotEntity, Long> {

    Optional<SiteCapabilitySnapshotEntity> findBySiteProfile_Id(Long siteProfileId);
}
