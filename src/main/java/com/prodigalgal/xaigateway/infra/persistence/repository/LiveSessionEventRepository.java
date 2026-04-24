package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.LiveSessionEventEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveSessionEventRepository extends JpaRepository<LiveSessionEventEntity, Long> {
    List<LiveSessionEventEntity> findAllBySession_IdOrderByEventIdAsc(Long sessionId);
    List<LiveSessionEventEntity> findAllBySession_IdAndEventIdGreaterThanOrderByEventIdAsc(Long sessionId, long eventId);
}
