package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamSiteProfileEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UpstreamSiteProfileRepository extends JpaRepository<UpstreamSiteProfileEntity, Long> {

    List<UpstreamSiteProfileEntity> findAllByActiveTrueOrderByDisplayNameAsc();

    Optional<UpstreamSiteProfileEntity> findByProfileCode(String profileCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from UpstreamSiteProfileEntity profile where profile.id = :id")
    Optional<UpstreamSiteProfileEntity> findByIdForUpdate(@Param("id") Long id);
}
