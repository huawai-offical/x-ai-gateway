package com.prodigalgal.xaigateway.gateway.core.auth;

import com.prodigalgal.xaigateway.infra.persistence.entity.AccessGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyAccessGroupGrantEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.DistributedKeyEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.PlanAccessGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SubscriptionPlanEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UserAccessGroupGrantEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UserSubscriptionEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccessGroupGrantRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.PlanAccessGroupRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UserAccessGroupGrantRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UserSubscriptionRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccessGroupEntitlementServiceTests {

    private final PlanAccessGroupRepository planAccessGroupRepository = Mockito.mock(PlanAccessGroupRepository.class);
    private final DistributedKeyAccessGroupGrantRepository keyGrantRepository = Mockito.mock(DistributedKeyAccessGroupGrantRepository.class);
    private final UserSubscriptionRepository userSubscriptionRepository = Mockito.mock(UserSubscriptionRepository.class);
    private final DistributedKeyRepository distributedKeyRepository = Mockito.mock(DistributedKeyRepository.class);
    private final UserAccessGroupGrantRepository userAccessGroupGrantRepository = Mockito.mock(UserAccessGroupGrantRepository.class);
    private final AccessGroupEntitlementService service = new AccessGroupEntitlementService(
            planAccessGroupRepository,
            keyGrantRepository,
            userSubscriptionRepository,
            distributedKeyRepository,
            userAccessGroupGrantRepository
    );

    @Test
    void shouldInheritPlanAccessGroupForOwnedDistributedKey() {
        GatewayUserEntity user = user(1L);
        DistributedKeyEntity key = key(10L, user);
        SubscriptionPlanEntity plan = plan(3L);
        AccessGroupEntity group = group(5L, "starter-access", List.of("openai.native"), List.of("gpt-5-mini"), 60);
        PlanAccessGroupEntity binding = planBinding(plan, group);
        UserSubscriptionEntity subscription = subscription(user, plan);

        Mockito.when(keyGrantRepository.findAllByDistributedKey_IdAndActiveTrueOrderByPriorityAscCreatedAtAsc(10L))
                .thenReturn(List.of());
        Mockito.when(userSubscriptionRepository.findAllByUser_IdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(subscription));
        Mockito.when(planAccessGroupRepository.findAllByPlan_IdInAndActiveTrueOrderByPriorityAscCreatedAtAsc(List.of(3L)))
                .thenReturn(List.of(binding));
        Mockito.when(userAccessGroupGrantRepository.findAllByUser_IdAndStatusOrderByCreatedAtDesc(1L, "ACTIVE"))
                .thenReturn(List.of());

        ResolvedAccessPolicy policy = service.resolveForDistributedKey(key);

        assertEquals(List.of("starter-access"), policy.sourceAccessGroups());
        assertEquals(List.of("openai.native"), policy.allowedProtocolSuites());
        assertEquals(List.of("gpt-5-mini"), policy.allowedModels());
        assertEquals(60, policy.rpmLimit());
    }

    @Test
    void shouldUseKeyOverrideGrantInsteadOfPlanInheritance() {
        GatewayUserEntity user = user(1L);
        DistributedKeyEntity key = key(10L, user);
        SubscriptionPlanEntity plan = plan(3L);
        AccessGroupEntity inherited = group(5L, "starter-access", List.of("openai.native"), List.of("gpt-5-mini"), 60);
        AccessGroupEntity override = group(6L, "key-override", List.of("anthropic.native"), List.of("claude-sonnet-4"), 30);
        DistributedKeyAccessGroupGrantEntity grant = keyGrant(key, override, "OVERRIDE");

        Mockito.when(keyGrantRepository.findAllByDistributedKey_IdAndActiveTrueOrderByPriorityAscCreatedAtAsc(10L))
                .thenReturn(List.of(grant));
        Mockito.when(userSubscriptionRepository.findAllByUser_IdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(subscription(user, plan)));
        Mockito.when(planAccessGroupRepository.findAllByPlan_IdInAndActiveTrueOrderByPriorityAscCreatedAtAsc(List.of(3L)))
                .thenReturn(List.of(planBinding(plan, inherited)));

        ResolvedAccessPolicy policy = service.resolveForDistributedKey(key);

        assertEquals(List.of("key-override"), policy.sourceAccessGroups());
        assertEquals(List.of("anthropic.native"), policy.allowedProtocolSuites());
        assertEquals(List.of("claude-sonnet-4"), policy.allowedModels());
        assertEquals(30, policy.rpmLimit());
    }

    @Test
    void shouldIncludeUserAccessGroupGrantInDistributedKeyPolicy() {
        GatewayUserEntity user = user(1L);
        DistributedKeyEntity key = key(10L, user);
        AccessGroupEntity granted = group(7L, "gift-access", List.of("deepseek.openai_compatible"), List.of("deepseek-chat"), 40);
        UserAccessGroupGrantEntity grant = userGrant(user, granted);

        Mockito.when(keyGrantRepository.findAllByDistributedKey_IdAndActiveTrueOrderByPriorityAscCreatedAtAsc(10L))
                .thenReturn(List.of());
        Mockito.when(userSubscriptionRepository.findAllByUser_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        Mockito.when(userAccessGroupGrantRepository.findAllByUser_IdAndStatusOrderByCreatedAtDesc(1L, "ACTIVE"))
                .thenReturn(List.of(grant));

        ResolvedAccessPolicy policy = service.resolveForDistributedKey(key);

        assertEquals(List.of("gift-access"), policy.sourceAccessGroups());
        assertEquals(List.of("deepseek.openai_compatible"), policy.allowedProtocolSuites());
        assertEquals(List.of("deepseek-chat"), policy.allowedModels());
        assertEquals(40, policy.rpmLimit());
    }

    private GatewayUserEntity user(Long id) {
        GatewayUserEntity user = new GatewayUserEntity();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail("alpha@example.com");
        user.setActive(true);
        return user;
    }

    private DistributedKeyEntity key(Long id, GatewayUserEntity user) {
        DistributedKeyEntity key = new DistributedKeyEntity();
        ReflectionTestUtils.setField(key, "id", id);
        key.setKeyName("portal-key");
        key.setOwnerUser(user);
        key.setAllowedProtocolSuites(List.of());
        key.setAllowedModels(List.of());
        key.setAllowedProviderTypes(List.of());
        key.setAllowedClientFamilies(List.of());
        return key;
    }

    private SubscriptionPlanEntity plan(Long id) {
        SubscriptionPlanEntity plan = new SubscriptionPlanEntity();
        ReflectionTestUtils.setField(plan, "id", id);
        plan.setPlanName("starter");
        return plan;
    }

    private AccessGroupEntity group(Long id, String name, List<String> protocols, List<String> models, Integer rpm) {
        AccessGroupEntity group = new AccessGroupEntity();
        ReflectionTestUtils.setField(group, "id", id);
        group.setGroupName(name);
        group.setActive(true);
        group.setPriority(100);
        group.setAllowedProtocolSuites(protocols);
        group.setAllowedModels(models);
        group.setAllowedProviderTypes(List.of());
        group.setAllowedClientFamilies(List.of());
        group.setRpmLimit(rpm);
        return group;
    }

    private PlanAccessGroupEntity planBinding(SubscriptionPlanEntity plan, AccessGroupEntity group) {
        PlanAccessGroupEntity binding = new PlanAccessGroupEntity();
        binding.setPlan(plan);
        binding.setAccessGroup(group);
        binding.setActive(true);
        binding.setPriority(100);
        return binding;
    }

    private UserSubscriptionEntity subscription(GatewayUserEntity user, SubscriptionPlanEntity plan) {
        UserSubscriptionEntity subscription = new UserSubscriptionEntity();
        subscription.setUser(user);
        subscription.setPlan(plan);
        subscription.setStatus("ACTIVE");
        subscription.setStartsAt(Instant.parse("2026-04-24T00:00:00Z"));
        subscription.setExpiresAt(Instant.parse("2026-06-24T00:00:00Z"));
        return subscription;
    }

    private DistributedKeyAccessGroupGrantEntity keyGrant(DistributedKeyEntity key, AccessGroupEntity group, String mode) {
        DistributedKeyAccessGroupGrantEntity grant = new DistributedKeyAccessGroupGrantEntity();
        grant.setDistributedKey(key);
        grant.setAccessGroup(group);
        grant.setGrantMode(mode);
        grant.setActive(true);
        grant.setPriority(100);
        return grant;
    }

    private UserAccessGroupGrantEntity userGrant(GatewayUserEntity user, AccessGroupEntity group) {
        UserAccessGroupGrantEntity grant = new UserAccessGroupGrantEntity();
        grant.setUser(user);
        grant.setAccessGroup(group);
        grant.setStatus("ACTIVE");
        grant.setStartsAt(Instant.parse("2026-04-24T00:00:00Z"));
        grant.setExpiresAt(Instant.parse("2026-06-24T00:00:00Z"));
        return grant;
    }
}
