package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.UserAccessGroupGrantEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccessGroupGrantRepository extends JpaRepository<UserAccessGroupGrantEntity, Long> {

    List<UserAccessGroupGrantEntity> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

    List<UserAccessGroupGrantEntity> findAllByUser_IdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    Optional<UserAccessGroupGrantEntity> findBySourceTypeAndSourceId(String sourceType, String sourceId);

    long countByAccessGroup_Id(Long accessGroupId);
}
