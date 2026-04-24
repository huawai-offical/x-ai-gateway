package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.PromoCampaignEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromoCampaignRepository extends JpaRepository<PromoCampaignEntity, Long> {

    List<PromoCampaignEntity> findAllByOrderByCreatedAtDesc();

    boolean existsByCampaignNameIgnoreCase(String campaignName);

    boolean existsByCampaignNameIgnoreCaseAndIdNot(String campaignName, Long id);
}
