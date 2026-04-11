package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.RouteDecisionLogEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteDecisionLogRepository extends JpaRepository<RouteDecisionLogEntity, Long> {

    List<RouteDecisionLogEntity> findTop100ByOrderByCreatedAtDesc();

    List<RouteDecisionLogEntity> findTop100ByDistributedKeyIdOrderByCreatedAtDesc(Long distributedKeyId);

    @Query("""
            select entity
            from RouteDecisionLogEntity entity
            where (:distributedKeyId is null or entity.distributedKeyId = :distributedKeyId)
              and (:providerType is null or entity.selectedProviderType = :providerType)
            order by entity.createdAt desc
            """)
    List<RouteDecisionLogEntity> search(
            @Param("distributedKeyId") Long distributedKeyId,
            @Param("providerType") ProviderType providerType,
            Pageable pageable);

    @Query("""
            select entity
            from RouteDecisionLogEntity entity
            where (:distributedKeyId is null or entity.distributedKeyId = :distributedKeyId)
              and (:providerType is null or entity.selectedProviderType = :providerType)
              and entity.createdAt >= :from
              and entity.createdAt <= :to
            order by entity.createdAt desc
            """)
    List<RouteDecisionLogEntity> searchWithinWindow(
            @Param("distributedKeyId") Long distributedKeyId,
            @Param("providerType") ProviderType providerType,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
