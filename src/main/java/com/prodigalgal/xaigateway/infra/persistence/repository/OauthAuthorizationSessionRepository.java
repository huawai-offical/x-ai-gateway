package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.OauthAuthorizationSessionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OauthAuthorizationSessionRepository extends JpaRepository<OauthAuthorizationSessionEntity, Long> {
    Optional<OauthAuthorizationSessionEntity> findBySessionKey(String sessionKey);
}
