package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.RedeemCodeUsageEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RedeemCodeUsageRepository extends JpaRepository<RedeemCodeUsageEntity, Long> {

    boolean existsByRedeemCode_IdAndUser_Id(Long redeemCodeId, Long userId);

    long countByCampaign_IdAndUser_Id(Long campaignId, Long userId);

    List<RedeemCodeUsageEntity> findAllByUser_IdOrderByUsedAtDesc(Long userId);
}
