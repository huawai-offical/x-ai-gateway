package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.InvitationRelationshipEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRelationshipRepository extends JpaRepository<InvitationRelationshipEntity, Long> {

    Optional<InvitationRelationshipEntity> findByInvitedUser_Id(Long invitedUserId);

    boolean existsByInvitedUser_Id(Long invitedUserId);

    List<InvitationRelationshipEntity> findAllByReferrerUser_IdOrderByCreatedAtDesc(Long referrerUserId);

    List<InvitationRelationshipEntity> findAllByOrderByCreatedAtDesc();
}
