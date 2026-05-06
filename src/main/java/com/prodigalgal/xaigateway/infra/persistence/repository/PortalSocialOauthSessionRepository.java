package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.PortalSocialOauthSessionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortalSocialOauthSessionRepository extends JpaRepository<PortalSocialOauthSessionEntity, Long> {

    Optional<PortalSocialOauthSessionEntity> findByState(String state);
}
