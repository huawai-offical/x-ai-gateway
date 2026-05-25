package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.RequestLogEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequestLogRepository extends JpaRepository<RequestLogEntity, Long> {

    Optional<RequestLogEntity> findByRequestId(String requestId);
    List<RequestLogEntity> findAllByRequestIdIn(Iterable<String> requestIds);

    List<RequestLogEntity> findTop100ByGatewayResourceKeyOrderByCreatedAtDesc(String gatewayResourceKey);

    List<RequestLogEntity> findTop100ByClientFamilyAndClientInstanceOrderByStartedAtDesc(String clientFamily, String clientInstance);

    @Query("""
            select entity
            from RequestLogEntity entity
            where (:distributedKeyId is null or entity.distributedKeyId = :distributedKeyId)
              and (:providerType is null or entity.providerType = :providerType)
            order by entity.createdAt desc
            """)
    List<RequestLogEntity> search(
            @Param("distributedKeyId") Long distributedKeyId,
            @Param("providerType") ProviderType providerType,
            Pageable pageable);

    @Query("""
            select entity
            from RequestLogEntity entity
            where (:distributedKeyId is null or entity.distributedKeyId = :distributedKeyId)
              and (:providerType is null or entity.providerType = :providerType)
              and entity.createdAt >= :from
              and entity.createdAt <= :to
            order by entity.createdAt desc
            """)
    List<RequestLogEntity> searchWithinWindow(
            @Param("distributedKeyId") Long distributedKeyId,
            @Param("providerType") ProviderType providerType,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            select entity
            from RequestLogEntity entity
            where (:providerType is null or entity.providerType = :providerType)
              and (:credentialId is null or entity.credentialId = :credentialId)
              and entity.startedAt >= :from
              and entity.startedAt <= :to
            order by entity.startedAt desc
            """)
    List<RequestLogEntity> searchHealthWithinWindow(
            @Param("providerType") ProviderType providerType,
            @Param("credentialId") Long credentialId,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
