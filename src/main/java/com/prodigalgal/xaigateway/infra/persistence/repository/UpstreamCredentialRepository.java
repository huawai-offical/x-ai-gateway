package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UpstreamCredentialRepository extends JpaRepository<UpstreamCredentialEntity, Long> {

    List<UpstreamCredentialEntity> findAllByDeletedFalseOrderByCreatedAtDesc();

    List<UpstreamCredentialEntity> findAllByGroupIdAndDeletedFalseOrderByCreatedAtDesc(Long groupId);

    List<UpstreamCredentialEntity> findAllByIdInAndDeletedFalse(Collection<Long> ids);

    List<UpstreamCredentialEntity> findAllByProviderTypeAndDeletedFalseAndActiveTrue(ProviderType providerType);

    List<UpstreamCredentialEntity> findAllBySiteProfileIdAndDeletedFalseOrderByCreatedAtDesc(Long siteProfileId);

    List<UpstreamCredentialEntity> findAllBySiteProfileIdAndDeletedFalseAndActiveTrueOrderByCreatedAtDesc(Long siteProfileId);

    List<UpstreamCredentialEntity> findAllBySiteProfileIdInAndDeletedFalseAndActiveTrue(Collection<Long> siteProfileIds);

    long countBySiteProfileIdAndDeletedFalse(Long siteProfileId);

    long countByGroupIdAndDeletedFalse(Long groupId);

    Optional<UpstreamCredentialEntity> findByApiKeyFingerprintAndProviderTypeAndBaseUrlAndSiteProfileIdAndDeletedFalse(
            String apiKeyFingerprint,
            ProviderType providerType,
            String baseUrl,
            Long siteProfileId);

    Optional<UpstreamCredentialEntity> findFirstByApiKeyFingerprintAndProviderTypeAndBaseUrlAndSiteProfileIdOrderByUpdatedAtDesc(
            String apiKeyFingerprint,
            ProviderType providerType,
            String baseUrl,
            Long siteProfileId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UpstreamCredentialEntity credential set credential.groupId = null where credential.groupId = :groupId")
    int clearGroupReferenceByGroupId(@Param("groupId") Long groupId);
}
