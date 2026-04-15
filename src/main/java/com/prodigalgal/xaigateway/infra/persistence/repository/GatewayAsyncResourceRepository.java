package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceType;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayAsyncResourceEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GatewayAsyncResourceRepository extends JpaRepository<GatewayAsyncResourceEntity, Long> {

    Optional<GatewayAsyncResourceEntity> findByResourceKeyAndDeletedFalse(String resourceKey);

    Optional<GatewayAsyncResourceEntity> findByResourceKeyAndResourceTypeAndDeletedFalse(
            String resourceKey,
            GatewayAsyncResourceType resourceType);

    @Query("""
            select entity
            from GatewayAsyncResourceEntity entity
            where entity.deleted = false
              and (:distributedKeyId is null or entity.distributedKeyId = :distributedKeyId)
              and (:resourceType is null or entity.resourceType = :resourceType)
              and (:status is null or lower(entity.status) = :status)
            order by entity.createdAt desc
            """)
    List<GatewayAsyncResourceEntity> search(
            @Param("distributedKeyId") Long distributedKeyId,
            @Param("resourceType") GatewayAsyncResourceType resourceType,
            @Param("status") String status,
            Pageable pageable);

    @Query("""
            select entity
            from GatewayAsyncResourceEntity entity
            where entity.deleted = false
              and (:distributedKeyId is null or entity.distributedKeyId = :distributedKeyId)
              and (:resourceType is null or entity.resourceType = :resourceType)
              and (:status is null or lower(entity.status) = :status)
              and entity.createdAt >= :from
              and entity.createdAt <= :to
            order by entity.createdAt desc
            """)
    List<GatewayAsyncResourceEntity> searchWithinWindow(
            @Param("distributedKeyId") Long distributedKeyId,
            @Param("resourceType") GatewayAsyncResourceType resourceType,
            @Param("status") String status,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
