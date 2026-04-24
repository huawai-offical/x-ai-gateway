package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.SubscriptionPlanRequest;
import com.prodigalgal.xaigateway.admin.api.SubscriptionPlanResponse;
import com.prodigalgal.xaigateway.infra.persistence.entity.SubscriptionPlanEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.SubscriptionPlanRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UserSubscriptionRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SubscriptionPlanAdminService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;

    public SubscriptionPlanAdminService(
            SubscriptionPlanRepository subscriptionPlanRepository,
            UserSubscriptionRepository userSubscriptionRepository) {
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.userSubscriptionRepository = userSubscriptionRepository;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> list(String keyword, Boolean active) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim().toLowerCase(Locale.ROOT);
        List<SubscriptionPlanEntity> entities = active == null
                ? subscriptionPlanRepository.findAllByOrderByCreatedAtDesc()
                : subscriptionPlanRepository.findAllByActiveOrderByCreatedAtDesc(active);
        return entities.stream()
                .filter(entity -> matchesKeyword(entity, normalizedKeyword))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionPlanResponse get(Long id) {
        return toResponse(getRequired(id));
    }

    public SubscriptionPlanResponse create(SubscriptionPlanRequest request) {
        String normalizedPlanName = normalizePlanName(request.planName());
        if (subscriptionPlanRepository.existsByPlanNameIgnoreCase(normalizedPlanName)) {
            throw new IllegalArgumentException("套餐名称已存在。");
        }

        SubscriptionPlanEntity entity = new SubscriptionPlanEntity();
        apply(entity, request, true);
        return toResponse(subscriptionPlanRepository.save(entity));
    }

    public SubscriptionPlanResponse update(Long id, SubscriptionPlanRequest request) {
        SubscriptionPlanEntity entity = getRequired(id);
        String normalizedPlanName = normalizePlanName(request.planName());
        if (subscriptionPlanRepository.existsByPlanNameIgnoreCaseAndIdNot(normalizedPlanName, id)) {
            throw new IllegalArgumentException("套餐名称已存在。");
        }
        apply(entity, request, false);
        return toResponse(subscriptionPlanRepository.save(entity));
    }

    public void delete(Long id) {
        SubscriptionPlanEntity entity = getRequired(id);
        long subscriptionCount = userSubscriptionRepository.countByPlan_Id(id);
        if (subscriptionCount > 0) {
            throw new IllegalArgumentException("该套餐仍有关联订阅，请先删除订阅关系。");
        }
        subscriptionPlanRepository.delete(entity);
    }

    private SubscriptionPlanEntity getRequired(Long id) {
        Optional<SubscriptionPlanEntity> entity = subscriptionPlanRepository.findById(id);
        if (entity.isEmpty()) {
            throw new IllegalArgumentException("未找到指定套餐。");
        }
        return entity.get();
    }

    private boolean matchesKeyword(SubscriptionPlanEntity entity, String keyword) {
        if (keyword == null) {
            return true;
        }
        return containsIgnoreCase(entity.getPlanName(), keyword) || containsIgnoreCase(entity.getDescription(), keyword);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String normalizePlanName(String planName) {
        String normalized = planName == null ? "" : planName.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("套餐名称不能为空。");
        }
        return normalized;
    }

    private void apply(SubscriptionPlanEntity entity, SubscriptionPlanRequest request, boolean isCreate) {
        entity.setPlanName(normalizePlanName(request.planName()));
        entity.setDescription(blankToNull(request.description()));
        if (request.active() != null) {
            entity.setActive(request.active());
        } else if (isCreate) {
            entity.setActive(true);
        }
        entity.setDefaultDurationDays(resolveInt(request.defaultDurationDays(), entity.getDefaultDurationDays(), 30, 1, 3650));
        entity.setMaxActiveKeys(resolveInt(request.maxActiveKeys(), entity.getMaxActiveKeys(), 3, 1, 10000));
        entity.setRpmLimit(resolveInt(request.rpmLimit(), entity.getRpmLimit(), 60, 1, 1_000_000));
        entity.setTpmLimit(resolveInt(request.tpmLimit(), entity.getTpmLimit(), 120000, 1, 1_000_000_000));
        entity.setConcurrencyLimit(resolveInt(request.concurrencyLimit(), entity.getConcurrencyLimit(), 2, 1, 10000));
        entity.setDailyTokenLimit(resolveLong(request.dailyTokenLimit(), entity.getDailyTokenLimit(), 1_000_000L, 1L, 10_000_000_000L));
    }

    private int resolveInt(Integer requestedValue, int currentValue, int defaultValue, int min, int max) {
        int value = requestedValue != null ? requestedValue : currentValue;
        if (value <= 0) {
            value = defaultValue;
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private long resolveLong(Long requestedValue, long currentValue, long defaultValue, long min, long max) {
        long value = requestedValue != null ? requestedValue : currentValue;
        if (value <= 0) {
            value = defaultValue;
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private SubscriptionPlanResponse toResponse(SubscriptionPlanEntity entity) {
        return new SubscriptionPlanResponse(
                entity.getId(),
                entity.getPlanName(),
                entity.getDescription(),
                entity.isActive(),
                entity.getDefaultDurationDays(),
                entity.getMaxActiveKeys(),
                entity.getRpmLimit(),
                entity.getTpmLimit(),
                entity.getConcurrencyLimit(),
                entity.getDailyTokenLimit(),
                userSubscriptionRepository.countByPlan_Id(entity.getId()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
