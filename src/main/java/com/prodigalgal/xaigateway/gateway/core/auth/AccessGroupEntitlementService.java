package com.prodigalgal.xaigateway.gateway.core.auth;

import com.prodigalgal.xaigateway.gateway.core.shared.ModelIdNormalizer;
import com.prodigalgal.xaigateway.infra.persistence.entity.AccessGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyAccessGroupGrantEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.PlanAccessGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UserAccessGroupGrantEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UserSubscriptionEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccessGroupGrantRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.PlanAccessGroupRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UserAccessGroupGrantRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UserSubscriptionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccessGroupEntitlementService {

    private final PlanAccessGroupRepository planAccessGroupRepository;
    private final DistributedKeyAccessGroupGrantRepository keyGrantRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final DistributedKeyRepository distributedKeyRepository;
    private final UserAccessGroupGrantRepository userAccessGroupGrantRepository;

    public AccessGroupEntitlementService(
            PlanAccessGroupRepository planAccessGroupRepository,
            DistributedKeyAccessGroupGrantRepository keyGrantRepository,
            UserSubscriptionRepository userSubscriptionRepository,
            DistributedKeyRepository distributedKeyRepository) {
        this(planAccessGroupRepository, keyGrantRepository, userSubscriptionRepository, distributedKeyRepository, null);
    }

    @Autowired
    public AccessGroupEntitlementService(
            PlanAccessGroupRepository planAccessGroupRepository,
            DistributedKeyAccessGroupGrantRepository keyGrantRepository,
            UserSubscriptionRepository userSubscriptionRepository,
            DistributedKeyRepository distributedKeyRepository,
            UserAccessGroupGrantRepository userAccessGroupGrantRepository) {
        this.planAccessGroupRepository = planAccessGroupRepository;
        this.keyGrantRepository = keyGrantRepository;
        this.userSubscriptionRepository = userSubscriptionRepository;
        this.distributedKeyRepository = distributedKeyRepository;
        this.userAccessGroupGrantRepository = userAccessGroupGrantRepository;
    }

    public ResolvedAccessPolicy resolveForDistributedKey(DistributedKeyEntity key) {
        if (key == null || key.getId() == null) {
            return ResolvedAccessPolicy.empty();
        }
        List<DistributedKeyAccessGroupGrantEntity> directGrants = keyGrantRepository
                .findAllByDistributedKey_IdAndActiveTrueOrderByPriorityAscCreatedAtAsc(key.getId());
        boolean overrideInherited = directGrants.stream()
                .anyMatch(grant -> "OVERRIDE".equalsIgnoreCase(grant.getGrantMode()));

        List<AccessGroupEntity> groups = new ArrayList<>();
        if (!overrideInherited && key.getOwnerUser() != null) {
            groups.addAll(activeGroupsForUserSubscriptions(key.getOwnerUser().getId()));
        }
        groups.addAll(directGrants.stream()
                .map(DistributedKeyAccessGroupGrantEntity::getAccessGroup)
                .toList());

        ResolvedAccessPolicy inherited = resolveGroups(groups);
        return new ResolvedAccessPolicy(
                inherited.sourceAccessGroups(),
                chooseList(key.getAllowedProtocolSuites(), inherited.allowedProtocolSuites()),
                chooseList(key.getAllowedModels(), inherited.allowedModels()),
                chooseList(key.getAllowedProviderTypes(), inherited.allowedProviderTypes()),
                chooseList(key.getAllowedClientFamilies(), inherited.allowedClientFamilies()),
                chooseLimit(key.getRpmLimit(), inherited.rpmLimit()),
                chooseLimit(key.getTpmLimit(), inherited.tpmLimit()),
                chooseLimit(key.getConcurrencyLimit(), inherited.concurrencyLimit()),
                inherited.dailyTokenLimit()
        );
    }

    public Set<Long> activeAccessGroupIdsForUser(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        activeGroupsForUserSubscriptions(userId).stream()
                .map(AccessGroupEntity::getId)
                .forEach(ids::add);
        activeGroupsForUserGrants(userId).stream()
                .map(AccessGroupEntity::getId)
                .forEach(ids::add);
        List<Long> keyIds = distributedKeyRepository.findAllByOwnerUser_IdOrderByCreatedAtDesc(userId).stream()
                .filter(DistributedKeyEntity::isActive)
                .map(DistributedKeyEntity::getId)
                .toList();
        if (!keyIds.isEmpty()) {
            keyGrantRepository.findAllByDistributedKey_IdInAndActiveTrueOrderByPriorityAscCreatedAtAsc(keyIds).stream()
                    .map(DistributedKeyAccessGroupGrantEntity::getAccessGroup)
                    .filter(AccessGroupEntity::isActive)
                    .map(AccessGroupEntity::getId)
                    .forEach(ids::add);
        }
        return Set.copyOf(ids);
    }

    private List<AccessGroupEntity> activeGroupsForUserSubscriptions(Long userId) {
        Instant now = Instant.now();
        List<Long> activePlanIds = userSubscriptionRepository.findAllByUser_IdOrderByCreatedAtDesc(userId).stream()
                .filter(entity -> isActiveSubscription(entity, now))
                .map(entity -> entity.getPlan().getId())
                .toList();
        if (activePlanIds.isEmpty()) {
            return activeGroupsForUserGrants(userId);
        }
        List<AccessGroupEntity> groups = new ArrayList<>(planAccessGroupRepository.findAllByPlan_IdInAndActiveTrueOrderByPriorityAscCreatedAtAsc(activePlanIds).stream()
                .map(PlanAccessGroupEntity::getAccessGroup)
                .toList());
        groups.addAll(activeGroupsForUserGrants(userId));
        return groups;
    }

    private List<AccessGroupEntity> activeGroupsForUserGrants(Long userId) {
        if (userAccessGroupGrantRepository == null) {
            return List.of();
        }
        Instant now = Instant.now();
        return userAccessGroupGrantRepository.findAllByUser_IdAndStatusOrderByCreatedAtDesc(userId, "ACTIVE").stream()
                .filter(entity -> isActiveUserGrant(entity, now))
                .map(UserAccessGroupGrantEntity::getAccessGroup)
                .toList();
    }

    private boolean isActiveUserGrant(UserAccessGroupGrantEntity entity, Instant now) {
        return entity.getAccessGroup() != null
                && entity.getAccessGroup().isActive()
                && (entity.getStartsAt() == null || !entity.getStartsAt().isAfter(now))
                && (entity.getExpiresAt() == null || entity.getExpiresAt().isAfter(now));
    }

    private boolean isActiveSubscription(UserSubscriptionEntity entity, Instant now) {
        return "ACTIVE".equals(entity.getStatus())
                && (entity.getStartsAt() == null || !entity.getStartsAt().isAfter(now))
                && (entity.getExpiresAt() == null || entity.getExpiresAt().isAfter(now));
    }

    private ResolvedAccessPolicy resolveGroups(List<AccessGroupEntity> groups) {
        List<AccessGroupEntity> activeGroups = groups.stream()
                .filter(AccessGroupEntity::isActive)
                .sorted(Comparator.comparingInt(AccessGroupEntity::getPriority).thenComparing(AccessGroupEntity::getGroupName))
                .toList();
        return new ResolvedAccessPolicy(
                activeGroups.stream().map(AccessGroupEntity::getGroupName).distinct().toList(),
                mergeLists(activeGroups.stream().map(AccessGroupEntity::getAllowedProtocolSuites).toList()),
                mergeModels(activeGroups.stream().map(AccessGroupEntity::getAllowedModels).toList()),
                mergeLists(activeGroups.stream().map(AccessGroupEntity::getAllowedProviderTypes).toList()),
                mergeLists(activeGroups.stream().map(AccessGroupEntity::getAllowedClientFamilies).toList()),
                minInteger(activeGroups.stream().map(AccessGroupEntity::getRpmLimit).toList()),
                minInteger(activeGroups.stream().map(AccessGroupEntity::getTpmLimit).toList()),
                minInteger(activeGroups.stream().map(AccessGroupEntity::getConcurrencyLimit).toList()),
                minLong(activeGroups.stream().map(AccessGroupEntity::getDailyTokenLimit).toList())
        );
    }

    private List<String> chooseList(List<String> direct, List<String> inherited) {
        return direct == null || direct.isEmpty() ? inherited : List.copyOf(direct);
    }

    private Integer chooseLimit(Integer direct, Integer inherited) {
        return direct == null || direct <= 0 ? inherited : direct;
    }

    private Long chooseLimit(Long direct, Long inherited) {
        return direct == null || direct <= 0 ? inherited : direct;
    }

    private List<String> mergeLists(List<List<String>> values) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        for (List<String> list : values) {
            if (list == null) {
                continue;
            }
            for (String value : list) {
                if (value != null && !value.isBlank()) {
                    merged.add(value.trim());
                }
            }
        }
        return List.copyOf(merged);
    }

    private List<String> mergeModels(List<List<String>> values) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        for (List<String> list : values) {
            if (list == null) {
                continue;
            }
            for (String value : list) {
                String normalized = ModelIdNormalizer.normalize(value);
                if (normalized != null && !normalized.isBlank()) {
                    merged.add(normalized);
                }
            }
        }
        return List.copyOf(merged);
    }

    private Integer minInteger(List<Integer> values) {
        return values.stream()
                .filter(value -> value != null && value > 0)
                .min(Integer::compareTo)
                .orElse(null);
    }

    private Long minLong(List<Long> values) {
        return values.stream()
                .filter(value -> value != null && value > 0)
                .min(Long::compareTo)
                .orElse(null);
    }
}
