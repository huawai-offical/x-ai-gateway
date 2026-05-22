package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AccessGroupRequest;
import com.prodigalgal.xaigateway.gateway.core.auth.AccessGroupEntitlementService;
import com.prodigalgal.xaigateway.infra.persistence.entity.AccessGroupEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.AccessGroupRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyAccessGroupGrantRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.DistributedKeyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.PlanAccessGroupRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SubscriptionPlanRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccessGroupAdminServiceTests {

    private final AccessGroupRepository accessGroupRepository = Mockito.mock(AccessGroupRepository.class);
    private final PlanAccessGroupRepository planAccessGroupRepository = Mockito.mock(PlanAccessGroupRepository.class);
    private final DistributedKeyAccessGroupGrantRepository keyGrantRepository = Mockito.mock(DistributedKeyAccessGroupGrantRepository.class);
    private final SubscriptionPlanRepository subscriptionPlanRepository = Mockito.mock(SubscriptionPlanRepository.class);
    private final DistributedKeyRepository distributedKeyRepository = Mockito.mock(DistributedKeyRepository.class);
    private final AccessGroupEntitlementService accessGroupEntitlementService = Mockito.mock(AccessGroupEntitlementService.class);
    private final AccessGroupAdminService service = new AccessGroupAdminService(
            accessGroupRepository,
            planAccessGroupRepository,
            keyGrantRepository,
            subscriptionPlanRepository,
            distributedKeyRepository,
            accessGroupEntitlementService
    );

    @Test
    void shouldCreateAccessGroupWithNormalizedPolicy() {
        Mockito.when(accessGroupRepository.existsByGroupNameIgnoreCase("default")).thenReturn(false);
        Mockito.when(accessGroupRepository.save(Mockito.any())).thenAnswer(invocation -> {
            AccessGroupEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 9L);
            return entity;
        });
        Mockito.when(planAccessGroupRepository.findAllByAccessGroup_IdOrderByPriorityAscCreatedAtAsc(9L)).thenReturn(List.of());
        Mockito.when(keyGrantRepository.findAllByAccessGroup_IdOrderByPriorityAscCreatedAtAsc(9L)).thenReturn(List.of());

        var response = service.create(new AccessGroupRequest(
                " default ",
                "demo",
                null,
                null,
                List.of(" OpenAI ", "openai"),
                List.of(" GPT-5-MINI "),
                List.of("openai"),
                List.of("codex"),
                60,
                null,
                null,
                null
        ));

        assertEquals("default", response.groupName());
        assertEquals(List.of("openai"), response.allowedProtocolSuites());
        assertEquals(List.of("gpt-5-mini"), response.allowedModels());
        assertEquals(List.of("OPENAI_DIRECT"), response.allowedProviderTypes());
        assertEquals(List.of("CODEX"), response.allowedClientFamilies());
    }

    @Test
    void shouldDeleteAccessGroupAndItsBindings() {
        AccessGroupEntity entity = new AccessGroupEntity();
        ReflectionTestUtils.setField(entity, "id", 9L);
        entity.setGroupName("default");
        Mockito.when(accessGroupRepository.findById(9L)).thenReturn(Optional.of(entity));

        service.delete(9L);

        Mockito.verify(planAccessGroupRepository).deleteAllByAccessGroup_Id(9L);
        Mockito.verify(keyGrantRepository).deleteAllByAccessGroup_Id(9L);
        Mockito.verify(accessGroupRepository).delete(entity);
    }
}
