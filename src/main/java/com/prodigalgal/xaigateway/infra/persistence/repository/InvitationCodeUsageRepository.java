package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.InvitationCodeUsageEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationCodeUsageRepository extends JpaRepository<InvitationCodeUsageEntity, Long> {

    boolean existsByInvitationCode_IdAndUser_Id(Long invitationCodeId, Long userId);

    List<InvitationCodeUsageEntity> findAllByInvitationCode_IdOrderByUsedAtDesc(Long invitationCodeId);

    List<InvitationCodeUsageEntity> findAllByUser_IdOrderByUsedAtDesc(Long userId);

    List<InvitationCodeUsageEntity> findAllByReferrerUser_IdOrderByUsedAtDesc(Long referrerUserId);

    List<InvitationCodeUsageEntity> findAllByReferrerUserIsNotNullOrderByUsedAtDesc();
}
