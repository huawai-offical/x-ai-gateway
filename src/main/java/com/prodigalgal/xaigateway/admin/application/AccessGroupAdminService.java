package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AccessGroupKeyGrantRequest;
import com.prodigalgal.xaigateway.admin.api.AccessGroupKeyGrantResponse;
import com.prodigalgal.xaigateway.admin.api.AccessGroupPlanBindingRequest;
import com.prodigalgal.xaigateway.admin.api.AccessGroupPlanBindingResponse;
import com.prodigalgal.xaigateway.admin.api.AccessGroupRequest;
import com.prodigalgal.xaigateway.admin.api.AccessGroupResolvedPolicyResponse;
import com.prodigalgal.xaigateway.admin.api.AccessGroupResponse;
import com.prodigalgal.xaigateway.gateway.core.auth.AccessGroupEntitlementService;
import com.prodigalgal.xaigateway.gateway.core.auth.ResolvedAccessPolicy;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelIdNormalizer;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.AccessGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyAccessGroupGrantEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.PlanAccessGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SubscriptionPlanEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.AccessGroupRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccessGroupGrantRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.PlanAccessGroupRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SubscriptionPlanRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccessGroupAdminService {

    private static final Set<String> KEY_GRANT_MODES = Set.of("INHERIT", "OVERRIDE");

    private final AccessGroupRepository accessGroupRepository;
    private final PlanAccessGroupRepository planAccessGroupRepository;
    private final DistributedKeyAccessGroupGrantRepository keyGrantRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final DistributedKeyRepository distributedKeyRepository;
    private final AccessGroupEntitlementService accessGroupEntitlementService;

    public AccessGroupAdminService(
            AccessGroupRepository accessGroupRepository,
            PlanAccessGroupRepository planAccessGroupRepository,
            DistributedKeyAccessGroupGrantRepository keyGrantRepository,
            SubscriptionPlanRepository subscriptionPlanRepository,
            DistributedKeyRepository distributedKeyRepository,
            AccessGroupEntitlementService accessGroupEntitlementService) {
        this.accessGroupRepository = accessGroupRepository;
        this.planAccessGroupRepository = planAccessGroupRepository;
        this.keyGrantRepository = keyGrantRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.distributedKeyRepository = distributedKeyRepository;
        this.accessGroupEntitlementService = accessGroupEntitlementService;
    }

    @Transactional(readOnly = true)
    public List<AccessGroupResponse> list(String keyword, Boolean active) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim().toLowerCase(Locale.ROOT);
        List<AccessGroupEntity> entities = active == null
                ? accessGroupRepository.findAllByOrderByCreatedAtDesc()
                : accessGroupRepository.findAllByActiveOrderByCreatedAtDesc(active);
        return entities.stream()
                .filter(entity -> matchesKeyword(entity, normalizedKeyword))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccessGroupResponse get(Long id) {
        return toResponse(getRequired(id));
    }

    public AccessGroupResponse create(AccessGroupRequest request) {
        String name = normalizeName(request.groupName());
        if (accessGroupRepository.existsByGroupNameIgnoreCase(name)) {
            throw new IllegalArgumentException("访问组名称已存在。");
        }
        AccessGroupEntity entity = new AccessGroupEntity();
        apply(entity, request, true);
        return toResponse(accessGroupRepository.save(entity));
    }

    public AccessGroupResponse update(Long id, AccessGroupRequest request) {
        AccessGroupEntity entity = getRequired(id);
        String name = normalizeName(request.groupName());
        if (accessGroupRepository.existsByGroupNameIgnoreCaseAndIdNot(name, id)) {
            throw new IllegalArgumentException("访问组名称已存在。");
        }
        apply(entity, request, false);
        return toResponse(accessGroupRepository.save(entity));
    }

    public void delete(Long id) {
        AccessGroupEntity entity = getRequired(id);
        planAccessGroupRepository.deleteAllByAccessGroup_Id(id);
        keyGrantRepository.deleteAllByAccessGroup_Id(id);
        accessGroupRepository.delete(entity);
    }

    public AccessGroupResponse bindPlan(Long accessGroupId, AccessGroupPlanBindingRequest request) {
        AccessGroupEntity accessGroup = getRequired(accessGroupId);
        SubscriptionPlanEntity plan = subscriptionPlanRepository.findById(request.planId())
                .orElseThrow(() -> new IllegalArgumentException("未找到指定套餐。"));
        PlanAccessGroupEntity binding = planAccessGroupRepository
                .findByPlan_IdAndAccessGroup_Id(plan.getId(), accessGroupId)
                .orElseGet(PlanAccessGroupEntity::new);
        binding.setPlan(plan);
        binding.setAccessGroup(accessGroup);
        binding.setActive(request.active() == null || request.active());
        binding.setPriority(resolvePriority(request.priority(), binding.getPriority()));
        planAccessGroupRepository.save(binding);
        return toResponse(accessGroup);
    }

    public AccessGroupResponse removePlanBinding(Long accessGroupId, Long planId) {
        AccessGroupEntity accessGroup = getRequired(accessGroupId);
        planAccessGroupRepository.deleteByPlan_IdAndAccessGroup_Id(planId, accessGroupId);
        return toResponse(accessGroup);
    }

    public AccessGroupResponse grantDistributedKey(Long accessGroupId, AccessGroupKeyGrantRequest request) {
        AccessGroupEntity accessGroup = getRequired(accessGroupId);
        DistributedKeyEntity distributedKey = distributedKeyRepository.findById(request.distributedKeyId())
                .orElseThrow(() -> new IllegalArgumentException("未找到指定分发 Key。"));
        DistributedKeyAccessGroupGrantEntity grant = keyGrantRepository
                .findByDistributedKey_IdAndAccessGroup_Id(distributedKey.getId(), accessGroupId)
                .orElseGet(DistributedKeyAccessGroupGrantEntity::new);
        grant.setDistributedKey(distributedKey);
        grant.setAccessGroup(accessGroup);
        grant.setGrantMode(normalizeGrantMode(request.grantMode()));
        grant.setActive(request.active() == null || request.active());
        grant.setPriority(resolvePriority(request.priority(), grant.getPriority()));
        grant.setReason(blankToNull(request.reason()));
        keyGrantRepository.save(grant);
        return toResponse(accessGroup);
    }

    public AccessGroupResponse removeDistributedKeyGrant(Long accessGroupId, Long distributedKeyId) {
        AccessGroupEntity accessGroup = getRequired(accessGroupId);
        keyGrantRepository.deleteByDistributedKey_IdAndAccessGroup_Id(distributedKeyId, accessGroupId);
        return toResponse(accessGroup);
    }

    @Transactional(readOnly = true)
    public AccessGroupResolvedPolicyResponse resolveDistributedKeyPolicy(Long distributedKeyId) {
        DistributedKeyEntity distributedKey = distributedKeyRepository.findById(distributedKeyId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定分发 Key。"));
        return toResolvedPolicyResponse(accessGroupEntitlementService.resolveForDistributedKey(distributedKey));
    }

    private void apply(AccessGroupEntity entity, AccessGroupRequest request, boolean isCreate) {
        entity.setGroupName(normalizeName(request.groupName()));
        entity.setDescription(blankToNull(request.description()));
        entity.setActive(request.active() == null ? isCreate || entity.isActive() : request.active());
        entity.setPriority(resolvePriority(request.priority(), entity.getPriority()));
        entity.setAllowedProtocols(normalizeProtocols(request.allowedProtocols()));
        entity.setAllowedModels(normalizeModels(request.allowedModels()));
        entity.setAllowedProviderTypes(normalizeProviderTypes(request.allowedProviderTypes()));
        entity.setAllowedClientFamilies(normalizeClientFamilies(request.allowedClientFamilies()));
        entity.setRpmLimit(resolveNullablePositive(request.rpmLimit()));
        entity.setTpmLimit(resolveNullablePositive(request.tpmLimit()));
        entity.setConcurrencyLimit(resolveNullablePositive(request.concurrencyLimit()));
        entity.setDailyTokenLimit(resolveNullablePositive(request.dailyTokenLimit()));
    }

    private AccessGroupEntity getRequired(Long id) {
        Optional<AccessGroupEntity> entity = accessGroupRepository.findById(id);
        if (entity.isEmpty()) {
            throw new IllegalArgumentException("未找到指定访问组。");
        }
        return entity.get();
    }

    private boolean matchesKeyword(AccessGroupEntity entity, String keyword) {
        if (keyword == null) {
            return true;
        }
        return contains(entity.getGroupName(), keyword) || contains(entity.getDescription(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String normalizeName(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("访问组名称不能为空。");
        }
        return normalized;
    }

    private String normalizeGrantMode(String value) {
        String normalized = value == null || value.isBlank()
                ? "INHERIT"
                : value.trim().toUpperCase(Locale.ROOT);
        if (!KEY_GRANT_MODES.contains(normalized)) {
            throw new IllegalArgumentException("分发 Key 授权模式不合法。");
        }
        return normalized;
    }

    private int resolvePriority(Integer requested, int current) {
        int value = requested == null ? current : requested;
        return Math.max(0, Math.min(10_000, value));
    }

    private Integer resolveNullablePositive(Integer value) {
        return value == null || value <= 0 ? null : value;
    }

    private Long resolveNullablePositive(Long value) {
        return value == null || value <= 0 ? null : value;
    }

    private List<String> normalizeProtocols(List<String> protocols) {
        if (protocols == null) {
            return List.of();
        }
        return protocols.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private List<String> normalizeModels(List<String> models) {
        if (models == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String model : models) {
            String value = ModelIdNormalizer.normalize(model);
            if (value != null && !value.isBlank() && !normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    private List<String> normalizeProviderTypes(List<String> providerTypes) {
        if (providerTypes == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String providerType : providerTypes) {
            if (providerType == null || providerType.isBlank()) {
                continue;
            }
            String value = normalizeProviderType(providerType);
            if (!normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    private String normalizeProviderType(String providerType) {
        String normalized = providerType.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "OPENAI" -> ProviderType.OPENAI_DIRECT.name();
            case "OPENAI_COMPAT" -> ProviderType.OPENAI_COMPATIBLE.name();
            case "ANTHROPIC", "CLAUDE" -> ProviderType.ANTHROPIC_DIRECT.name();
            case "GEMINI", "GOOGLE" -> ProviderType.GEMINI_DIRECT.name();
            case "OLLAMA" -> ProviderType.OLLAMA_DIRECT.name();
            default -> ProviderType.valueOf(normalized).name();
        };
    }

    private List<String> normalizeClientFamilies(List<String> clientFamilies) {
        if (clientFamilies == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String clientFamily : clientFamilies) {
            if (clientFamily == null || clientFamily.isBlank()) {
                continue;
            }
            String value = GatewayClientFamily.from(clientFamily).name();
            if (!normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private AccessGroupResponse toResponse(AccessGroupEntity entity) {
        List<AccessGroupPlanBindingResponse> planBindings = planAccessGroupRepository
                .findAllByAccessGroup_IdOrderByPriorityAscCreatedAtAsc(entity.getId())
                .stream()
                .map(this::toPlanBindingResponse)
                .toList();
        List<AccessGroupKeyGrantResponse> keyGrants = keyGrantRepository
                .findAllByAccessGroup_IdOrderByPriorityAscCreatedAtAsc(entity.getId())
                .stream()
                .map(this::toKeyGrantResponse)
                .toList();
        return new AccessGroupResponse(
                entity.getId(),
                entity.getGroupName(),
                entity.getDescription(),
                entity.isActive(),
                entity.getPriority(),
                entity.getAllowedProtocols(),
                entity.getAllowedModels(),
                entity.getAllowedProviderTypes(),
                entity.getAllowedClientFamilies(),
                entity.getRpmLimit(),
                entity.getTpmLimit(),
                entity.getConcurrencyLimit(),
                entity.getDailyTokenLimit(),
                planBindings.size(),
                keyGrants.size(),
                planBindings,
                keyGrants,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private AccessGroupPlanBindingResponse toPlanBindingResponse(PlanAccessGroupEntity entity) {
        return new AccessGroupPlanBindingResponse(
                entity.getId(),
                entity.getPlan().getId(),
                entity.getPlan().getPlanName(),
                entity.isActive(),
                entity.getPriority(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private AccessGroupKeyGrantResponse toKeyGrantResponse(DistributedKeyAccessGroupGrantEntity entity) {
        return new AccessGroupKeyGrantResponse(
                entity.getId(),
                entity.getDistributedKey().getId(),
                entity.getDistributedKey().getKeyName(),
                entity.getDistributedKey().getKeyPrefix(),
                entity.getGrantMode(),
                entity.isActive(),
                entity.getPriority(),
                entity.getReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private AccessGroupResolvedPolicyResponse toResolvedPolicyResponse(ResolvedAccessPolicy policy) {
        return new AccessGroupResolvedPolicyResponse(
                policy.sourceAccessGroups(),
                policy.allowedProtocols(),
                policy.allowedModels(),
                policy.allowedProviderTypes(),
                policy.allowedClientFamilies(),
                policy.rpmLimit(),
                policy.tpmLimit(),
                policy.concurrencyLimit(),
                policy.dailyTokenLimit()
        );
    }
}
