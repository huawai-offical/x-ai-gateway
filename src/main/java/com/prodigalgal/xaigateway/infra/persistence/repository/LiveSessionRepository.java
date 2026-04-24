package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.LiveSessionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveSessionRepository extends JpaRepository<LiveSessionEntity, Long> {
    Optional<LiveSessionEntity> findBySessionKey(String sessionKey);
    Optional<LiveSessionEntity> findByResumeToken(String resumeToken);
    List<LiveSessionEntity> findTop100ByOrderByCreatedAtDesc();
}
