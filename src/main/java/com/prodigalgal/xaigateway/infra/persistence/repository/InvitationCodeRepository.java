package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.InvitationCodeEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvitationCodeRepository extends JpaRepository<InvitationCodeEntity, Long> {

    List<InvitationCodeEntity> findAllByOrderByCreatedAtDesc();

    List<InvitationCodeEntity> findAllByActiveOrderByCreatedAtDesc(boolean active);

    Optional<InvitationCodeEntity> findByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InvitationCodeEntity> findFirstByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query("""
            select code
            from InvitationCodeEntity code
            where (:keyword is null
                   or lower(code.code) like lower(concat('%', :keyword, '%'))
                   or lower(code.notes) like lower(concat('%', :keyword, '%')))
              and (:active is null or code.active = :active)
            order by code.createdAt desc
            """)
    List<InvitationCodeEntity> search(
            @Param("keyword") String keyword,
            @Param("active") Boolean active);

    @Query("""
            select case when count(code) > 0 then true else false end
            from InvitationCodeEntity code
            where code.active = true
              and code.usedCount < code.maxUses
              and (code.expiresAt is null or code.expiresAt > CURRENT_TIMESTAMP)
            """)
    boolean existsUsableCode();
}
