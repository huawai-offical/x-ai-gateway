package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UpstreamCredentialRepository extends JpaRepository<UpstreamCredentialEntity, Long> {

    List<UpstreamCredentialEntity> findAllByDeletedFalseOrderByCreatedAtDesc();

    List<UpstreamCredentialEntity> findAllByIdInAndDeletedFalse(Collection<Long> ids);

    List<UpstreamCredentialEntity> findAllByProviderTypeAndDeletedFalseAndActiveTrue(ProviderType providerType);

    Optional<UpstreamCredentialEntity> findByApiKeyFingerprintAndDeletedFalse(String apiKeyFingerprint);
}
