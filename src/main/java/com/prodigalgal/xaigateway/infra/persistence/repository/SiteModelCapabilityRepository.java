package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.SiteModelCapabilityEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteModelCapabilityRepository extends JpaRepository<SiteModelCapabilityEntity, Long> {

    List<SiteModelCapabilityEntity> findAllBySiteProfile_IdInAndActiveTrue(Collection<Long> siteProfileIds);

    List<SiteModelCapabilityEntity> findAllByModelKeyAndActiveTrue(String modelKey);

    List<SiteModelCapabilityEntity> findAllBySiteProfile_IdOrderByModelKeyAsc(Long siteProfileId);

    void deleteAllBySiteProfile_Id(Long siteProfileId);
}
