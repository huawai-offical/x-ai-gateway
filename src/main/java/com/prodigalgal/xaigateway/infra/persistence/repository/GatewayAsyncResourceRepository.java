package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceType;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayAsyncResourceEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayAsyncResourceRepository extends JpaRepository<GatewayAsyncResourceEntity, Long> {

    Optional<GatewayAsyncResourceEntity> findByResourceKeyAndDeletedFalse(String resourceKey);

    Optional<GatewayAsyncResourceEntity> findByResourceKeyAndResourceTypeAndDeletedFalse(
            String resourceKey,
            GatewayAsyncResourceType resourceType);
}
