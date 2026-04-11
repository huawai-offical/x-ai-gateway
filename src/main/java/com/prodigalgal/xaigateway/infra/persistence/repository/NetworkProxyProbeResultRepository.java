package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.NetworkProxyProbeResultEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NetworkProxyProbeResultRepository extends JpaRepository<NetworkProxyProbeResultEntity, Long> {
    List<NetworkProxyProbeResultEntity> findTop50ByProxyIdOrderByCreatedAtDesc(Long proxyId);
}
