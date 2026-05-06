package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserSocialIdentityEntity;
import com.prodigalgal.xaigateway.portal.application.SocialOAuthProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayUserSocialIdentityRepository extends JpaRepository<GatewayUserSocialIdentityEntity, Long> {

    Optional<GatewayUserSocialIdentityEntity> findByProviderAndExternalSubject(
            SocialOAuthProvider provider,
            String externalSubject);

    List<GatewayUserSocialIdentityEntity> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<GatewayUserSocialIdentityEntity> findByUser_IdAndProviderAndExternalSubject(
            Long userId,
            SocialOAuthProvider provider,
            String externalSubject);
}
