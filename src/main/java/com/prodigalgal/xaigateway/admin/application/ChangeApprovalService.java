package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ApprovalRecordResponse;
import com.prodigalgal.xaigateway.admin.application.operations.ApprovalDecision;
import com.prodigalgal.xaigateway.infra.persistence.entity.ApprovalRecordEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.ChangePlanEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.ApprovalRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class ChangeApprovalService {

    private final ApprovalRecordRepository approvalRecordRepository;

    public ChangeApprovalService(ApprovalRecordRepository approvalRecordRepository) {
        this.approvalRecordRepository = approvalRecordRepository;
    }

    public ApprovalRecordResponse approve(ChangePlanEntity changePlan, String actor, String reason) {
        return toResponse(saveRecord(changePlan.getId(), ApprovalDecision.APPROVED, actor, reason));
    }

    public ApprovalRecordResponse reject(ChangePlanEntity changePlan, String actor, String reason) {
        return toResponse(saveRecord(changePlan.getId(), ApprovalDecision.REJECTED, actor, reason));
    }

    public ApprovalRecordResponse recordSystemApproval(ChangePlanEntity changePlan, String reason) {
        return toResponse(saveRecord(changePlan.getId(), ApprovalDecision.SYSTEM_APPROVED, "SYSTEM", reason));
    }

    @Transactional(readOnly = true)
    public List<ApprovalRecordResponse> listByPlanId(Long changePlanId) {
        return approvalRecordRepository.findAllByChangePlanIdOrderByDecisionAtAsc(changePlanId).stream()
                .map(this::toResponse)
                .toList();
    }

    private ApprovalRecordEntity saveRecord(Long changePlanId, ApprovalDecision decision, String actor, String reason) {
        ApprovalRecordEntity entity = new ApprovalRecordEntity();
        entity.setChangePlanId(changePlanId);
        entity.setDecision(decision.name());
        entity.setActor(actor == null || actor.isBlank() ? "SYSTEM" : actor);
        entity.setReason(reason);
        entity.setDecisionAt(Instant.now());
        return approvalRecordRepository.save(entity);
    }

    private ApprovalRecordResponse toResponse(ApprovalRecordEntity entity) {
        return new ApprovalRecordResponse(
                entity.getId(),
                entity.getDecision(),
                entity.getActor(),
                entity.getReason(),
                entity.getDecisionAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
