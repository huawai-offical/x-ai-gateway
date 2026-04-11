package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayFileBindingEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayFileBindingRepository extends JpaRepository<GatewayFileBindingEntity, Long> {

    List<GatewayFileBindingEntity> findAllByGatewayFileIdOrderByCreatedAtDesc(Long gatewayFileId);
}
