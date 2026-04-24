package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.SubscriptionPlanRequest;
import com.prodigalgal.xaigateway.admin.api.SubscriptionPlanResponse;
import com.prodigalgal.xaigateway.infra.persistence.entity.SubscriptionPlanEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.SubscriptionPlanRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UserSubscriptionRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubscriptionPlanAdminServiceTests {

    @Test
    void shouldApplyDefaultsOnCreate() {
        SubscriptionPlanRepository planRepository = Mockito.mock(SubscriptionPlanRepository.class);
        UserSubscriptionRepository subscriptionRepository = Mockito.mock(UserSubscriptionRepository.class);
        SubscriptionPlanAdminService service = new SubscriptionPlanAdminService(planRepository, subscriptionRepository);

        Mockito.when(planRepository.existsByPlanNameIgnoreCase("starter")).thenReturn(false);
        Mockito.when(planRepository.save(Mockito.any())).thenAnswer(invocation -> {
            SubscriptionPlanEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 7L);
            return entity;
        });
        Mockito.when(subscriptionRepository.countByPlan_Id(7L)).thenReturn(0L);

        SubscriptionPlanResponse response = service.create(new SubscriptionPlanRequest(
                "starter",
                "demo",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertEquals(30, response.defaultDurationDays());
        assertEquals(3, response.maxActiveKeys());
        assertEquals(60, response.rpmLimit());
        assertEquals(120000, response.tpmLimit());
        assertEquals(2, response.concurrencyLimit());
        assertEquals(1_000_000L, response.dailyTokenLimit());
    }

    @Test
    void shouldRejectDeleteWhenPlanStillHasSubscriptions() {
        SubscriptionPlanRepository planRepository = Mockito.mock(SubscriptionPlanRepository.class);
        UserSubscriptionRepository subscriptionRepository = Mockito.mock(UserSubscriptionRepository.class);
        SubscriptionPlanAdminService service = new SubscriptionPlanAdminService(planRepository, subscriptionRepository);

        SubscriptionPlanEntity entity = new SubscriptionPlanEntity();
        entity.setPlanName("starter");
        Mockito.when(planRepository.findById(2L)).thenReturn(Optional.of(entity));
        Mockito.when(subscriptionRepository.countByPlan_Id(2L)).thenReturn(1L);

        assertThrows(IllegalArgumentException.class, () -> service.delete(2L));
        Mockito.verify(planRepository, Mockito.never()).delete(Mockito.any());
    }
}
