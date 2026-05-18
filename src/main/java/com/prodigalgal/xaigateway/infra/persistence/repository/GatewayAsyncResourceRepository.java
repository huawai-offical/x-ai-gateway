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

    boolean existsByResourceKey(String resourceKey);

    Optional<GatewayAsyncResourceEntity> findByResourceKeyAndDistributedKeyIdAndDeletedFalse(
            String resourceKey,
            Long distributedKeyId);

    Optional<GatewayAsyncResourceEntity> findByResourceKeyAndResourceTypeAndDeletedFalse(
            String resourceKey,
            GatewayAsyncResourceType resourceType);

    Optional<GatewayAsyncResourceEntity> findByResourceKeyAndResourceTypeAndDistributedKeyIdAndDeletedFalse(
            String resourceKey,
            GatewayAsyncResourceType resourceType,
            Long distributedKeyId);

    Optional<GatewayAsyncResourceEntity> findByDistributedKeyIdAndResourceTypeAndUpstreamObjectIdAndDeletedFalse(
            Long distributedKeyId,
            GatewayAsyncResourceType resourceType,
            String upstreamObjectId);

    List<GatewayAsyncResourceEntity> findAllByUpstreamObjectIdAndDeletedFalse(String upstreamObjectId);

    List<GatewayAsyncResourceEntity> findAllByDistributedKeyIdAndUpstreamObjectIdAndDeletedFalse(
            Long distributedKeyId,
            String upstreamObjectId);

    List<GatewayAsyncResourceEntity> findAllByDistributedKeyIdAndResourceTypeAndDeletedFalse(
            Long distributedKeyId,
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

    @Query("""
            select entity
            from GatewayAsyncResourceEntity entity
            where entity.deleted = false
              and entity.distributedKeyId = :distributedKeyId
              and entity.resourceType = :resourceType
              and entity.resourceKey like concat(:resourceKeyPrefix, '%')
              and (:model is null or entity.requestModel = :model)
              and (
                    :cursorCreatedAt is null
                    or entity.createdAt > :cursorCreatedAt
                    or (entity.createdAt = :cursorCreatedAt and entity.id > :cursorId)
                  )
            order by entity.createdAt asc, entity.id asc
            """)
    List<GatewayAsyncResourceEntity> findStoredResourcesAfterCursorAsc(
            @Param("distributedKeyId") Long distributedKeyId,
            @Param("resourceType") GatewayAsyncResourceType resourceType,
            @Param("resourceKeyPrefix") String resourceKeyPrefix,
            @Param("model") String model,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @Query("""
            select entity
            from GatewayAsyncResourceEntity entity
            where entity.deleted = false
              and entity.distributedKeyId = :distributedKeyId
              and entity.resourceType = :resourceType
              and entity.resourceKey like concat(:resourceKeyPrefix, '%')
              and (:model is null or entity.requestModel = :model)
              and (
                    :cursorCreatedAt is null
                    or entity.createdAt < :cursorCreatedAt
                    or (entity.createdAt = :cursorCreatedAt and entity.id < :cursorId)
                  )
            order by entity.createdAt desc, entity.id desc
            """)
    List<GatewayAsyncResourceEntity> findStoredResourcesAfterCursorDesc(
            @Param("distributedKeyId") Long distributedKeyId,
            @Param("resourceType") GatewayAsyncResourceType resourceType,
            @Param("resourceKeyPrefix") String resourceKeyPrefix,
            @Param("model") String model,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @Query("""
            select entity
            from GatewayAsyncResourceEntity entity
            where entity.deleted = false
              and entity.distributedKeyId = :distributedKeyId
              and entity.resourceType = :resourceType
              and entity.upstreamObjectId = :parentResourceKey
              and (
                    :cursorCreatedAt is null
                    or entity.createdAt > :cursorCreatedAt
                    or (entity.createdAt = :cursorCreatedAt and entity.id > :cursorId)
                  )
            order by entity.createdAt asc, entity.id asc
            """)
    List<GatewayAsyncResourceEntity> findChildResourcesAfterCursorAsc(
            @Param("distributedKeyId") Long distributedKeyId,
            @Param("resourceType") GatewayAsyncResourceType resourceType,
            @Param("parentResourceKey") String parentResourceKey,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @Query("""
            select entity
            from GatewayAsyncResourceEntity entity
            where entity.deleted = false
              and entity.distributedKeyId = :distributedKeyId
              and entity.resourceType = :resourceType
              and entity.upstreamObjectId = :parentResourceKey
              and (
                    :cursorCreatedAt is null
                    or entity.createdAt < :cursorCreatedAt
                    or (entity.createdAt = :cursorCreatedAt and entity.id < :cursorId)
                  )
            order by entity.createdAt desc, entity.id desc
            """)
    List<GatewayAsyncResourceEntity> findChildResourcesAfterCursorDesc(
            @Param("distributedKeyId") Long distributedKeyId,
            @Param("resourceType") GatewayAsyncResourceType resourceType,
            @Param("parentResourceKey") String parentResourceKey,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);
}
