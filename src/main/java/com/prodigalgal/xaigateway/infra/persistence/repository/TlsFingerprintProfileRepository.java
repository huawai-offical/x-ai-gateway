package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.TlsFingerprintProfileEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TlsFingerprintProfileRepository extends JpaRepository<TlsFingerprintProfileEntity, Long> {
    List<TlsFingerprintProfileEntity> findAllByOrderByCreatedAtDesc();
}
