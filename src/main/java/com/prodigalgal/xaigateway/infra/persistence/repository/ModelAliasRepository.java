package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.ModelAliasEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelAliasRepository extends JpaRepository<ModelAliasEntity, Long> {

    Optional<ModelAliasEntity> findByAliasKeyAndEnabledTrue(String aliasKey);

    List<ModelAliasEntity> findAllByEnabledTrueOrderByAliasNameAsc();
}
