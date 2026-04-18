package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.RunbookLinkEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunbookLinkRepository extends JpaRepository<RunbookLinkEntity, Long> {

    List<RunbookLinkEntity> findAllByEnabledTrueOrderByCreatedAtDesc();

    List<RunbookLinkEntity> findAllByOrderByCreatedAtDesc();
}
