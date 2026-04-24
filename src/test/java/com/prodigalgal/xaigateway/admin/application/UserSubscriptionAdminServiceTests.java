package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.UserSubscriptionRequest;
import com.prodigalgal.xaigateway.admin.api.UserSubscriptionResponse;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.SubscriptionPlanEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UserSubscriptionEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.SubscriptionPlanRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UserSubscriptionRepository;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserSubscriptionAdminServiceTests {

    @Test
    void shouldApplyDefaultStatusAndExpiryOnCreate() {
        UserSubscriptionRepository subscriptionRepository = Mockito.mock(UserSubscriptionRepository.class);
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        SubscriptionPlanRepository planRepository = Mockito.mock(SubscriptionPlanRepository.class);
        UserSubscriptionAdminService service = new UserSubscriptionAdminService(subscriptionRepository, userRepository, planRepository);

        GatewayUserEntity user = new GatewayUserEntity();
        ReflectionTestUtils.setField(user, "id", 1L);
        user.setEmail("alpha@example.com");
        SubscriptionPlanEntity plan = new SubscriptionPlanEntity();
        ReflectionTestUtils.setField(plan, "id", 3L);
        plan.setPlanName("starter");
        plan.setDefaultDurationDays(14);

        Mockito.when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Mockito.when(planRepository.findById(3L)).thenReturn(Optional.of(plan));
        Mockito.when(subscriptionRepository.save(Mockito.any())).thenAnswer(invocation -> {
            UserSubscriptionEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 20L);
            return entity;
        });

        UserSubscriptionResponse response = service.create(new UserSubscriptionRequest(
                1L,
                3L,
                null,
                null,
                null,
                null,
                null
        ));

        assertEquals("ACTIVE", response.status());
        assertNotNull(response.startsAt());
        assertNotNull(response.expiresAt());
        assertEquals(14L, ChronoUnit.DAYS.between(response.startsAt(), response.expiresAt()));
    }

    @Test
    void shouldRejectInvalidStatus() {
        UserSubscriptionRepository subscriptionRepository = Mockito.mock(UserSubscriptionRepository.class);
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        SubscriptionPlanRepository planRepository = Mockito.mock(SubscriptionPlanRepository.class);
        UserSubscriptionAdminService service = new UserSubscriptionAdminService(subscriptionRepository, userRepository, planRepository);

        GatewayUserEntity user = new GatewayUserEntity();
        ReflectionTestUtils.setField(user, "id", 1L);
        user.setEmail("alpha@example.com");
        SubscriptionPlanEntity plan = new SubscriptionPlanEntity();
        ReflectionTestUtils.setField(plan, "id", 3L);
        plan.setPlanName("starter");
        plan.setDefaultDurationDays(30);

        Mockito.when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Mockito.when(planRepository.findById(3L)).thenReturn(Optional.of(plan));

        assertThrows(IllegalArgumentException.class, () -> service.create(new UserSubscriptionRequest(
                1L,
                3L,
                "UNKNOWN",
                null,
                null,
                null,
                null
        )));
    }
}
