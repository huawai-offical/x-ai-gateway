package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.CacheHitLogEntity;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CacheHitLogRepository extends JpaRepository<CacheHitLogEntity, Long> {

    List<CacheHitLogEntity> findTop100ByOrderByCreatedAtDesc();

    List<CacheHitLogEntity> findTop100ByDistributedKeyIdOrderByCreatedAtDesc(Long distributedKeyId);

    List<CacheHitLogEntity> findTop100ByProviderTypeOrderByCreatedAtDesc(ProviderType providerType);

    @Query("""
            select entity
            from CacheHitLogEntity entity
            where (:distributedKeyId is null or entity.distributedKeyId = :distributedKeyId)
              and (:providerType is null or entity.providerType = :providerType)
            order by entity.createdAt desc
            """)
    List<CacheHitLogEntity> search(
            @Param("distributedKeyId") Long distributedKeyId,
            @Param("providerType") ProviderType providerType,
            Pageable pageable);

    @Query("""
            select entity
            from CacheHitLogEntity entity
            where (:distributedKeyId is null or entity.distributedKeyId = :distributedKeyId)
              and (:providerType is null or entity.providerType = :providerType)
              and entity.createdAt >= :from
              and entity.createdAt <= :to
            order by entity.createdAt desc
            """)
    List<CacheHitLogEntity> searchWithinWindow(
            @Param("distributedKeyId") Long distributedKeyId,
            @Param("providerType") ProviderType providerType,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
