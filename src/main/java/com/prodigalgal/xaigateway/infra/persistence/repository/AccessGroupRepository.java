package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.AccessGroupEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessGroupRepository extends JpaRepository<AccessGroupEntity, Long> {

    List<AccessGroupEntity> findAllByOrderByCreatedAtDesc();

    List<AccessGroupEntity> findAllByActiveOrderByCreatedAtDesc(boolean active);

    boolean existsByGroupNameIgnoreCase(String groupName);

    boolean existsByGroupNameIgnoreCaseAndIdNot(String groupName, Long id);
}
