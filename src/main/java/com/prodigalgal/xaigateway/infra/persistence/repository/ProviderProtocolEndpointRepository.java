package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.ProviderProtocolEndpointEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderProtocolEndpointRepository extends JpaRepository<ProviderProtocolEndpointEntity, Long> {

    List<ProviderProtocolEndpointEntity> findAllBySiteProfileIdOrderByDisplayNameAsc(Long siteProfileId);

    List<ProviderProtocolEndpointEntity> findAllBySiteProfileIdAndActiveTrueOrderByDisplayNameAsc(Long siteProfileId);

    Optional<ProviderProtocolEndpointEntity> findBySiteProfileIdAndProtocolSuite(Long siteProfileId, String protocolSuite);

    long countBySiteProfileId(Long siteProfileId);

    void deleteAllBySiteProfileId(Long siteProfileId);
}
