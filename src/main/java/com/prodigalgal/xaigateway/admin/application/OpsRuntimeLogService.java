package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.OpsRuntimeLogSettingRequest;
import com.prodigalgal.xaigateway.admin.api.OpsRuntimeLogSettingResponse;
import com.prodigalgal.xaigateway.gateway.core.ops.OpsEventBusService;
import com.prodigalgal.xaigateway.gateway.core.ops.OpsEventType;
import com.prodigalgal.xaigateway.infra.persistence.entity.OpsRuntimeLogSettingEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.OpsRuntimeLogSettingRepository;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class OpsRuntimeLogService {

    private final OpsRuntimeLogSettingRepository opsRuntimeLogSettingRepository;
    private final LoggingSystem loggingSystem;
    private final OpsAuditService opsAuditService;
    private final OpsEventBusService opsEventBusService;

    public OpsRuntimeLogService(
            OpsRuntimeLogSettingRepository opsRuntimeLogSettingRepository,
            LoggingSystem loggingSystem,
            OpsAuditService opsAuditService,
            OpsEventBusService opsEventBusService) {
        this.opsRuntimeLogSettingRepository = opsRuntimeLogSettingRepository;
        this.loggingSystem = loggingSystem;
        this.opsAuditService = opsAuditService;
        this.opsEventBusService = opsEventBusService;
    }

    @Transactional(readOnly = true)
    public List<OpsRuntimeLogSettingResponse> list() {
        return opsRuntimeLogSettingRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    public OpsRuntimeLogSettingResponse save(Long id, OpsRuntimeLogSettingRequest request) {
        OpsRuntimeLogSettingEntity entity = id == null
                ? new OpsRuntimeLogSettingEntity()
                : opsRuntimeLogSettingRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("未找到日志设置。"));
        entity.setLoggerName(request.loggerName());
        entity.setLogLevel(request.logLevel());
        entity.setPayloadLoggingEnabled(Boolean.TRUE.equals(request.payloadLoggingEnabled()));
        entity.setEnabled(request.enabled() == null || request.enabled());
        OpsRuntimeLogSettingEntity saved = opsRuntimeLogSettingRepository.save(entity);
        loggingSystem.setLogLevel(saved.getLoggerName(), LogLevel.valueOf(saved.getLogLevel()));
        OpsRuntimeLogSettingResponse response = toResponse(saved);
        opsAuditService.record("OPS", "RUNTIME_LOG_SETTING_UPDATED", "ops_runtime_log_setting", String.valueOf(saved.getId()), saved.getLoggerName());
        opsEventBusService.publish(OpsEventType.RUNTIME_LOG_SETTING_CHANGED, response);
        return response;
    }

    private OpsRuntimeLogSettingResponse toResponse(OpsRuntimeLogSettingEntity entity) {
        return new OpsRuntimeLogSettingResponse(
                entity.getId(),
                entity.getLoggerName(),
                entity.getLogLevel(),
                entity.isPayloadLoggingEnabled(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
