package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyAccessGroupGrantEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistributedKeyAccessGroupGrantRepository extends JpaRepository<DistributedKeyAccessGroupGrantEntity, Long> {

    List<DistributedKeyAccessGroupGrantEntity> findAllByAccessGroup_IdOrderByPriorityAscCreatedAtAsc(Long accessGroupId);

    List<DistributedKeyAccessGroupGrantEntity> findAllByDistributedKey_IdAndActiveTrueOrderByPriorityAscCreatedAtAsc(Long distributedKeyId);

    List<DistributedKeyAccessGroupGrantEntity> findAllByDistributedKey_IdInAndActiveTrueOrderByPriorityAscCreatedAtAsc(List<Long> distributedKeyIds);

    Optional<DistributedKeyAccessGroupGrantEntity> findByDistributedKey_IdAndAccessGroup_Id(Long distributedKeyId, Long accessGroupId);

    long countByAccessGroup_Id(Long accessGroupId);

    void deleteAllByAccessGroup_Id(Long accessGroupId);

    void deleteAllByDistributedKey_Id(Long distributedKeyId);

    void deleteByDistributedKey_IdAndAccessGroup_Id(Long distributedKeyId, Long accessGroupId);
}
