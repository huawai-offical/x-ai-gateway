package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.RunbookLinkRequest;
import com.prodigalgal.xaigateway.admin.api.RunbookLinkResponse;
import com.prodigalgal.xaigateway.infra.persistence.entity.RunbookLinkEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.RunbookLinkRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RunbookLinkService {

    private final RunbookLinkRepository runbookLinkRepository;

    public RunbookLinkService(RunbookLinkRepository runbookLinkRepository) {
        this.runbookLinkRepository = runbookLinkRepository;
    }

    @Transactional(readOnly = true)
    public List<RunbookLinkResponse> list() {
        return runbookLinkRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    public RunbookLinkResponse save(Long id, RunbookLinkRequest request) {
        RunbookLinkEntity entity = id == null
                ? new RunbookLinkEntity()
                : runbookLinkRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("未找到 runbook link。"));
        entity.setLinkName(requireText(request.linkName(), "linkName"));
        entity.setEventType(normalizeUpper(request.eventType()));
        entity.setEntityType(normalizeUpper(request.entityType()));
        entity.setLinkUrl(requireText(request.linkUrl(), "linkUrl"));
        entity.setDescription(blankToNull(request.description()));
        entity.setEnabled(request.enabled() == null || request.enabled());
        return toResponse(runbookLinkRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public String resolveUrl(String eventType, String entityType) {
        String normalizedEventType = normalizeUpper(eventType);
        String normalizedEntityType = normalizeUpper(entityType);
        return runbookLinkRepository.findAllByEnabledTrueOrderByCreatedAtDesc().stream()
                .map(link -> new WeightedLink(link, weight(link, normalizedEventType, normalizedEntityType)))
                .filter(item -> item.weight > 0)
                .max(Comparator.comparingInt(WeightedLink::weight))
                .map(item -> item.entity.getLinkUrl())
                .orElse(null);
    }

    private int weight(RunbookLinkEntity entity, String eventType, String entityType) {
        String linkEventType = normalizeUpper(entity.getEventType());
        String linkEntityType = normalizeUpper(entity.getEntityType());
        if (linkEventType != null && !linkEventType.equals(eventType)) {
            return 0;
        }
        if (linkEntityType != null && !linkEntityType.equals(entityType)) {
            return 0;
        }
        int score = 1;
        if (linkEventType != null) {
            score += 2;
        }
        if (linkEntityType != null) {
            score += 1;
        }
        return score;
    }

    private RunbookLinkResponse toResponse(RunbookLinkEntity entity) {
        return new RunbookLinkResponse(
                entity.getId(),
                entity.getLinkName(),
                entity.getEventType(),
                entity.getEntityType(),
                entity.getLinkUrl(),
                entity.getDescription(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String requireText(String value, String fieldName) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " 不能为空。");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeUpper(String value) {
        return Optional.ofNullable(blankToNull(value))
                .map(item -> item.toUpperCase(Locale.ROOT))
                .orElse(null);
    }

    private record WeightedLink(RunbookLinkEntity entity, int weight) {
    }
}
