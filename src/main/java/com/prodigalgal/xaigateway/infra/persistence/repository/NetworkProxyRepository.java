package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.NetworkProxyEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NetworkProxyRepository extends JpaRepository<NetworkProxyEntity, Long> {
    List<NetworkProxyEntity> findAllByOrderByCreatedAtDesc();
}
