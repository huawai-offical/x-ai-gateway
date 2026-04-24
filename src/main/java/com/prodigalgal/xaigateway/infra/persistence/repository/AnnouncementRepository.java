package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.AnnouncementEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<AnnouncementEntity, Long> {

    List<AnnouncementEntity> findAllByOrderByCreatedAtDesc();

    List<AnnouncementEntity> findAllByStatusOrderByPublishedAtDescCreatedAtDesc(String status);
}
