package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UpstreamAccountRepository extends JpaRepository<UpstreamAccountEntity, Long> {
    List<UpstreamAccountEntity> findAllByOrderByCreatedAtDesc();
    List<UpstreamAccountEntity> findAllByGroup_IdOrderByCreatedAtDesc(Long groupId);
    List<UpstreamAccountEntity> findAllByGroup_IdAndActiveTrueAndFrozenFalseAndHealthyTrueOrderByUpdatedAtDesc(Long groupId);
    List<UpstreamAccountEntity> findAllByProviderTypeOrderByUpdatedAtDesc(UpstreamAccountProviderType providerType);
    List<UpstreamAccountEntity> findAllByProviderTypeAndActiveTrueAndFrozenFalseAndHealthyTrueOrderByUpdatedAtDesc(UpstreamAccountProviderType providerType);
    List<UpstreamAccountEntity> findAllBySiteProfileIdAndActiveTrueAndFrozenFalseAndHealthyTrueOrderByUpdatedAtDesc(Long siteProfileId);
    Optional<UpstreamAccountEntity> findFirstByProviderTypeAndExternalAccountIdOrderByUpdatedAtDesc(UpstreamAccountProviderType providerType, String externalAccountId);
    long countByGroup_Id(Long groupId);
    long countByProxyId(Long proxyId);
    long countByTlsFingerprintProfileId(Long tlsFingerprintProfileId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UpstreamAccountEntity account set account.group = null where account.group.id = :groupId")
    int clearGroupReferenceByGroupId(@Param("groupId") Long groupId);
}
