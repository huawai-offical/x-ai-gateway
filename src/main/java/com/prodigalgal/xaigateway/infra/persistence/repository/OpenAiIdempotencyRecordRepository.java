package com.prodigalgal.xaigateway.infra.persistence.repository;

import com.prodigalgal.xaigateway.infra.persistence.entity.OpenAiIdempotencyRecordEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpenAiIdempotencyRecordRepository extends JpaRepository<OpenAiIdempotencyRecordEntity, Long> {

    Optional<OpenAiIdempotencyRecordEntity> findByDistributedKeyIdAndRequestPathAndIdempotencyKey(
            Long distributedKeyId,
            String requestPath,
            String idempotencyKey);

    long deleteByCreatedAtBefore(Instant cutoff);
}
