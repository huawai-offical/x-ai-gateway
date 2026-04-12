package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.ReleaseArtifactEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReleaseArtifactRepository extends JpaRepository<ReleaseArtifactEntity, Long> {
    List<ReleaseArtifactEntity> findAllByOrderByCreatedAtDesc();
}
