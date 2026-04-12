package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UpstreamSiteProfileRepository extends JpaRepository<UpstreamSiteProfileEntity, Long> {

    List<UpstreamSiteProfileEntity> findAllByActiveTrueOrderByDisplayNameAsc();

    Optional<UpstreamSiteProfileEntity> findByProfileCode(String profileCode);
}
