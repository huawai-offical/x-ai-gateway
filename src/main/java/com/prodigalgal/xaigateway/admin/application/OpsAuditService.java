package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.OpsOperationAuditResponse;
import com.prodigalgal.xaigateway.gateway.core.ops.OpsEventBusService;
import com.prodigalgal.xaigateway.gateway.core.ops.OpsEventType;
import com.prodigalgal.xaigateway.infra.persistence.entity.OpsOperationAuditEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.OpsOperationAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class OpsAuditService {

    private final OpsOperationAuditRepository opsOperationAuditRepository;
    private final OpsEventBusService opsEventBusService;

    public OpsAuditService(
            OpsOperationAuditRepository opsOperationAuditRepository,
            OpsEventBusService opsEventBusService) {
        this.opsOperationAuditRepository = opsOperationAuditRepository;
        this.opsEventBusService = opsEventBusService;
    }

    public void record(String category, String action, String resourceType, String resourceRef, String detailJson) {
        OpsOperationAuditEntity entity = new OpsOperationAuditEntity();
        entity.setCategory(category);
        entity.setAction(action);
        entity.setResourceType(resourceType);
        entity.setResourceRef(resourceRef);
        entity.setDetailJson(detailJson);
        OpsOperationAuditEntity saved = opsOperationAuditRepository.save(entity);
        opsEventBusService.publish(OpsEventType.SYSTEM_LOG, toResponse(saved));
    }

    @Transactional(readOnly = true)
    public List<OpsOperationAuditResponse> listRecent() {
        return opsOperationAuditRepository.findTop200ByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    private OpsOperationAuditResponse toResponse(OpsOperationAuditEntity entity) {
        return new OpsOperationAuditResponse(
                entity.getId(),
                entity.getCategory(),
                entity.getAction(),
                entity.getResourceType(),
                entity.getResourceRef(),
                entity.getDetailJson(),
                entity.getCreatedAt()
        );
    }
}
