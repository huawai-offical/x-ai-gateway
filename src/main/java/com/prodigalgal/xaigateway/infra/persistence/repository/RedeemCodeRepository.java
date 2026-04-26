package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.RedeemCodeEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RedeemCodeRepository extends JpaRepository<RedeemCodeEntity, Long> {

    List<RedeemCodeEntity> findAllByCampaign_IdOrderByCreatedAtDesc(Long campaignId);

    Optional<RedeemCodeEntity> findByIdAndCampaign_Id(Long id, Long campaignId);

    Optional<RedeemCodeEntity> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
