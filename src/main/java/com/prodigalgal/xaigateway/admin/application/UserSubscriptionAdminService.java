package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.UserSubscriptionRequest;
import com.prodigalgal.xaigateway.admin.api.UserSubscriptionResponse;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SubscriptionPlanEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UserSubscriptionEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SubscriptionPlanRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UserSubscriptionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserSubscriptionAdminService {

    private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "PAUSED", "EXPIRED", "CANCELED");

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final GatewayUserRepository gatewayUserRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    public UserSubscriptionAdminService(
            UserSubscriptionRepository userSubscriptionRepository,
            GatewayUserRepository gatewayUserRepository,
            SubscriptionPlanRepository subscriptionPlanRepository) {
        this.userSubscriptionRepository = userSubscriptionRepository;
        this.gatewayUserRepository = gatewayUserRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
    }

    @Transactional(readOnly = true)
    public List<UserSubscriptionResponse> list(String status, Long userId, Long planId) {
        String normalizedStatus = status == null || status.isBlank()
                ? null
                : status.trim().toUpperCase(Locale.ROOT);
        return userSubscriptionRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(entity -> normalizedStatus == null || normalizedStatus.equals(entity.getStatus()))
                .filter(entity -> userId == null || userId.equals(entity.getUser().getId()))
                .filter(entity -> planId == null || planId.equals(entity.getPlan().getId()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserSubscriptionResponse get(Long id) {
        return toResponse(getRequired(id));
    }

    public UserSubscriptionResponse create(UserSubscriptionRequest request) {
        GatewayUserEntity user = getRequiredUser(request.userId());
        SubscriptionPlanEntity plan = getRequiredPlan(request.planId());
        UserSubscriptionEntity entity = new UserSubscriptionEntity();
        entity.setUser(user);
        entity.setPlan(plan);
        apply(entity, request, plan, true);
        return toResponse(userSubscriptionRepository.save(entity));
    }

    public UserSubscriptionResponse update(Long id, UserSubscriptionRequest request) {
        UserSubscriptionEntity entity = getRequired(id);
        GatewayUserEntity user = request.userId().equals(entity.getUser().getId())
                ? entity.getUser()
                : getRequiredUser(request.userId());
        SubscriptionPlanEntity plan = request.planId().equals(entity.getPlan().getId())
                ? entity.getPlan()
                : getRequiredPlan(request.planId());
        entity.setUser(user);
        entity.setPlan(plan);
        apply(entity, request, plan, false);
        return toResponse(userSubscriptionRepository.save(entity));
    }

    public void delete(Long id) {
        userSubscriptionRepository.delete(getRequired(id));
    }

    private UserSubscriptionEntity getRequired(Long id) {
        Optional<UserSubscriptionEntity> entity = userSubscriptionRepository.findById(id);
        if (entity.isEmpty()) {
            throw new IllegalArgumentException("未找到指定订阅。");
        }
        return entity.get();
    }

    private GatewayUserEntity getRequiredUser(Long userId) {
        return gatewayUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定用户。"));
    }

    private SubscriptionPlanEntity getRequiredPlan(Long planId) {
        return subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定套餐。"));
    }

    private void apply(UserSubscriptionEntity entity, UserSubscriptionRequest request, SubscriptionPlanEntity plan, boolean isCreate) {
        String status = normalizeStatus(request.status(), isCreate ? "ACTIVE" : entity.getStatus());
        Instant startsAt = request.startsAt() != null
                ? request.startsAt()
                : (isCreate ? Instant.now() : entity.getStartsAt());
        Instant expiresAt = request.expiresAt() != null
                ? request.expiresAt()
                : (isCreate ? startsAt.plus(plan.getDefaultDurationDays(), ChronoUnit.DAYS) : entity.getExpiresAt());
        if (expiresAt != null && expiresAt.isBefore(startsAt)) {
            throw new IllegalArgumentException("失效时间不能早于生效时间。");
        }
        entity.setStatus(status);
        entity.setStartsAt(startsAt);
        entity.setExpiresAt(expiresAt);
        if (request.autoRenew() != null) {
            entity.setAutoRenew(request.autoRenew());
        } else if (isCreate) {
            entity.setAutoRenew(false);
        }
        entity.setNotes(blankToNull(request.notes()));
    }

    private String normalizeStatus(String status, String defaultStatus) {
        String normalized = status == null || status.isBlank()
                ? defaultStatus
                : status.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("订阅状态不合法。");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private UserSubscriptionResponse toResponse(UserSubscriptionEntity entity) {
        return new UserSubscriptionResponse(
                entity.getId(),
                entity.getUser().getId(),
                entity.getUser().getEmail(),
                entity.getPlan().getId(),
                entity.getPlan().getPlanName(),
                entity.getStatus(),
                entity.getStartsAt(),
                entity.getExpiresAt(),
                entity.isAutoRenew(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
