package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.MaintenanceWindowRequest;
import com.prodigalgal.xaigateway.admin.api.MaintenanceWindowResponse;
import com.prodigalgal.xaigateway.infra.persistence.entity.MaintenanceWindowEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.ChangePlanRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.MaintenanceWindowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class MaintenanceWindowService {

    private final MaintenanceWindowRepository maintenanceWindowRepository;
    private final ChangePlanRepository changePlanRepository;

    public MaintenanceWindowService(
            MaintenanceWindowRepository maintenanceWindowRepository,
            ChangePlanRepository changePlanRepository) {
        this.maintenanceWindowRepository = maintenanceWindowRepository;
        this.changePlanRepository = changePlanRepository;
    }

    @Transactional(readOnly = true)
    public List<MaintenanceWindowResponse> list(Instant now) {
        Instant effectiveNow = now == null ? Instant.now() : now;
        return maintenanceWindowRepository.findTop200ByOrderByStartsAtDesc().stream()
                .map(item -> toResponse(item, effectiveNow))
                .toList();
    }

    public MaintenanceWindowResponse save(Long id, MaintenanceWindowRequest request) {
        MaintenanceWindowEntity entity = id == null
                ? new MaintenanceWindowEntity()
                : maintenanceWindowRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("未找到维护窗口。"));
        entity.setWindowName(request.windowName());
        entity.setScopeType(request.scopeType());
        entity.setScopeRef(request.scopeRef());
        entity.setStartsAt(request.startsAt());
        entity.setEndsAt(request.endsAt());
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setDescription(request.description());
        return toResponse(maintenanceWindowRepository.save(entity), Instant.now());
    }

    public void delete(Long id) {
        MaintenanceWindowEntity entity = maintenanceWindowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到维护窗口。"));
        long planCount = changePlanRepository.countByMaintenanceWindowId(id);
        if (planCount > 0) {
            throw new IllegalArgumentException("该维护窗口仍被变更计划引用，请先调整相关计划。");
        }
        maintenanceWindowRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public boolean isActive(Long maintenanceWindowId, Instant now) {
        if (maintenanceWindowId == null) {
            return false;
        }
        MaintenanceWindowEntity entity = maintenanceWindowRepository.findById(maintenanceWindowId)
                .orElseThrow(() -> new IllegalArgumentException("未找到维护窗口。"));
        return entity.isEnabled() && !now.isBefore(entity.getStartsAt()) && !now.isAfter(entity.getEndsAt());
    }

    @Transactional(readOnly = true)
    public MaintenanceWindowEntity getById(Long maintenanceWindowId) {
        return maintenanceWindowRepository.findById(maintenanceWindowId)
                .orElseThrow(() -> new IllegalArgumentException("未找到维护窗口。"));
    }

    private MaintenanceWindowResponse toResponse(MaintenanceWindowEntity entity, Instant now) {
        boolean activeNow = entity.isEnabled() && !now.isBefore(entity.getStartsAt()) && !now.isAfter(entity.getEndsAt());
        return new MaintenanceWindowResponse(
                entity.getId(),
                entity.getWindowName(),
                entity.getScopeType(),
                entity.getScopeRef(),
                entity.getStartsAt(),
                entity.getEndsAt(),
                entity.isEnabled(),
                entity.getDescription(),
                activeNow,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
