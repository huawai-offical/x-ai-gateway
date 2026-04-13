package com.prodigalgal.xaigateway.gateway.core.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.infra.persistence.entity.AuditLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.AuditLogRepository;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GatewayAuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public GatewayAuditLogService(
            AuditLogRepository auditLogRepository,
            ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void recordAdminApiAction(
            String requestId,
            String action,
            String path,
            String status,
            String actor,
            Map<String, Object> detail) {
        persist("ADMIN_API", action, "HTTP_ENDPOINT", path, status, actor, requestId, path, detail);
    }

    public void recordGatewayEvent(
            String requestId,
            String action,
            String status,
            Map<String, Object> detail) {
        persist("GATEWAY_EVENT", action, "REQUEST", requestId, status, "system", requestId, null, detail);
    }

    private void persist(
            String auditType,
            String action,
            String targetType,
            String targetId,
            String status,
            String actor,
            String requestId,
            String path,
            Map<String, Object> detail) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setRequestId(requestId);
        entity.setAuditType(auditType);
        entity.setAction(action);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setStatus(status);
        entity.setActor(actor == null || actor.isBlank() ? "system" : actor);
        entity.setPath(path);
        entity.setDetailJson(toJson(detail));
        auditLogRepository.save(entity);
    }

    private String toJson(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException exception) {
            return "{\"serializationError\":true,\"createdAt\":\"" + Instant.now() + "\"}";
        }
    }
}
