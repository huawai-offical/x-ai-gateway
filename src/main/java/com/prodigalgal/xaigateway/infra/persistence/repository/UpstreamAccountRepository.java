package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UpstreamAccountRepository extends JpaRepository<UpstreamAccountEntity, Long> {
    List<UpstreamAccountEntity> findAllByPool_IdOrderByCreatedAtDesc(Long poolId);
    List<UpstreamAccountEntity> findAllByPool_IdAndActiveTrueAndFrozenFalseAndHealthyTrueOrderByUpdatedAtDesc(Long poolId);
    List<UpstreamAccountEntity> findAllByProviderTypeAndActiveTrueAndFrozenFalseAndHealthyTrueOrderByUpdatedAtDesc(UpstreamAccountProviderType providerType);
    List<UpstreamAccountEntity> findAllBySiteProfileIdAndActiveTrueAndFrozenFalseAndHealthyTrueOrderByUpdatedAtDesc(Long siteProfileId);
}
