package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.GatewayUserRequest;
import com.prodigalgal.xaigateway.admin.api.GatewayUserResponse;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UserSubscriptionRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAdminServiceTests {

    @Test
    void shouldNormalizeEmailAndApplyDefaultActiveOnCreate() {
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        UserSubscriptionRepository subscriptionRepository = Mockito.mock(UserSubscriptionRepository.class);
        UserAdminService service = new UserAdminService(userRepository, subscriptionRepository);

        Mockito.when(userRepository.existsByEmailIgnoreCase("alpha@example.com")).thenReturn(false);
        Mockito.when(userRepository.save(Mockito.any())).thenAnswer(invocation -> {
            GatewayUserEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 9L);
            return entity;
        });
        Mockito.when(subscriptionRepository.countByUser_Id(9L)).thenReturn(0L);

        GatewayUserResponse response = service.create(new GatewayUserRequest("  Alpha@Example.com ", "Alpha", null, null));

        assertEquals("alpha@example.com", response.email());
        assertTrue(response.active());
    }

    @Test
    void shouldRejectDeleteWhenUserStillHasSubscriptions() {
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        UserSubscriptionRepository subscriptionRepository = Mockito.mock(UserSubscriptionRepository.class);
        UserAdminService service = new UserAdminService(userRepository, subscriptionRepository);

        GatewayUserEntity entity = new GatewayUserEntity();
        entity.setEmail("alpha@example.com");
        Mockito.when(userRepository.findById(1L)).thenReturn(Optional.of(entity));
        Mockito.when(subscriptionRepository.countByUser_Id(1L)).thenReturn(2L);

        assertThrows(IllegalArgumentException.class, () -> service.delete(1L));
        Mockito.verify(userRepository, Mockito.never()).delete(Mockito.any());
    }
}
