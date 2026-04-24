package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.GatewayUserRequest;
import com.prodigalgal.xaigateway.admin.api.GatewayUserResponse;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UserSubscriptionRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserAdminService {

    private final GatewayUserRepository gatewayUserRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;

    public UserAdminService(
            GatewayUserRepository gatewayUserRepository,
            UserSubscriptionRepository userSubscriptionRepository) {
        this.gatewayUserRepository = gatewayUserRepository;
        this.userSubscriptionRepository = userSubscriptionRepository;
    }

    @Transactional(readOnly = true)
    public List<GatewayUserResponse> list(String keyword, Boolean active) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim().toLowerCase(Locale.ROOT);
        List<GatewayUserEntity> entities = active == null
                ? gatewayUserRepository.findAllByOrderByCreatedAtDesc()
                : gatewayUserRepository.findAllByActiveOrderByCreatedAtDesc(active);
        return entities.stream()
                .filter(entity -> matchesKeyword(entity, normalizedKeyword))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GatewayUserResponse get(Long id) {
        return toResponse(getRequired(id));
    }

    public GatewayUserResponse create(GatewayUserRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (gatewayUserRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("用户邮箱已存在。");
        }

        GatewayUserEntity entity = new GatewayUserEntity();
        apply(entity, request, true);
        return toResponse(gatewayUserRepository.save(entity));
    }

    public GatewayUserResponse update(Long id, GatewayUserRequest request) {
        GatewayUserEntity entity = getRequired(id);
        String normalizedEmail = normalizeEmail(request.email());
        if (gatewayUserRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, id)) {
            throw new IllegalArgumentException("用户邮箱已存在。");
        }
        apply(entity, request, false);
        return toResponse(gatewayUserRepository.save(entity));
    }

    public void delete(Long id) {
        GatewayUserEntity entity = getRequired(id);
        long subscriptionCount = userSubscriptionRepository.countByUser_Id(id);
        if (subscriptionCount > 0) {
            throw new IllegalArgumentException("该用户仍有关联订阅，请先删除订阅关系。");
        }
        gatewayUserRepository.delete(entity);
    }

    private GatewayUserEntity getRequired(Long id) {
        Optional<GatewayUserEntity> entity = gatewayUserRepository.findById(id);
        if (entity.isEmpty()) {
            throw new IllegalArgumentException("未找到指定用户。");
        }
        return entity.get();
    }

    private boolean matchesKeyword(GatewayUserEntity entity, String keyword) {
        if (keyword == null) {
            return true;
        }
        return containsIgnoreCase(entity.getEmail(), keyword) || containsIgnoreCase(entity.getDisplayName(), keyword);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String normalizeEmail(String email) {
        String value = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            throw new IllegalArgumentException("用户邮箱不能为空。");
        }
        return value;
    }

    private void apply(GatewayUserEntity entity, GatewayUserRequest request, boolean isCreate) {
        entity.setEmail(normalizeEmail(request.email()));
        entity.setDisplayName(blankToNull(request.displayName()));
        entity.setNotes(blankToNull(request.notes()));
        if (request.active() != null) {
            entity.setActive(request.active());
        } else if (isCreate) {
            entity.setActive(true);
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private GatewayUserResponse toResponse(GatewayUserEntity entity) {
        return new GatewayUserResponse(
                entity.getId(),
                entity.getEmail(),
                entity.getDisplayName(),
                entity.isActive(),
                userSubscriptionRepository.countByUser_Id(entity.getId()),
                entity.getLastLoginAt(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
