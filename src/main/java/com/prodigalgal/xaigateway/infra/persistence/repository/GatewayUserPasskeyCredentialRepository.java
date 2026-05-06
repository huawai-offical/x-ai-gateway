package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserPasskeyCredentialEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayUserPasskeyCredentialRepository extends JpaRepository<GatewayUserPasskeyCredentialEntity, Long> {

    List<GatewayUserPasskeyCredentialEntity> findAllByUser_IdAndActiveTrueOrderByCreatedAtDesc(Long userId);

    Optional<GatewayUserPasskeyCredentialEntity> findByCredentialIdAndActiveTrue(String credentialId);

    Optional<GatewayUserPasskeyCredentialEntity> findByUser_IdAndIdAndActiveTrue(Long userId, Long id);

    long countByUser_IdAndActiveTrue(Long userId);
}
