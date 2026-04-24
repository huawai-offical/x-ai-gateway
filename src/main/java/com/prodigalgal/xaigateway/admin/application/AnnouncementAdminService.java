package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AnnouncementRequest;
import com.prodigalgal.xaigateway.admin.api.AnnouncementResponse;
import com.prodigalgal.xaigateway.infra.persistence.entity.AnnouncementEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SubscriptionPlanEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.AnnouncementRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SubscriptionPlanRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AnnouncementAdminService {

    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");
    private static final Set<String> AUDIENCE_TYPES = Set.of("GLOBAL", "USER", "PLAN");

    private final AnnouncementRepository announcementRepository;
    private final GatewayUserRepository gatewayUserRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    public AnnouncementAdminService(
            AnnouncementRepository announcementRepository,
            GatewayUserRepository gatewayUserRepository,
            SubscriptionPlanRepository subscriptionPlanRepository) {
        this.announcementRepository = announcementRepository;
        this.gatewayUserRepository = gatewayUserRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
    }

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> list(String status) {
        String normalizedStatus = blankToNull(status) == null ? null : status.trim().toUpperCase(Locale.ROOT);
        return announcementRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(entity -> normalizedStatus == null || normalizedStatus.equals(entity.getStatus()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AnnouncementResponse get(Long id) {
        return toResponse(getRequired(id));
    }

    public AnnouncementResponse create(AnnouncementRequest request) {
        AnnouncementEntity entity = new AnnouncementEntity();
        apply(entity, request, true);
        return toResponse(announcementRepository.save(entity));
    }

    public AnnouncementResponse update(Long id, AnnouncementRequest request) {
        AnnouncementEntity entity = getRequired(id);
        apply(entity, request, false);
        return toResponse(announcementRepository.save(entity));
    }

    public void delete(Long id) {
        announcementRepository.delete(getRequired(id));
    }

    private AnnouncementEntity getRequired(Long id) {
        Optional<AnnouncementEntity> entity = announcementRepository.findById(id);
        if (entity.isEmpty()) {
            throw new IllegalArgumentException("未找到指定公告。");
        }
        return entity.get();
    }

    private void apply(AnnouncementEntity entity, AnnouncementRequest request, boolean isCreate) {
        entity.setTitle(requireText(request.title(), "公告标题不能为空。"));
        entity.setSummary(blankToNull(request.summary()));
        entity.setBody(blankToNull(request.body()));

        String status = normalizeOption(request.status(), isCreate ? "DRAFT" : entity.getStatus(), STATUSES, "公告状态不合法。");
        String audienceType = normalizeOption(request.audienceType(), "GLOBAL", AUDIENCE_TYPES, "公告受众类型不合法。");
        entity.setStatus(status);
        entity.setAudienceType(audienceType);
        entity.setAudienceUser(null);
        entity.setAudiencePlan(null);

        if ("USER".equals(audienceType)) {
            entity.setAudienceUser(getUser(request.audienceUserId()));
        } else if ("PLAN".equals(audienceType)) {
            entity.setAudiencePlan(getPlan(request.audiencePlanId()));
        }

        Instant publishedAt = request.publishedAt();
        if ("PUBLISHED".equals(status) && publishedAt == null) {
            publishedAt = Instant.now();
        }
        if (publishedAt != null && request.expiresAt() != null && request.expiresAt().isBefore(publishedAt)) {
            throw new IllegalArgumentException("公告过期时间不能早于发布时间。");
        }
        entity.setPublishedAt(publishedAt);
        entity.setExpiresAt(request.expiresAt());
    }

    private GatewayUserEntity getUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("USER 受众必须选择用户。");
        }
        return gatewayUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定受众用户。"));
    }

    private SubscriptionPlanEntity getPlan(Long planId) {
        if (planId == null) {
            throw new IllegalArgumentException("PLAN 受众必须选择套餐。");
        }
        return subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定受众套餐。"));
    }

    private String normalizeOption(String value, String defaultValue, Set<String> allowed, String errorMessage) {
        String normalized = value == null || value.isBlank()
                ? defaultValue
                : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    private String requireText(String value, String errorMessage) {
        String text = blankToNull(value);
        if (text == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        return text;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private AnnouncementResponse toResponse(AnnouncementEntity entity) {
        return new AnnouncementResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getSummary(),
                entity.getBody(),
                entity.getStatus(),
                entity.getAudienceType(),
                entity.getAudienceUser() == null ? null : entity.getAudienceUser().getId(),
                entity.getAudienceUser() == null ? null : entity.getAudienceUser().getEmail(),
                entity.getAudiencePlan() == null ? null : entity.getAudiencePlan().getId(),
                entity.getAudiencePlan() == null ? null : entity.getAudiencePlan().getPlanName(),
                entity.getPublishedAt(),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
