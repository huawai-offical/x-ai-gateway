package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.RollbackPlaybookResponse;
import com.prodigalgal.xaigateway.admin.application.operations.RollbackPlaybookStatus;
import com.prodigalgal.xaigateway.infra.persistence.entity.RollbackPlaybookEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.RollbackPlaybookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class RollbackPlaybookService {

    private final RollbackPlaybookRepository rollbackPlaybookRepository;
    private final ObjectMapper objectMapper;

    public RollbackPlaybookService(
            RollbackPlaybookRepository rollbackPlaybookRepository,
            ObjectMapper objectMapper) {
        this.rollbackPlaybookRepository = rollbackPlaybookRepository;
        this.objectMapper = objectMapper;
    }

    public RollbackPlaybookResponse ensureForUpgradePlan(Long changePlanId) {
        return rollbackPlaybookRepository.findByChangePlanId(changePlanId)
                .map(this::toResponse)
                .orElseGet(() -> toResponse(rollbackPlaybookRepository.save(newEntity(changePlanId))));
    }

    public RollbackPlaybookResponse attachCheckpoint(Long playbookId, Long checkpointId, Long rollbackReleaseArtifactId) {
        RollbackPlaybookEntity entity = rollbackPlaybookRepository.findById(playbookId)
                .orElseThrow(() -> new IllegalArgumentException("未找到 rollback playbook。"));
        entity.setRecoveryCheckpointId(checkpointId);
        entity.setRollbackReleaseArtifactId(rollbackReleaseArtifactId);
        return toResponse(rollbackPlaybookRepository.save(entity));
    }

    public RollbackPlaybookResponse markTriggered(Long playbookId, Long rollbackPlanId) {
        RollbackPlaybookEntity entity = rollbackPlaybookRepository.findById(playbookId)
                .orElseThrow(() -> new IllegalArgumentException("未找到 rollback playbook。"));
        entity.setStatus(RollbackPlaybookStatus.TRIGGERED.name());
        entity.setLatestRollbackPlanId(rollbackPlanId);
        entity.setLastTriggeredAt(Instant.now());
        return toResponse(rollbackPlaybookRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public RollbackPlaybookResponse get(Long playbookId) {
        return rollbackPlaybookRepository.findById(playbookId).map(this::toResponse).orElse(null);
    }

    private RollbackPlaybookEntity newEntity(Long changePlanId) {
        RollbackPlaybookEntity entity = new RollbackPlaybookEntity();
        entity.setChangePlanId(changePlanId);
        entity.setStatus(RollbackPlaybookStatus.ACTIVE.name());
        entity.setTriggerConditionsJson(writeJson(List.of(
                "CANARY_VERIFY failed",
                "FULL_VERIFY failed",
                "health check failed",
                "manual abort"
        )));
        return entity;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化 rollback playbook 失败。", exception);
        }
    }

    private RollbackPlaybookResponse toResponse(RollbackPlaybookEntity entity) {
        return new RollbackPlaybookResponse(
                entity.getId(),
                entity.getRecoveryCheckpointId(),
                entity.getRollbackReleaseArtifactId(),
                entity.getStatus(),
                entity.getTriggerConditionsJson(),
                entity.getLatestRollbackPlanId(),
                entity.getLastTriggeredAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
