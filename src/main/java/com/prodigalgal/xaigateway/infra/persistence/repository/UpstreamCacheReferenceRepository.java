package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCacheReferenceEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UpstreamCacheReferenceRepository extends JpaRepository<UpstreamCacheReferenceEntity, Long> {

    Optional<UpstreamCacheReferenceEntity> findByDistributedKeyIdAndProviderTypeAndModelGroupAndPrefixHash(
            Long distributedKeyId,
            ProviderType providerType,
            String modelGroup,
            String prefixHash
    );

    List<UpstreamCacheReferenceEntity> findTop100ByOrderByUpdatedAtDesc();

    List<UpstreamCacheReferenceEntity> findTop100ByDistributedKeyIdOrderByUpdatedAtDesc(Long distributedKeyId);

    List<UpstreamCacheReferenceEntity> findTop100ByStatusOrderByUpdatedAtDesc(String status);

    @Query("""
            select entity
            from UpstreamCacheReferenceEntity entity
            where (:distributedKeyId is null or entity.distributedKeyId = :distributedKeyId)
              and (:providerType is null or entity.providerType = :providerType)
              and (:status is null or entity.status = :status)
            order by entity.updatedAt desc
            """)
    List<UpstreamCacheReferenceEntity> search(
            @Param("distributedKeyId") Long distributedKeyId,
            @Param("providerType") ProviderType providerType,
            @Param("status") String status,
            Pageable pageable);

    @Query("""
            select entity
            from UpstreamCacheReferenceEntity entity
            where (:distributedKeyId is null or entity.distributedKeyId = :distributedKeyId)
              and (:providerType is null or entity.providerType = :providerType)
              and (:status is null or entity.status = :status)
              and entity.updatedAt >= :from
              and entity.updatedAt <= :to
            order by entity.updatedAt desc
            """)
    List<UpstreamCacheReferenceEntity> searchWithinWindow(
            @Param("distributedKeyId") Long distributedKeyId,
            @Param("providerType") ProviderType providerType,
            @Param("status") String status,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
