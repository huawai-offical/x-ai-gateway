package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.RecoveryCheckpointResponse;
import com.prodigalgal.xaigateway.admin.application.operations.RecoveryCheckpointStatus;
import com.prodigalgal.xaigateway.infra.persistence.entity.ChangePlanEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.RecoveryCheckpointEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.RecoveryCheckpointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class RecoveryCheckpointService {

    private final RecoveryCheckpointRepository recoveryCheckpointRepository;
    private final MetadataSnapshotService metadataSnapshotService;
    private final RuntimeStateSnapshotService runtimeStateSnapshotService;
    private final DataSnapshotService dataSnapshotService;
    private final ObjectMapper objectMapper;

    public RecoveryCheckpointService(
            RecoveryCheckpointRepository recoveryCheckpointRepository,
            MetadataSnapshotService metadataSnapshotService,
            RuntimeStateSnapshotService runtimeStateSnapshotService,
            DataSnapshotService dataSnapshotService,
            ObjectMapper objectMapper) {
        this.recoveryCheckpointRepository = recoveryCheckpointRepository;
        this.metadataSnapshotService = metadataSnapshotService;
        this.runtimeStateSnapshotService = runtimeStateSnapshotService;
        this.dataSnapshotService = dataSnapshotService;
        this.objectMapper = objectMapper;
    }

    public RecoveryCheckpointResponse createCheckpointForPlan(ChangePlanEntity changePlan) {
        String checkpointName = "cp-" + changePlan.getId() + "-" + Instant.now().toEpochMilli();
        MetadataSnapshotService.SnapshotResult metadataSnapshot = metadataSnapshotService.createSnapshot(checkpointName);
        RuntimeStateSnapshotService.SnapshotResult runtimeSnapshot = runtimeStateSnapshotService.createSnapshot(checkpointName);
        DataSnapshotService.SnapshotResult dataSnapshot = dataSnapshotService.createSnapshot(checkpointName);

        RecoveryCheckpointEntity entity = new RecoveryCheckpointEntity();
        entity.setCheckpointName(checkpointName);
        entity.setChangePlanId(changePlan.getId());
        entity.setStatus(RecoveryCheckpointStatus.READY.name());
        entity.setMetadataSnapshotPath(metadataSnapshot.manifestPath());
        entity.setRuntimeSnapshotPath(runtimeSnapshot.manifestPath());
        entity.setDataSnapshotPath(dataSnapshot.manifestPath());
        entity.setManifestJson(writeJson(Map.of(
                "checkpointName", checkpointName,
                "changePlanId", changePlan.getId(),
                "metadataSnapshot", metadataSnapshot.manifestJson(),
                "runtimeSnapshot", runtimeSnapshot.manifestJson(),
                "dataSnapshot", dataSnapshot.manifestJson()
        )));
        return toResponse(recoveryCheckpointRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<RecoveryCheckpointResponse> list() {
        return recoveryCheckpointRepository.findTop200ByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RecoveryCheckpointResponse get(Long checkpointId) {
        return toResponse(getEntity(checkpointId));
    }

    public RecoveryCheckpointResponse verify(Long checkpointId, String verifiedBy) {
        RecoveryCheckpointEntity entity = getEntity(checkpointId);
        MetadataSnapshotService.VerificationResult metadata = metadataSnapshotService.verifySnapshot(entity.getMetadataSnapshotPath());
        RuntimeStateSnapshotService.VerificationResult runtime = runtimeStateSnapshotService.verifySnapshot(entity.getRuntimeSnapshotPath());
        DataSnapshotService.VerificationResult data = dataSnapshotService.verifySnapshot(entity.getDataSnapshotPath());
        boolean success = metadata.success() && runtime.success() && data.success();
        entity.setVerificationStatus(success ? RecoveryCheckpointStatus.VERIFIED.name() : RecoveryCheckpointStatus.FAILED.name());
        entity.setVerificationMessage(writeJson(Map.of(
                "metadata", metadata.message(),
                "runtime", runtime.message(),
                "data", data.message()
        )));
        entity.setVerifiedAt(Instant.now());
        entity.setVerifiedBy(verifiedBy);
        return toResponse(recoveryCheckpointRepository.save(entity));
    }

    public void restore(Long checkpointId) {
        RecoveryCheckpointEntity entity = getEntity(checkpointId);
        metadataSnapshotService.restoreSnapshot(entity.getMetadataSnapshotPath());
        runtimeStateSnapshotService.restoreSnapshot(entity.getRuntimeSnapshotPath());
        dataSnapshotService.restoreSnapshot(entity.getDataSnapshotPath());
    }

    public RecoveryCheckpointEntity getEntity(Long checkpointId) {
        return recoveryCheckpointRepository.findById(checkpointId)
                .orElseThrow(() -> new IllegalArgumentException("未找到 recovery checkpoint。"));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化 recovery checkpoint 失败。", exception);
        }
    }

    private RecoveryCheckpointResponse toResponse(RecoveryCheckpointEntity entity) {
        return new RecoveryCheckpointResponse(
                entity.getId(),
                entity.getCheckpointName(),
                entity.getChangePlanId(),
                entity.getStatus(),
                entity.getMetadataSnapshotPath(),
                entity.getRuntimeSnapshotPath(),
                entity.getDataSnapshotPath(),
                entity.getManifestJson(),
                entity.getVerificationStatus(),
                entity.getVerificationMessage(),
                entity.getVerifiedAt(),
                entity.getVerifiedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
