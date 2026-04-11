package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.CredentialModelCatalogEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredentialModelCatalogRepository extends JpaRepository<CredentialModelCatalogEntity, Long> {

    List<CredentialModelCatalogEntity> findAllByCredentialIdInAndActiveTrue(Collection<Long> credentialIds);

    List<CredentialModelCatalogEntity> findAllByModelKeyAndActiveTrue(String modelKey);

    List<CredentialModelCatalogEntity> findAllByModelKeyInAndActiveTrue(Collection<String> modelKeys);

    void deleteAllByCredentialId(Long credentialId);
}
