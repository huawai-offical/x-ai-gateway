package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayUserRepository extends JpaRepository<GatewayUserEntity, Long> {

    List<GatewayUserEntity> findAllByOrderByCreatedAtDesc();

    List<GatewayUserEntity> findAllByActiveOrderByCreatedAtDesc(boolean active);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}
