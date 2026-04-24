package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.AnnouncementReadStateEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementReadStateRepository extends JpaRepository<AnnouncementReadStateEntity, Long> {

    List<AnnouncementReadStateEntity> findAllByUser_Id(Long userId);

    Optional<AnnouncementReadStateEntity> findByAnnouncement_IdAndUser_Id(Long announcementId, Long userId);

    boolean existsByAnnouncement_IdAndUser_Id(Long announcementId, Long userId);
}
